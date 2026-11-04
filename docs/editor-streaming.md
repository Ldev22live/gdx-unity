# Running the Editor in the Browser (streamed, urconnex.com/editor)

The Forgeroom editor cannot be compiled to the browser the way the game runtime was (it compiles scripts in-process,
uses native file dialogs and reads the project through `java.io`; see TD-037). Instead the **real desktop editor runs
on the VPS** and its window is **streamed to the browser**. Each logged-in user gets their own container.

```
Browser  ──https──►  nginx  ──auth_request──►  Spring Boot (is this user logged in? which container?)
   ▲                   │
   │ noVNC page        └──websocket──►  user's editor container (internal Docker network, no internet)
   │ + WebSocket                          Xvfb display ► openbox ► Forgeroom editor (software OpenGL)
   └─────────────────────────────────────  x11vnc ► websockify ► noVNC page
```

| What | Where |
|---|---|
| Image (display, VNC, noVNC, editor) | [`editor-desktop/deploy/stream/`](../editor-desktop/deploy/stream/Dockerfile) |
| Start/stop/status per user (the only thing the web app calls) | [`editor-session.sh`](../editor-desktop/deploy/stream/editor-session.sh) |
| Its test (fake `docker`, 62 checks) | [`test-editor-session.sh`](../editor-desktop/deploy/stream/test-editor-session.sh) |
| Decision record | TD-037 in [TECHNICAL_DECISIONS.md](TECHNICAL_DECISIONS.md) |
| Game player for the samples (separate, static files) | [deploy-steps.md](deploy-steps.md) |

## What has and has not been checked

This was written on a Windows machine with **no Docker and no Linux**, so most of it has never run.

| Checked | Not checked (do this on the VPS, in this order) |
|---|---|
| `editor-session.sh` against a fake `docker`: id validation, flags (no published ports, no capabilities, read-only root, limits, internal network with `enable_icc=false`, fixed bridge name), capacity limit, reuse of a running session, stop keeps the volume. 62 checks; copies of the script with a flag removed (published port, capabilities, `--internal`, `enable_icc`, bridge name, id validation) fail them. | 1. The image builds. |
| `bash -n` syntax of all three scripts. | 2. `docker run ... smoke` passes: Xvfb, llvmpipe OpenGL context and the editor's own smoke test. |
| The Gradle step of the Dockerfile (`:editor-desktop:prepareEditorPackageInput`) runs and produces the flat folder of 43 jars plus `sample-project/` (with its `gdxunity.project` marker) that the entrypoint's classpath expects. Run on Windows here, so it picked Windows natives; the Linux natives (LWJGL, GLFW, OpenGL, tinyfd, libGDX, FreeType) are among the resolved jars, and `PlatformDirectories` has Linux (XDG) folders. | 3. A session opens in a browser through an SSH tunnel; keyboard, mouse, file dialogs, Play. |
| | 4. `--idle-timeout` of websockify stops an unused session; the host can reach a session's IP; **one session cannot connect to another, and no session can connect to a service on the VPS** (try `curl` from inside a session's container to the other session's IP and to `172.x.0.1`). |
| | 5. The nginx, Spring Boot and Next.js snippets below. They were written for a stack I have not seen. |

## Capacity, honestly

- There is no GPU. Mesa's llvmpipe draws on the CPU, so the editor UI is fine but a 3D scene in the viewport or in
  Play mode will be slow. 2D scenes are much cheaper.
- The defaults (3 GB memory and 2 CPUs per session, at most 4 sessions) are guesses. Measure with `docker stats`
  while someone works, then set `GDXUNITY_SESSION_MEMORY`, `GDXUNITY_SESSION_CPUS` and `GDXUNITY_MAX_SESSIONS`.
  Four sessions at the defaults need a VPS with well over 12 GB of RAM and 8 CPUs to feel good.
- When all slots are taken, `start` fails with exit status 3 and the web app should say "try again later".

## Security model

The editor runs scripts as trusted in-process code (TD-019): **anyone who can open the editor can run arbitrary Java
on the machine that runs it.** The container is the only barrier, so it is locked down:

- One container per user, on one Docker network created with `--internal` (no route to the internet or other
  networks) and `enable_icc=false` (containers on it cannot connect to each other). Without that, one user's
  script could open another user's editor: noVNC has no password, because nginx is the gate.
