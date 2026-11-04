import { num, str } from "../scene/types";
import type { TextItem } from "./SceneBuilder";

/** Base text size in CSS pixels; a Text component's {@code scale} multiplies it. */
const BASE_FONT_PX = 16;
/** Gap in CSS pixels between a text anchored to an edge and that edge. */
const EDGE_MARGIN_PX = 12;

/**
 * Draws Text components as HTML over the canvas. The text, colour and offsets are re-read every frame, so scripts that
 * change a component's data (a score, a message) show up at once.
 */
export class Hud {
  private readonly elements: HTMLElement[] = [];

  constructor(private readonly container: HTMLElement, private readonly items: TextItem[]) {
    container.replaceChildren();
    for (let i = 0; i < items.length; i++) {
      const element = document.createElement("div");
      element.className = "hud-text";
      container.appendChild(element);
      this.elements.push(element);
    }
  }

  update(): void {
    this.items.forEach((item, index) => {
      const element = this.elements[index]!;
      const d = item.data;
      element.hidden = !item.entity.node.isEnabled();
      element.textContent = str(d, "text");
      const toByte = (channel: string) => Math.round(num(d, channel) * 255);
      element.style.color = `rgba(${toByte("colorR")}, ${toByte("colorG")}, ${toByte("colorB")}, ${num(d, "colorA")})`;
      element.style.fontSize = `${BASE_FONT_PX * num(d, "scale", 1)}px`;
      placeAtAnchor(element, str(d, "anchor"), num(d, "offsetX", 0), num(d, "offsetY", 0));
    });
  }

  dispose(): void {
    this.container.replaceChildren();
  }
}

/** Positions an element at one of the nine view anchors; a positive {@code offsetY} moves it up, like the scene's Y. */
export function placeAtAnchor(element: HTMLElement, anchor: string, offsetX: number, offsetY: number): void {
  const style = element.style;
  style.left = style.right = style.top = style.bottom = "";
  const transforms: string[] = [];
  const horizontal = anchor.endsWith("LEFT") ? "left" : anchor.endsWith("RIGHT") ? "right" : "center";
  const vertical = anchor.startsWith("TOP") ? "top" : anchor.startsWith("BOTTOM") ? "bottom" : "middle";
  if (horizontal === "left") style.left = `${EDGE_MARGIN_PX + offsetX}px`;
  else if (horizontal === "right") style.right = `${EDGE_MARGIN_PX - offsetX}px`;
  else {
    style.left = `calc(50% + ${offsetX}px)`;
    transforms.push("translateX(-50%)");
  }
  if (vertical === "top") style.top = `${EDGE_MARGIN_PX - offsetY}px`;
  else if (vertical === "bottom") style.bottom = `${EDGE_MARGIN_PX + offsetY}px`;
  else {
    style.top = `calc(50% - ${offsetY}px)`;
    transforms.push("translateY(-50%)");
  }
  style.transform = transforms.join(" ");
  style.textAlign = horizontal === "right" ? "right" : horizontal === "center" ? "center" : "left";
}
