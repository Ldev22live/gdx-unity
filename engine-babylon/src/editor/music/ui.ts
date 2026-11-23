import { h } from "../dom";

/** A labelled slider with its number beside it; `onInput` is called on every move with the new value. */
export function slider(
  label: string,
  min: number,
  max: number,
  step: number,
  value: number,
  onInput: (value: number) => void,
  title = "",
): { row: HTMLElement; input: HTMLInputElement } {
  const input = h("input", { type: "range", min: String(min), max: String(max), step: String(step) }) as HTMLInputElement;
  input.value = String(value);
  const readout = h("span", { class: "readout" }, format(value));
  input.addEventListener("input", () => {
    const next = Number(input.value);
    readout.textContent = format(next);
    onInput(next);
  });
  return { row: h("label", { class: "slider", title }, h("span", { class: "name" }, label), input, readout), input };
}

/** A drop-down of fixed choices. */
export function select<T extends string>(
  choices: readonly T[],
  value: T,
  onChange: (value: T) => void,
  describe: (choice: T) => string = (c) => c,
): HTMLSelectElement {
  const element = h("select", {}) as HTMLSelectElement;
  for (const choice of choices) {
    const option = h("option", { value: choice }, describe(choice)) as HTMLOptionElement;
    option.selected = choice === value;
    element.append(option);
  }
  element.addEventListener("change", () => onChange(element.value as T));
  return element;
}

export function button(text: string, title: string, onClick: () => void, className = ""): HTMLButtonElement {
  const element = h("button", { title, class: className }, text) as HTMLButtonElement;
  element.addEventListener("click", onClick);
  return element;
}

/** Rounds for display only. */
function format(value: number): string {
  return Math.abs(value) >= 100 ? String(Math.round(value)) : String(Math.round(value * 1000) / 1000);
}

/** Mouse position inside an element, in its own pixels. */
export function localPoint(element: HTMLElement, event: MouseEvent): { x: number; y: number } {
  const rect = element.getBoundingClientRect();
  return { x: event.clientX - rect.left, y: event.clientY - rect.top };
}
