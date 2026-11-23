# Web player browser check (TD-059)

`check.mjs` drives a headless Edge or Chrome through the DevTools protocol against the built web player: it loads a sample scene,
presses Play, takes screenshots (about 0.6 s, 5 s and 25 s later), presses Stop and Play again, and prints the Play and Stop
button states, the HUD text and any console errors. It reads only the player page. Not part of any build.

```
cd engine-babylon && npm run build && npx vite preview --port 4173 --strictPort      # one terminal
msedge --headless=new --remote-debugging-port=9333 --user-data-dir=<scratch folder> --no-first-run \
  --use-angle=swiftshader --enable-unsafe-swiftshader about:blank                    # another
node tools/web-browser-check/check.mjs 4173 9333 physics-sandbox-3d.json <output prefix>
```

Without a graphics card the page renders only a few frames per second, and the physics takes at most 0.1 s of simulated time per
frame, so balls fall slowly: judge the picture after 25 s, not 5 s. Stop only the processes you started (the Edge you launched
with your scratch `--user-data-dir`, the preview server), not every `msedge.exe`.
