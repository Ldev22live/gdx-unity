# Deploying the Web Runtime to an Ubuntu VPS (urconnex.com/games)

Step-by-step deployment of the Forgeroom web game runtime ([`engine-web`](../engine-web/README.md), TD-036) to the
Ubuntu VPS that hosts urconnex.com. The games end up at:

| URL | Served by |
|---|---|
| `https://urconnex.com/games` and `/games/<id>` | the UrConnex Next.js app (pages around the game) |
| `https://urconnex.com/games/engine/` | nginx, static files from `/var/www/gdxunity` (the game itself) |

There are two ways to deploy:
- **A. Build on the development machine, copy to the VPS** (recommended): the VPS needs only nginx. No Java,
  Gradle or Docker, and no copy of this repository. Sections 1–7.
- **B. Build on the VPS**, either with JDK 21 and Gradle or with Docker: needs the repository on the VPS and about
  2 GB of RAM for the build. See [Building on the VPS](#building-on-the-vps-instead).

**Status:** written for Ubuntu Server 22.04 LTS and 24.04 LTS with the Ubuntu `nginx` package. It has **not been
run against the urconnex.com VPS**. Its layout is not known here, so check each path against the server; the
commands that find the right file are included. What has been tested is listed in
[web-runtime-spike-report.md](reports/web-runtime-spike-report.md).

## Prerequisites

- **Development machine:**
  - This repository and JDK 21.
  - `ssh`, `scp` and `tar`. These ship with Windows 10/11 (OpenSSH, `tar.exe`), Linux and macOS.
- **VPS:**
  - Ubuntu with SSH access as a user that can `sudo`.
  - urconnex.com already served over HTTPS by nginx, in front of Next.js and Spring Boot.
- In the commands below, replace `user@vps` with your SSH login.

## 1. Check the VPS (once)

```bash
ssh user@vps
lsb_release -ds                       # e.g. "Ubuntu 24.04.1 LTS"
nginx -v                              # nginx is installed
systemctl is-active nginx             # "active"
sudo ufw status                       # if active: "Nginx Full" or 80/443 allowed (already true if the site works)
grep -rl "server_name.*urconnex" /etc/nginx/sites-enabled/ /etc/nginx/conf.d/ 2>/dev/null
```

The last command prints the nginx file that serves urconnex.com, usually `/etc/nginx/sites-enabled/<name>`. That
is a symlink to `/etc/nginx/sites-available/<name>`; edit the file in `sites-available`.

If nginx is missing, the site is served some other way, and this guide's nginx steps do not apply as written.
Installing nginx is `sudo apt update && sudo apt install -y nginx`, but putting it in front of an existing site
is a separate job.

## 2. Build and package (development machine, repository root)

Windows (PowerShell):

```powershell
.\gradlew.bat :engine-web:gdx_teavm_web_js_release_build
tar -czf gdxunity-web.tgz --exclude=WEB-INF -C engine-web/build/dist/js/release/webapp .
scp gdxunity-web.tgz user@vps:/tmp/
```

Linux or macOS:

```bash
./gradlew :engine-web:gdx_teavm_web_js_release_build
tar -czf gdxunity-web.tgz --exclude=WEB-INF -C engine-web/build/dist/js/release/webapp .
scp gdxunity-web.tgz user@vps:/tmp/
```

- The build output is in `engine-web/build/dist/js/release/webapp`, about 3 MB.
- `WEB-INF` is a descriptor for the gdx-teavm development server only; browsers do not need it.
- `gradle.properties` sets quiet logging, so a successful build prints almost nothing. Check that the command
  ended without an error and that `gdxunity-web.tgz` exists.

## 3. Unpack on the VPS

```bash
ssh user@vps
sudo mkdir -p /var/www/gdxunity-new
sudo tar -xzf /tmp/gdxunity-web.tgz -C /var/www/gdxunity-new --no-same-owner
sudo rm -rf /var/www/gdxunity-old
[ -d /var/www/gdxunity ] && sudo mv /var/www/gdxunity /var/www/gdxunity-old
sudo mv /var/www/gdxunity-new /var/www/gdxunity
sudo chown -R root:root /var/www/gdxunity
sudo find /var/www/gdxunity -type d -exec chmod 755 {} +
sudo find /var/www/gdxunity -type f -exec chmod 644 {} +
rm /tmp/gdxunity-web.tgz
```

- The new version is unpacked beside the old one and swapped in with a rename, so visitors never load a
  half-copied build. The previous version stays in `/var/www/gdxunity-old` for a rollback (step 7).
- The files are owned by root. nginx's workers run as `www-data` on Ubuntu and only need to read them.

## 4. Add the nginx routes (first deployment only)

Put the routes in a snippet file, so the site's config only gains one `include` line:

```bash
sudo tee /etc/nginx/snippets/gdxunity-games.conf > /dev/null <<'EOF'
# Forgeroom web game runtime at /games/engine/ (see gdx-unity docs/deploy-steps.md)
location /games/engine/ {
    alias /var/www/gdxunity/;
    add_header Cache-Control "public, max-age=3600";
    add_header Content-Security-Policy "frame-ancestors 'self'" always;
    gzip on;
    gzip_types application/javascript application/json text/plain;
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
EOF
```

Then open the site's file from step 1, for example `sudo nano /etc/nginx/sites-available/urconnex`. In the
`server { ... }` block that has `listen 443 ssl` (the HTTPS one), add this line next to the existing `location /`:

```nginx
    include snippets/gdxunity-games.conf;
```

- **Certbot:** if the site's certificate comes from certbot, that block contains lines marked
  `# managed by Certbot`. Add the `include` inside that same block. Leave the port 80 block alone: it only
  redirects to HTTPS.
- **Routing:** nginx uses the longest matching prefix, so `/games/engine/` goes to the files and every other path
  still reaches Next.js and Spring Boot.
- **Framing:** `frame-ancestors 'self'` allows only urconnex.com pages to embed the game in an iframe.
- **Caching:** `index.html` and `games.json` are not cached, so a redeploy shows up on the next page load.

Check the configuration and reload, which does not drop connections:

```bash
sudo nginx -t && sudo systemctl reload nginx
```

If `nginx -t` reports an error, nginx keeps running with the old configuration. Fix the file and run the
command again.

## 5. Verify before changing the website

```bash
curl -sI https://urconnex.com/games/engine/index.html | grep -iE "HTTP/|content-security"
curl -s https://urconnex.com/games/engine/assets/games.json | head -c 200
```

- **First command:** expect `HTTP/... 200` and `frame-ancestors 'self'`.
- **Second command:** expect the start of the game catalog (`{ "version": 1, "games": [ ...`).
- **In a browser:** open
  `https://urconnex.com/games/engine/index.html?scene=samples%2Fbreakout-2d.json`, click into the page and play
  (A/D or arrows, Space to launch).

## 6. Add the pages to UrConnex (first deployment only)

Add the three Next.js files from [engine-web/EMBEDDING.md §3](../engine-web/EMBEDDING.md#3-nextjs-pages-app-router)
to the UrConnex app, then build and deploy UrConnex the usual way:
- `app/games/catalog.ts`
- `app/games/page.tsx`
- `app/games/[id]/page.tsx`

Spring Boot needs no change for a plain embed.

Then check `https://urconnex.com/games` (list of 14 games) and one game page such as
`https://urconnex.com/games/breakout-2d`.

## 7. Updating and rolling back

- **Update:** repeat steps 2 and 3. No nginx reload is needed; players get the new version on their next page load.
- **Rollback** to the previous version:

  ```bash
  sudo mv /var/www/gdxunity /var/www/gdxunity-bad
  sudo mv /var/www/gdxunity-old /var/www/gdxunity
  ```

## Building on the VPS instead

Both variants need the repository on the VPS. The work in this repository is **not committed yet**, so commit and
push it first; a `git clone` only has what is pushed.

### B1. JDK 21 and Gradle on Ubuntu

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk-headless git    # in the Ubuntu 22.04 and 24.04 repositories
java -version                                       # expect 21
git clone <repository-url> ~/gdx-unity && cd ~/gdx-unity
./gradlew :engine-web:gdx_teavm_web_js_release_build
tar -czf /tmp/gdxunity-web.tgz --exclude=WEB-INF -C engine-web/build/dist/js/release/webapp .
```

Continue with step 3. To update later, run `git pull` and repeat the build and step 3.

**Low-memory VPS:** the build runs Gradle with up to 1 GB of heap (`gradle.properties`) plus the TeaVM compiler.
On a VPS with less than about 2 GB of RAM, add swap first:

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### B2. Docker on Ubuntu

```bash
sudo apt update
sudo apt install -y docker.io git        # Ubuntu's Docker package; Docker's own apt repository also works
sudo systemctl enable --now docker
git clone <repository-url> ~/gdx-unity && cd ~/gdx-unity
sudo docker build -f engine-web/deploy/Dockerfile -t gdxunity-web .
sudo docker run -d --name gdxunity-web -p 127.0.0.1:8080:80 --restart unless-stopped gdxunity-web
```

- **Bind to 127.0.0.1:** keep `127.0.0.1:` in `-p`. Docker writes its own firewall rules and bypasses `ufw`, so
  `-p 8080:80` would expose port 8080 to the internet.
- **nginx snippet:** instead of the three blocks in step 4, `/etc/nginx/snippets/gdxunity-games.conf` holds one
  block. The container's own nginx sends the cache and `frame-ancestors` headers.

  ```nginx
  location /games/engine/ {
      proxy_pass http://127.0.0.1:8080/;
  }
  ```

- **Update:**

  ```bash
  cd ~/gdx-unity && git pull
  sudo docker build -f engine-web/deploy/Dockerfile -t gdxunity-web .
  sudo docker rm -f gdxunity-web
  sudo docker run -d --name gdxunity-web -p 127.0.0.1:8080:80 --restart unless-stopped gdxunity-web
  ```

  The game is unavailable for the few seconds between `rm` and `run`.

The Docker image has not been built yet, because Docker is not installed on the development machine.

The browser **editor** (streamed from the VPS, one container per user) has its own guide: [editor-streaming.md](editor-streaming.md).

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| 404 at `/games/engine/...` | The `include` is in the port 80 server block instead of the HTTPS one, or the reload failed (`sudo nginx -t`) |
| 403 at `/games/engine/...` | nginx cannot read the files: rerun the `chown`/`chmod` lines of step 3. Check `sudo tail -n 20 /var/log/nginx/error.log` |
| 502 at `/games/engine/` (Docker variant) | The container is not running: `sudo docker ps`, `sudo docker logs gdxunity-web` |
| Game page is blank and the console says the iframe was refused | The page embedding the game is not on urconnex.com (`frame-ancestors 'self'`), or another `Content-Security-Policy`/`X-Frame-Options` header is added for `/games/engine/` |
| Stuck on the libGDX loading screen | In the browser's Network tab, look for asset requests that never finish, and check `/var/log/nginx/error.log`. This was seen locally with Python's test server, not yet with nginx |
| "Scene not found" in the console | The `?scene=` path does not match a file under `assets/` (see `assets/games.json` for valid paths) |
| Keys scroll the page instead of playing | The game does not have focus: click inside it. The Next.js page focuses the iframe on load |
| Old version still showing | The browser cached `app.js` for up to an hour; reload with Ctrl+F5 |
| Build on the VPS is killed or very slow | Out of memory: add swap (B1) or build on the development machine (section 2) |