- The host's firewall refuses connections from the session network to services on the VPS itself (Spring Boot, a
  database bound to all interfaces, SSH). Section 2 adds that rule; without it a session can reach them.
- **No published ports.** The container can only be reached through nginx after Spring Boot approved the request.
- All Linux capabilities dropped, `no-new-privileges`, non-root user (uid 10001), read-only root file system,
  memory, CPU and process limits, `/tmp` limited to 1 GB.
- The VNC server only accepts connections from inside its own container; websockify (noVNC) is the only way in.
- The web application never gets the Docker socket (that would be root on the host). It runs one script through
  `sudo`, and the script validates the user id before calling docker.

**Known gaps** (decide before opening this to users you do not trust):
- A container shares the host kernel. A kernel escape is unlikely but possible. For stronger isolation run the
  containers with gVisor (`--runtime=runsc`, add it in `editor-session.sh`) or on their own VM.
- Isolation between sessions and from the host depends on `enable_icc=false` and the firewall rule of section 2.
  Both are untested here (checklist step 4); test them before letting anyone in.
- No disk quota for a user's volume. A script can fill the host's disk. Put `/var/lib/docker` on its own
  filesystem or use quota-capable storage.
- No way to upload or download project files (for example import a model or export the project). A file dialog
  inside the editor only sees the container's files. This needs a small upload/download endpoint next.
- Unsaved work is lost if the session ends (editor closed, container crash, idle timeout); the project on disk is
  kept in the user's volume.

## 1. Build the image

On a machine with Docker, in the repository root (work must be committed and pushed first if the VPS builds it):

```bash
docker build -f editor-desktop/deploy/stream/Dockerfile -t gdxunity-editor-stream .
```

To build on the development machine and move it to the VPS instead:

```bash
docker save gdxunity-editor-stream | gzip | ssh user@vps 'gunzip | sudo docker load'
```

Check the image (this is step 2 of the checklist above; it should print `smoke test passed` and exit 0):

```bash
sudo docker run --rm --read-only --tmpfs /tmp:rw,exec,nosuid,size=1g \
    -v gdxunity-smoke:/data gdxunity-editor-stream smoke; echo "exit $?"
sudo docker volume rm gdxunity-smoke
```

If it fails with an OpenGL error, show the output of `sudo docker run --rm gdxunity-editor-stream bash -c 'glxinfo -B'`
after installing `mesa-utils` in the image; the editor needs an OpenGL context from llvmpipe.

## 2. Install the session script (Ubuntu VPS)

```bash
sudo apt update && sudo apt install -y docker.io      # as in deploy-steps.md, "B2"
sudo install -o root -g root -m 755 editor-desktop/deploy/stream/editor-session.sh /opt/gdxunity/editor-session.sh
```

If `/opt/gdxunity` does not exist, create it first with `sudo mkdir -p /opt/gdxunity`.

### Stop sessions from reaching the VPS

The session network is `internal`, but its bridge (`gdxeditors0`) still has an address on the VPS, and a script
running in a session could connect to anything listening there on all interfaces. With `ufw` (already used for the
site), refuse new inbound connections on that bridge. Replies to nginx's connections to the sessions are not
affected, because ufw accepts established connections first:

```bash
sudo ufw deny in on gdxeditors0 comment 'editor sessions may not reach the VPS'
sudo ufw status verbose | grep gdxeditors0
```

The bridge exists after the first session starts. ufw accepts a rule for an interface that does not exist yet.
Without `ufw`, add an `iptables` rule: `sudo iptables -I INPUT -i gdxeditors0 -m conntrack --ctstate NEW -j DROP`
(and persist it with `iptables-persistent`).

Allow the web application's service user (replace `<service-user>` with the account Spring Boot runs as) to run
exactly this script, and nothing else, as root:

```bash
sudo tee /etc/sudoers.d/gdxunity-editor > /dev/null <<'EOF'
<service-user> ALL=(root) NOPASSWD: /opt/gdxunity/editor-session.sh start *, /opt/gdxunity/editor-session.sh stop *, /opt/gdxunity/editor-session.sh status *
EOF
sudo chmod 440 /etc/sudoers.d/gdxunity-editor
sudo visudo -c
```

