import { defineConfig } from "vitest/config";

export default defineConfig({
  // Relative asset URLs, so the built player can be served from any folder (the same way engine-web is deployed)
  base: "./",
  build: {
    chunkSizeWarningLimit: 4000,
    // Two pages: the player (index.html) and the editor (editor.html)
    rollupOptions: { input: { player: "index.html", editor: "editor.html" } },
  },
  test: { environment: "node", include: ["test/**/*.test.ts"] },
});
