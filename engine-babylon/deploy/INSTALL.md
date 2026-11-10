# Installing Forgeroom Web on a VPS

This package is the **Forgeroom web player and editor**, built. It is plain static files (HTML, JavaScript, models, songs),
so the server only has to hand them out: there is no database, no Java, no Node and no background process.

| Page | What it is |
|---|---|
| `index.html` | the **player**: pick a sample scene and play it |
| `editor.html` | the **editor**: scenes, sculpting, the music studio (also opens the samples with `?scene=landscape-3d.json`) |

What is in the package:

```
site/        the files to serve (both pages, scripts, samples/, models/, music/)
deploy/      nginx-forgeroom.conf, Dockerfile, container-nginx.conf, docker-compose.yml
INSTALL.md   this file
BUILD-INFO.txt   version, build date, commit
```

**Status of these instructions:** written for Ubuntu Server 22.04 / 24.04 with the Ubuntu `nginx` package, or Docker.
They have **not been run on your VPS**: check the paths against your server. What *was* checked before packaging: the
built site was served from a plain web server under a sub-folder and the player, the editor, a model scene and the music
studio ran in a real browser. The nginx and Docker files were **not** run (no nginx or Docker where the package was made).

## What you need

- A VPS with SSH access as a user that can `sudo`, and a domain (or sub-domain) pointing at it.
- **HTTPS.** The music studio's sound engine (an AudioWorklet) only runs on a secure page, so over plain `http://` the Music
  tab cannot play sound. The rest works on http, but use https: the editor also saves files through the browser's file picker,
  which needs it too.
- About 40 MB of disk. No other software besides nginx (or Docker).

## A. nginx on the VPS (recommended)

### 1. Copy the package up and unpack it

From your own computer (replace `user@vps`):

```bash
scp forgeroom-web-0.1.0.tar.gz forgeroom-web-0.1.0.tar.gz.sha256 user@vps:/tmp/
ssh user@vps
cd /tmp && sha256sum -c forgeroom-web-0.1.0.tar.gz.sha256      # must say: OK
tar -xzf forgeroom-web-0.1.0.tar.gz
cat forgeroom-web-0.1.0/BUILD-INFO.txt
```

### 2. Put the site where nginx will serve it

```bash
sudo mkdir -p /var/www/forgeroom
sudo rsync -a --delete /tmp/forgeroom-web-0.1.0/site/ /var/www/forgeroom/
sudo chown -R root:root /var/www/forgeroom
sudo find /var/www/forgeroom -type d -exec chmod 755 {} + && sudo find /var/www/forgeroom -type f -exec chmod 644 {} +
```

### 3a. Its own domain

```bash
sudo apt install -y nginx certbot python3-certbot-nginx        # skip what is already installed
sudo cp /tmp/forgeroom-web-0.1.0/deploy/nginx-forgeroom.conf /etc/nginx/sites-available/forgeroom
sudo nano /etc/nginx/sites-available/forgeroom                  # change server_name to your domain
sudo ln -s /etc/nginx/sites-available/forgeroom /etc/nginx/sites-enabled/forgeroom
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d forgeroom.example.com                   # adds https and renews it automatically
```

### 3b. A folder of a site you already serve (for example `https://example.com/forgeroom/`)

The site uses relative links, so it works from any folder. In the `server { ... }` block of your existing site add:

```nginx
location /forgeroom/ {
    alias /var/www/forgeroom/;
    index index.html;
    autoindex off;
}
location ~* ^/forgeroom/.*\.gltf$ { alias /var/www/forgeroom/; default_type model/gltf+json; }
location ~* ^/forgeroom/.*\.song$ { alias /var/www/forgeroom/; default_type application/json; }
```

and, if your site does not already compress JSON, add `gzip_types application/json model/gltf+json;` to the http block.
Then `sudo nginx -t && sudo systemctl reload nginx`. (The two extra blocks use `alias` with a regex location, which nginx
does not allow in this short form on every version: if `nginx -t` complains, drop them and add the types to
`/etc/nginx/mime.types` instead: `model/gltf+json gltf;` and `application/json song;`.)

### 4. Check it

```bash
curl -sI https://forgeroom.example.com/ | head -3                                  # HTTP/2 200
curl -sI https://forgeroom.example.com/editor.html | grep -i "^HTTP\|content-type"
curl -sI https://forgeroom.example.com/models/landscape/terrain-hills.gltf | grep -i "^HTTP\|content-type"   # model/gltf+json
curl -s  https://forgeroom.example.com/samples/index.json | head -c 200
```

Then open `https://forgeroom.example.com/editor.html?scene=landscape-3d.json` in a browser: you should see the valley with
trees and a lake. Open the **Music** tab, press **Play**: sound starts after that click.

## B. Docker

```bash
cd /tmp/forgeroom-web-0.1.0
docker compose -f deploy/docker-compose.yml up -d --build      # serves http on 127.0.0.1:8080 (only this machine)
curl -sI http://127.0.0.1:8080/ | head -1
```

The container speaks plain http. Put your reverse proxy (nginx, Caddy, Traefik) in front of it for HTTPS. With nginx on the host:

```nginx
server {
    server_name forgeroom.example.com;
    location / { proxy_pass http://127.0.0.1:8080; proxy_set_header Host $host; }
}
```

then `sudo certbot --nginx -d forgeroom.example.com`. With Caddy: `forgeroom.example.com { reverse_proxy 127.0.0.1:8080 }`.

## Updating and rolling back

New release: repeat step 1, then `sudo rsync -a --delete /tmp/forgeroom-web-<new>/site/ /var/www/forgeroom/` (Docker: run the
`docker compose ... up -d --build` command again). Pages are never cached (`no-cache`) and scripts have a hash in their name, so
visitors get the new version on their next visit. To keep a way back, sync into a dated folder instead
(`/var/www/forgeroom-2026-11-05/`), point the nginx `root` at it, and reload; to roll back, point it at the old folder.

## If something is wrong

| Symptom | Likely cause |
|---|---|
| Blank page, console says a module has the wrong MIME type | the files were copied wrongly, or a proxy in front rewrites types: `curl -I .../assets/<file>.js` should say `javascript` |
| A scene opens but models are missing (status bar: "Could not load N model(s)") | `.gltf` is served as `text/plain` or blocked: check the `models/` request in the browser's network tab, and the `default_type` lines |
| Music tab: Play does nothing, console mentions `audioWorklet` | the page is on `http://` (not https), or the browser blocked sound until you click: click Play once |
| 404 for `/samples/index.json` | `site/` was not copied whole (the `samples`, `models` and `music` folders are part of it) |
| Works at `/` but not under `/forgeroom/` | the `location` block is missing the trailing slash or `alias` path: they must both end in `/` |

## Notes

- **No accounts, no saving on the server.** Scenes and songs are opened from and saved to the visitor's own computer (the
  browser's file picker, or a download). Anyone who can reach the URL can use the editor; if that is not wanted, put the
  pages behind your proxy's login (for example nginx `auth_basic`).
- The editor is not a multi-user tool and keeps nothing between visits.
- The site is a static copy of what the repository builds with `npm run package` in `engine-babylon/`.