The `*` matches any user id; the script rejects everything that is not `^[a-z0-9][a-z0-9-]{0,39}$`.

## 3. Try a session by hand (before touching the website)

```bash
sudo /opt/gdxunity/editor-session.sh start test1        # prints "starting 172.x.y.z:6080", later "ready ..."
sudo /opt/gdxunity/editor-session.sh status test1
```

When it says `ready 172.18.0.2:6080`, open it from your own computer through an SSH tunnel to that address:

```bash
ssh -L 6080:172.18.0.2:6080 user@vps
# then browse to http://localhost:6080/vnc.html?autoconnect=true&resize=scale
```

You should see the editor with the sample project. Check: typing, mouse, the File menu's dialogs, and pressing Play.
If it does not work: `sudo docker logs gdxunity-editor-test1`. Then remove the test session (its volume stays):

```bash
sudo /opt/gdxunity/editor-session.sh stop test1
sudo docker volume rm gdxunity-editor-data-test1
```

## 4. nginx: authenticate, then route to the user's container

In the HTTPS `server` block of urconnex.com, next to the other `location` blocks (Ubuntu: put it in
`/etc/nginx/snippets/gdxunity-editor.conf` and `include` it, as in deploy-steps.md step 4):

```nginx
# Is this user logged in, and which container is theirs? Spring Boot answers with a header.
location = /editor/_auth {
    internal;
    proxy_pass http://127.0.0.1:8080/api/editor/upstream;     # your Spring Boot address
    proxy_pass_request_body off;
    proxy_set_header Content-Length "";
    proxy_set_header Cookie $http_cookie;
    proxy_set_header X-Original-URI $request_uri;
}

# The noVNC page and its WebSocket, for the authenticated user's container only
location /editor/session/ {
    auth_request /editor/_auth;
    auth_request_set $editor_upstream $upstream_http_x_editor_upstream;   # e.g. 172.18.0.2:6080
    rewrite ^/editor/session/(.*)$ /$1 break;
    proxy_pass http://$editor_upstream;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_read_timeout 3600s;
    proxy_buffering off;
    add_header Content-Security-Policy "frame-ancestors 'self'" always;
}
```

- The upstream is an IP and port, so no `resolver` is needed. Because `proxy_pass` has a variable, the `rewrite ... break`
  line is what removes the `/editor/session/` prefix.
- `auth_request` treats only 2xx as allowed, 401 and 403 as denied, and anything else (a redirect to a login page,
  for example) as a server error. Make `/api/editor/**` answer 401 to anonymous requests.
- `/editor` itself (the page with the iframe) stays with Next.js.

```bash
sudo nginx -t && sudo systemctl reload nginx
```

## 5. Spring Boot: the two endpoints

`POST /api/editor/session` starts the user's session (idempotent) and returns its state for the page to poll.
`GET /api/editor/upstream` is what nginx asks on every request: 200 with `X-Editor-Upstream` only when the
session is ready. Adapt the authentication to your setup. The user key must be stable, **different for every
account** and match `^[a-z0-9][a-z0-9-]{0,39}$` (a numeric id with a prefix, or a lower-case UUID).

```java
@RestController
@RequestMapping("/api/editor")
class EditorSessionController {
    private static final String SCRIPT = "/opt/gdxunity/editor-session.sh";
    private static final Pattern UPSTREAM = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}:6080");

    record SessionState(String state, String upstream) {}

    @PostMapping("/session")
    SessionState start(Authentication user) throws Exception {
        return run("start", key(user));
    }

    @GetMapping("/upstream")
    ResponseEntity<Void> upstream(Authentication user) throws Exception {
        SessionState state = run("status", key(user));
        if (!"ready".equals(state.state()) || !UPSTREAM.matcher(state.upstream()).matches()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok().header("X-Editor-Upstream", state.upstream()).build();
    }

    @DeleteMapping("/session")
    void stop(Authentication user) throws Exception {
        run("stop", key(user));
    }

    private static String key(Authentication user) {
        // Must give every account its own key: two accounts sharing a key would share one editor and its files.
        // Use the database id, never a name or an e-mail address. Replace the principal type with yours.
        long id = ((UrConnexUser) user.getPrincipal()).getId();
        return "u" + id;
    }

    private static SessionState run(String command, String key) throws Exception {
        Process process = new ProcessBuilder("sudo", "-n", SCRIPT, command, key).start();   // no shell involved
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "editor-session.sh timed out");
        }
        int exit = process.exitValue();
        if (exit == 3) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "All editor sessions are in use");
        }
        if (exit != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "editor-session.sh failed with status " + exit);
        }
        String[] parts = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim().split(" ");
        return new SessionState(parts[0], parts.length > 1 ? parts[1] : null);
    }
}
```

