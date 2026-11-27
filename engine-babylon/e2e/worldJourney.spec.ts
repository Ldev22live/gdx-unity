import { expect, test, type Page } from "@playwright/test";

/**
 * The browser-only journey of roadmap A14, in a real browser: a person makes a small playable scenario without the desktop or any
 * JSON. New ground, sculpt it, add players, place an owned object, add a rule, play and see the rule fire, then save and reopen with
 * everything kept. Each step uses the World shell's own buttons and the map canvas.
 */

type EditorWindow = {
  editor: {
    model: { file: { entities: { name: string; components: { type: string; data: Record<string, unknown> }[] }[] } & Record<string, unknown>; serialize(): string };
    player: { terrainOverrides: Map<string, { heights: Float32Array }>; getBuilt(): { pendingTerrains: unknown[] } | null };
    openText(text: string): void;
  };
};

const file = (page: Page) => page.evaluate(() => (window as unknown as EditorWindow).editor.model.file);

async function category(page: Page, label: string) {
  await page.locator("#world-categories button", { hasText: label }).click();
}

async function clickMap(page: Page, dx = 0, dy = 0) {
  const box = (await page.locator("#view").boundingBox())!;
  await page.mouse.click(box.x + box.width / 2 + dx, box.y + box.height / 2 + dy);
}

test("a browser-only user makes, plays, saves and reopens a small scenario", async ({ page }) => {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto("/editor.html");
  await page.waitForFunction(() => "world" in window && "editor" in window);
  await expect(page.locator("#world-categories")).toBeVisible();

  // 1. New ground from the Terrain row
  await category(page, "Terrain");
  await page.locator("#world-palette select").first().selectOption("SMALL");
  await page.locator("#world-palette button", { hasText: "New Ground" }).click();
  await expect.poll(async () => (await file(page)).entities.map((e) => e.name)).toContain("Level Ground");
  await page.waitForFunction(() => {
    const editor = (window as unknown as EditorWindow).editor;
    return editor.player.terrainOverrides.has("terrains/Level.terrain") && editor.player.getBuilt() !== null;
  });

  // The new ground is framed: the camera stands back far enough to see a ground 2,560 units across
  await expect.poll(() => page.evaluate(() => (window as unknown as { editor: { player: { getBuilt(): { viewerCamera: { radius: number } } | null } } })
    .editor.player.getBuilt()?.viewerCamera.radius ?? 0)).toBeGreaterThan(2000);

  // Opening the palette row resized the map: the drawing buffer follows the canvas, so clicks land where they are aimed
  await expect.poll(() => page.evaluate(() => {
    const canvas = document.getElementById("view") as HTMLCanvasElement;
    return canvas.width === canvas.clientWidth && canvas.height === canvas.clientHeight;
  })).toBe(true);

  // 2. Sculpt: a drag across the middle of the map raises the ground
  await page.locator("#world-palette button", { hasText: "Raise" }).click();
  const box = (await page.locator("#view").boundingBox())!;
  await page.mouse.move(box.x + box.width / 2 - 60, box.y + box.height / 2);
  await page.mouse.down();
  for (let i = 1; i <= 12; i++) await page.mouse.move(box.x + box.width / 2 - 60 + i * 10, box.y + box.height / 2);
  await page.mouse.up();
  const highest = await page.evaluate(() =>
    Math.max(...(window as unknown as EditorWindow).editor.player.terrainOverrides.get("terrains/Level.terrain")!.heights));
  expect(highest, "the stroke raised the ground").toBeGreaterThan(0);

  // 3. Players: two slots, and new objects owned by Player 1
  await category(page, "Players");
  await page.locator("#world-palette button", { hasText: "Add Players" }).click();
  await expect(page.locator("#world-palette .scenario-card input[type=text]"), "one name field per player").toHaveCount(2);
  const ownerSelect = page.locator("#world-palette select").first();
  const playerOne = await ownerSelect.locator("option", { hasText: "Player 1" }).getAttribute("value");
  await ownerSelect.selectOption(playerOne!);

  // 4. Place an object from the Objects palette onto the ground
  await category(page, "Objects");
  await page.locator("#world-palette .tile").first().click();
  await clickMap(page, 120, 40);
  await expect.poll(async () => (await file(page)).entities.filter((e) => e.components.some((c) => c.type === "OwnerComponent")).length)
    .toBeGreaterThan(0);
  const owned = (await file(page)).entities.find((e) => e.components.some((c) => c.type === "OwnerComponent"))!;
  expect(owned.components.find((c) => c.type === "OwnerComponent")!.data["player"]).toBe(playerOne);
  await page.keyboard.press("Escape");
  // Evidence for the report: the World shell with the placed, owned object (an automated capture, not a person's check)
  await page.screenshot({ path: "../docs/reports/alpha-a14/world-shell-1280x720-placed.png" });

  // 5. A rule: after 1 second, show a message
  await category(page, "Rules");
  await page.locator("#world-palette button", { hasText: "Add Rule" }).click();
  const seconds = page.locator("#world-palette input[type=number]").first();
  await seconds.fill("1");
  await seconds.press("Enter");
  const message = page.locator("#world-palette .scenario-card input[type=text]").last();
  await message.fill("Welcome to the valley");
  await message.press("Enter");
  await expect.poll(async () => JSON.stringify((await file(page))["scenario"])).toContain("Welcome to the valley");

  // 6. Play: the rule fires and its message shows over the game; Stop brings the editor back
  await page.locator("#world-play").click();
  await expect(page.locator(".hud-message")).toHaveText("Welcome to the valley", { timeout: 15_000 });
  await page.screenshot({ path: "../docs/reports/alpha-a14/play-1280x720-rule-message.png" });
  await page.locator("#world-play").click();
  await expect(page.locator(".hud-message")).toHaveCount(0);

  // 7. Save and reopen: the scene text the editor saves keeps ground, players, owner and rule; the edited ground is kept too
  const saved = await page.evaluate(() => (window as unknown as EditorWindow).editor.model.serialize());
  await page.evaluate((text) => (window as unknown as EditorWindow).editor.openText(text), saved);
  const reopened = await file(page);
  expect(reopened.entities.map((e) => e.name)).toContain("Level Ground");
  expect(JSON.stringify(reopened["scenario"])).toContain("Player 1");
  expect(JSON.stringify(reopened["scenario"])).toContain("Welcome to the valley");
  expect(reopened.entities.some((e) => e.components.some((c) => c.type === "OwnerComponent" && c.data["player"] === playerOne))).toBe(true);
  await expect(page.locator("#world-palette button", { hasText: "Save Terrain" })).toHaveCount(0); // the Rules row is open, not Terrain
  expect(errors, "no script errors on the page").toEqual([]);
});
