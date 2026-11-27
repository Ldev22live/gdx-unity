import { defineConfig } from "@playwright/test";

/**
 * Real-browser tests of the web editor (roadmap A14; Playwright approved by the owner on 2026-11-27, TD-111). They run the editor page
 * from the Vite dev server in headless Chromium with real WebGL. Run with `npx playwright test`; `npm test` (vitest) does not run them.
 */
export default defineConfig({
  testDir: "e2e",
  timeout: 120_000,
  workers: 1,
  reporter: [["list"]],
  use: { baseURL: "http://localhost:5183", viewport: { width: 1280, height: 720 } },
  webServer: { command: "npx vite --port 5183 --strictPort", url: "http://localhost:5183/editor.html", reuseExistingServer: false, timeout: 120_000 },
});