If Spring Security's CSRF protection is on, the `POST` and `DELETE` need the CSRF token like your other mutating endpoints.

## 6. Next.js: the /editor page

```tsx
// app/editor/page.tsx
'use client';
import { useEffect, useState } from 'react';

type SessionState = { state: string; upstream: string | null };

export default function EditorPage() {
  const [state, setState] = useState<string>('starting');
  const [error, setError] = useState<string>();

  useEffect(() => {
    let cancelled = false;
    async function poll() {
      while (!cancelled) {
        const response = await fetch('/api/editor/session', { method: 'POST', credentials: 'include' });
        if (response.status === 503) { setError('All editor sessions are in use. Try again in a few minutes.'); return; }
        if (!response.ok) { setError(`The editor could not be started (HTTP ${response.status}).`); return; }
        const session: SessionState = await response.json();
        setState(session.state);
        if (session.state === 'ready') return;
        await new Promise(resolve => setTimeout(resolve, 2000));
      }
    }
    poll();
    return () => { cancelled = true; };
  }, []);

  if (error) return <p>{error}</p>;
  if (state !== 'ready') return <p>Starting your editor…</p>;
  return (
    <iframe
      title="Forgeroom Editor"
      src="/editor/session/vnc.html?autoconnect=true&resize=scale&reconnect=true&path=editor/session/websockify"
      allow="clipboard-read; clipboard-write; fullscreen"
      style={{ width: '100%', height: 'calc(100vh - 4rem)', border: 0 }}
    />
  );
}
```

`path=editor/session/websockify` is the WebSocket address as seen from the browser; nginx strips the
`/editor/session/` prefix. Closing the tab leaves the session running until it is idle for 30 minutes
(`GDXUNITY_IDLE_SECONDS`), so reopening the page within that time returns to the same editor.

## 7. Operating it

| Task | Command |
|---|---|
| Who has a session | `sudo /opt/gdxunity/editor-session.sh list` |
| Resource use | `sudo docker stats --no-stream` |
| A user's logs (editor output, crash) | `sudo docker logs gdxunity-editor-<user-id>` |
| End a session | `sudo /opt/gdxunity/editor-session.sh stop <user-id>` |
| Delete a user's projects | `sudo docker volume rm gdxunity-editor-data-<user-id>` |
| Back up a user's projects | `sudo docker run --rm -v gdxunity-editor-data-<user-id>:/data:ro -v "$PWD":/out busybox tar czf /out/<user-id>.tgz -C /data .` |
| Deploy a new editor version | Build and load the image, then `stop` every session; the next start uses the new image |

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `start` exits 3 | All `GDXUNITY_MAX_SESSIONS` slots are taken; `list` shows who has them |
| Stuck on "Starting your editor" | `status` never reaches `ready`: `sudo docker logs gdxunity-editor-<id>` and `sudo docker inspect gdxunity-editor-<id> --format '{{json .State.Health}}'` |
| noVNC page loads but stays black | The editor crashed or has no OpenGL: read the container log; the crash log is in `/data/state/gdxunity/logs` inside the user's volume |
| nginx answers 500 on `/editor/session/` | `auth_request` got a status other than 200/401/403 from Spring Boot (a redirect to login, an exception) |
| nginx answers 502 | The upstream address is wrong or the container is gone; the host cannot reach the internal network (check `curl http://<ip>:6080/vnc.html` from the VPS) |
| WebSocket closes after about a minute | A proxy in front of nginx has a short timeout; keep `proxy_read_timeout` high on every hop |
| File dialogs do not appear | `zenity` is missing from the image or the dialog opened behind the editor window |
