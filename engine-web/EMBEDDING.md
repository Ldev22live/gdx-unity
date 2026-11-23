# Embedding the web runtime in a website (urconnex.com/games)

This guide puts the Forgeroom sample games under `https://urconnex.com/games`. The site's Next.js app shows the
pages, and the game itself runs in an iframe. The snippets below belong in the **UrConnex** repository. This
repository's rules (AGENTS.md §2) keep Forgeroom agents out of UrConnex, so they were written here and not tested
against the UrConnex code.

## Layout

| URL | Served by | What |
|---|---|---|
| `/games` | Next.js | List of games, read from the catalog below |
| `/games/<id>` | Next.js | One game: UrConnex header and layout around an iframe |
| `/games/engine/` | nginx (static files or the `engine-web` container) | The web runtime: `index.html`, `app.js`, `assets/`, `scripts/` |
| `/games/engine/assets/games.json` | nginx | Catalog of the bundled games: `id`, `name`, `kind`, `description`, `controls`, `scene` |
| `/games/engine/index.html?scene=<scene>` | nginx | Plays one scene; this is the iframe's `src` |

Everything is on the same origin, so there is no CORS setup and cookies are not an issue. The runtime only uses
relative URLs, so the `/games/engine/` prefix needs no configuration in the build.

## 1. Build and put the files on the VPS

Full walkthrough with checks, updates and rollback: [docs/deploy-steps.md](../docs/deploy-steps.md).

Pick one:

- **Container**
  ```bash
  docker build -f engine-web/deploy/Dockerfile -t gdxunity-web .
  docker run -d --name gdxunity-web -p 127.0.0.1:8080:80 --restart unless-stopped gdxunity-web
  ```
- **Static files**
  ```bash
  ./gradlew :engine-web:gdx_teavm_web_js_release_build
  rsync -av --delete --exclude WEB-INF engine-web/build/dist/js/release/webapp/ user@vps:/var/www/gdxunity/
  ```

## 2. nginx: route /games/engine/ before Next.js

In the `server` block for urconnex.com, next to the existing `location /` that proxies to Next.js. nginx picks the
longest matching prefix, so `/games/engine/` wins over `/` and `/games`.

```nginx
# Container variant: the trailing slashes strip /games/engine/ before passing the request on
location /games/engine/ {
    proxy_pass http://127.0.0.1:8080/;
}
```

```nginx
# Static-files variant
location /games/engine/ {
    alias /var/www/gdxunity/;
    add_header Cache-Control "public, max-age=3600";
    add_header Content-Security-Policy "frame-ancestors 'self'" always;
}
location = /games/engine/index.html {
    alias /var/www/gdxunity/index.html;
    add_header Cache-Control "no-cache";
    add_header Content-Security-Policy "frame-ancestors 'self'" always;
}
location = /games/engine/assets/games.json {
    alias /var/www/gdxunity/assets/games.json;
    add_header Cache-Control "no-cache";
}
```

`frame-ancestors 'self'` lets only urconnex.com pages embed the game. The container's `nginx.conf` sends the same
header. If UrConnex has no nginx in front and Next.js serves everything, use a rewrite in `next.config.js`
instead:

```js
async rewrites() {
  return [{ source: '/games/engine/:path*', destination: 'http://127.0.0.1:8080/:path*' }];
}
```

## 3. Next.js pages (App Router)

```tsx
// app/games/catalog.ts
export type Game = { id: string; name: string; kind: string; description: string; controls: string; scene: string };
export const ENGINE_PATH = '/games/engine';
export async function loadCatalog(): Promise<Game[]> {
  const response = await fetch(`${ENGINE_PATH}/assets/games.json`, { cache: 'no-store' });
  if (!response.ok) throw new Error(`Game catalog: HTTP ${response.status}`);
  return (await response.json()).games;
}
```

```tsx
// app/games/page.tsx
'use client';
import Link from 'next/link';
import { useEffect, useState } from 'react';
import { Game, loadCatalog } from './catalog';

export default function GamesPage() {
  const [games, setGames] = useState<Game[]>([]);
  const [error, setError] = useState<string>();
  useEffect(() => { loadCatalog().then(setGames).catch(e => setError(String(e))); }, []);
  if (error) return <p>Games are unavailable: {error}</p>;
  return (
    <ul>
      {games.map(game => (
        <li key={game.id}>
          <Link href={`/games/${game.id}`}>{game.name}</Link> ({game.kind}): {game.description}
        </li>
      ))}
    </ul>
  );
}
```

```tsx
// app/games/[id]/page.tsx
'use client';
import { useEffect, useRef, useState } from 'react';
import { useParams } from 'next/navigation';
import { ENGINE_PATH, Game, loadCatalog } from '../catalog';

export default function PlayPage() {
  const { id } = useParams<{ id: string }>();
  const [game, setGame] = useState<Game | null>();
  const frame = useRef<HTMLIFrameElement>(null);
  useEffect(() => { loadCatalog().then(games => setGame(games.find(g => g.id === id) ?? null)); }, [id]);
  if (game === undefined) return <p>Loading…</p>;
  if (game === null) return <p>No game named {id}.</p>;
  return (
    <section>
      <h1>{game.name}</h1>
      <p>Controls: {game.controls}</p>
      <iframe
        ref={frame}
        title={game.name}
        src={`${ENGINE_PATH}/index.html?scene=${encodeURIComponent(game.scene)}`}
        allow="fullscreen; gamepad"
        style={{ width: '100%', aspectRatio: '16 / 9', border: 0 }}
        onLoad={() => frame.current?.contentWindow?.focus()}
      />
    </section>
  );
}
```

Notes for the pages:
- **Keyboard focus:** the game needs keyboard focus. The `onLoad` handler gives it focus, and clicking inside the
  game takes it back after the user clicks elsewhere on the page. A visible "click to play" hint helps.
- **Scrolling:** while the game has focus, the runtime cancels the browser's default for Space, the arrows,
  Page Up/Down, End, Tab and Backspace, so they don't scroll the UrConnex page.
- **Size:** the game fills whatever size the iframe has. The samples are designed for 16:9.
- **Spring Boot:** nothing is needed for a plain embed. The game does not talk to UrConnex. Passing the logged-in
  user or saving scores would need a `postMessage` bridge, which is not built.

## Checked so far

The layout was simulated locally on 2026-11-02, with headless Chrome on Windows 11 and a minimal static server,
not nginx or Next.js:
- **Files:** the release build was served from `/games/engine/`.
- **Host page:** read `games.json` and embedded `index.html?scene=<encodeURIComponent(scene)>` in an iframe,
  focusing it on load.
- **Breakout:** Space, ArrowDown, PageDown and ArrowLeft were sent to the page. The game reacted (score, lives,
  paddle) and the host page did not scroll (`scrollY` 0).
- **Collector 3D:** holding W moved the ball and a gem was collected.

**Not checked:** a real UrConnex deployment, nginx, Next.js, mobile devices and audio.
