// Drives a headless Edge (DevTools protocol) against the built web player: loads a scene, presses Play, takes screenshots,
// and reports the status line and any console errors. Usage: node check.mjs <port> <devtools-port> <scene.json> <out-prefix>
import { writeFileSync } from 'node:fs';

const [, , playerPort, debugPort, scene, outPrefix] = process.argv;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

let targets;
for (let i = 0; i < 50; i++) {
  try {
    targets = await (await fetch(`http://127.0.0.1:${debugPort}/json`)).json();
    if (targets.some((t) => t.type === 'page')) break;
  } catch {
    // the browser is still starting
  }
  await sleep(200);
}
const page = targets.find((t) => t.type === 'page');
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise((resolve) => ws.addEventListener('open', resolve));

let nextId = 1;
const pending = new Map();
const problems = [];
ws.addEventListener('message', (event) => {
  const message = JSON.parse(event.data);
  if (message.id && pending.has(message.id)) {
    pending.get(message.id)(message);
    pending.delete(message.id);
  } else if (message.method === 'Runtime.exceptionThrown') {
    problems.push('exception: ' + (message.params.exceptionDetails.exception?.description ?? message.params.exceptionDetails.text));
  } else if (message.method === 'Runtime.consoleAPICalled' && ['error', 'warning'].includes(message.params.type)) {
    problems.push(message.params.type + ': ' + message.params.args.map((a) => a.value ?? a.description).join(' ').slice(0, 300));
  }
});
const send = (method, params = {}) =>
  new Promise((resolve) => {
    const id = nextId++;
    pending.set(id, resolve);
    ws.send(JSON.stringify({ id, method, params }));
  });
const evaluate = async (expression) => (await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })).result?.result?.value;
const shot = async (name) => {
  const result = await send('Page.captureScreenshot', { format: 'png' });
  writeFileSync(`${outPrefix}-${name}.png`, Buffer.from(result.result.data, 'base64'));
};

await send('Runtime.enable');
await send('Page.enable');
await send('Emulation.setDeviceMetricsOverride', { width: 1280, height: 720, deviceScaleFactor: 1, mobile: false });
await send('Page.navigate', { url: `http://127.0.0.1:${playerPort}/` });
await sleep(3000);
console.log('scenes listed:', await evaluate("document.querySelectorAll('#scenes option').length"));
await evaluate(`(() => { const s = document.getElementById('scenes'); s.value = ${JSON.stringify(scene)}; s.dispatchEvent(new Event('change')); return s.value; })()`);
await sleep(2000);
console.log('status after load:', await evaluate("document.getElementById('status').textContent"));
await shot('loaded');
await evaluate("document.getElementById('play').click()");
await sleep(600);
console.log('buttons 0.6 s after Play (play disabled, stop enabled):', await evaluate("[document.getElementById('play').disabled, !document.getElementById('stop').disabled].join(' ')"));
await shot('play-0.6s');
await sleep(4500);
await shot('play-5s');
await sleep(20000);
await shot('play-25s');
console.log('HUD text:', await evaluate("document.getElementById('hud').textContent.slice(0, 140)"));
await evaluate("document.getElementById('stop').click()");
await sleep(500);
console.log('buttons after Stop (play disabled, stop enabled):', await evaluate("[document.getElementById('play').disabled, !document.getElementById('stop').disabled].join(' ')"));
await evaluate("document.getElementById('play').click()");
await sleep(1500);
console.log('buttons after second Play (play disabled, stop enabled):', await evaluate("[document.getElementById('play').disabled, !document.getElementById('stop').disabled].join(' ')"));
await shot('second-play');
console.log('problems:', JSON.stringify(problems, null, 1));
ws.close();
