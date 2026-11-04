# engine-web — Web Game Runtime (TD-036)

Plays a Forgeroom scene file in the browser. The engine's Java code is compiled to JavaScript with
[gdx-teavm](https://github.com/xpenatan/gdx-teavm) (TeaVM). The output is a static site, so any web server can
host it. It plays scenes with the same `GameSession` and `GameRenderer` as the desktop runtime
(`:engine-desktop:run`). The editor is **not** part of the web build (see TD-036).

## Status

| Feature | Web |
|---|---|
| Scene loading (`?scene=` URL parameter), all component serializers | Works |
| All 14 sample games start and run their scripts | Works (headless Chrome, release build) |
| 2D shapes, HUD text, 2D physics (Box2D, pure-Java `gdx-box2d-gwt`) | Works |
| 3D scenes (lights, shadows, sky light) | Works |
| Java scripts under `assets/scripts` | Works: compiled into the build, registry generated (`generateScriptRegistry`) |
| Kotlin scripts | **Not supported** in the web build (only `.java` sources are compiled) |
| Visual-script graphs, Animators, imported glTF models | Works (checked with a temporary test scene) |
| Textured sprites | Same code path as desktop (`AssetService` through `AssetSource`); not checked in a browser |
| Keyboard input | Works (breakout: Space and arrows; platformer: D and Space, sent through Chrome DevTools) |
| Mouse, touch, gamepad, audio | Not checked |

Details and evidence: [`docs/reports/web-runtime-spike-report.md`](../docs/reports/web-runtime-spike-report.md).
To put the games inside another website (urconnex.com/games), see [`EMBEDDING.md`](EMBEDDING.md); to deploy them to
the VPS step by step, see [`docs/deploy-steps.md`](../docs/deploy-steps.md).

## What the build contains

- `index.html`: our page (`src/web/index.html`, replacing gdx-teavm's generated one). The game fills the window or
  iframe with no scrollbars.
- `app.js`: the runtime and the project's scripts.
- `assets/`: the project's assets without `assets/build`, plus `assets/games.json`, a catalog of the sample games
  built from the scene names and the table in `assets/samples/README.md` (`generateGameCatalog`; the build fails
  if a sample has no table row).
- `?scene=` takes a URL-encoded scene path, as `encodeURIComponent` writes it.
- While the game has focus it cancels the browser's default action for Space, the arrows, Page Up/Down, End, Tab
  and Backspace, so an embedding page does not scroll.

## Build and run locally

```bash
./gradlew :engine-web:gdx_teavm_web_js_run              # dev build, served by gdx-teavm's Jetty (port in the log)
./gradlew :engine-web:gdx_teavm_web_js_release_build    # optimized build -> engine-web/build/dist/js/release/webapp
```

Open `index.html?scene=samples/breakout-2d.json` to pick a scene (path relative to `assets/`). Without the
parameter it plays `samples/platformer-2d.json`.

The build bundles `assets/` without `assets/build/` (the editor's compiled scripts; `stageWebAssets`). Scripts are
compiled from `assets/scripts` into the build itself. A script class must be public (and static if nested) with a
public no-argument constructor; others are left out with a build warning, and a scene that binds one fails to start
with a message naming the class.

## Deploy to a VPS

The release build is plain static files: `index.html`, `app.js`, `assets/` and `scripts/`. You can deploy it in
either of two ways.

### Docker (builds on the server)

```bash
git clone <repo> gdx-unity && cd gdx-unity
docker build -f engine-web/deploy/Dockerfile -t gdxunity-web .
docker run -d --name gdxunity-web -p 8080:80 --restart unless-stopped gdxunity-web
```

The image builds with JDK 21 and serves with nginx (`engine-web/deploy/nginx.conf`). Put TLS in front of it with
the host's reverse proxy. For example, with Caddy:

```
games.example.com {
    reverse_proxy 127.0.0.1:8080
}
```

### Copy the files (builds on your machine)

```bash
./gradlew :engine-web:gdx_teavm_web_js_release_build
rsync -av --delete --exclude WEB-INF engine-web/build/dist/js/release/webapp/ user@vps:/var/www/gdxunity/
```

Then serve `/var/www/gdxunity` as a static root with nginx or Caddy (`file_server`).
