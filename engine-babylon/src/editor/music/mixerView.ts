import { EFFECT_TYPES, PARAMETER_RANGES, type EffectType } from "../../audio/effects";
import { MAX_EFFECTS, MAX_VOLUME } from "../../audio/mixer";
import type { StripData } from "../../audio/song";
import { h } from "../dom";
import type { MusicModel } from "./MusicModel";
import { button, slider } from "./ui";

/**
 * The mixer: eight inserts and the master, each with volume, pan, mute, solo, a level meter and up to four effects.
 * Knob moves go to the audio thread live (see MusicModel's `strip` and `effect` changes), so dragging never restarts
 * a reverb tail. The panel redraws only when the song's structure changes, never while a knob is being dragged.
 */
export class MixerView {
  readonly root = h("div", { class: "mixer" });
  private meters: { left: HTMLElement; right: HTMLElement }[] = [];
  private masterMeter: { left: HTMLElement; right: HTMLElement } | null = null;

  constructor(private readonly model: MusicModel) {
    this.render();
  }

  /** Updates the level meters from the audio thread's peaks (each 0..1+). */
  setMeters(strips: [number, number][], master: [number, number]): void {
    strips.forEach(([l, r], i) => this.paint(this.meters[i], l, r));
    this.paint(this.masterMeter ?? undefined, master[0], master[1]);
  }

  private paint(meter: { left: HTMLElement; right: HTMLElement } | undefined, left: number, right: number): void {
    if (!meter) return;
    meter.left.style.height = `${Math.min(left, 1.2) * 100}%`;
    meter.right.style.height = `${Math.min(right, 1.2) * 100}%`;
    meter.left.classList.toggle("hot", left > 1);
    meter.right.classList.toggle("hot", right > 1);
  }

  render(): void {
    this.meters = [];
    this.root.replaceChildren();
    const song = this.model.song;
    song.mixer.strips.forEach((strip, i) => this.root.append(this.strip(i, strip)));
    this.root.append(this.strip("master", song.mixer.master));
  }

  private strip(index: number | "master", data: StripData): HTMLElement {
    const model = this.model;
    const volume = slider("Vol", 0, MAX_VOLUME, 0.01, data.volume, (v) => model.setStrip(index, { volume: v }));
    const pan = slider("Pan", -1, 1, 0.01, data.pan, (v) => model.setStrip(index, { pan: v }));
    const mute = h("button", { class: data.muted ? "toggle on" : "toggle", title: "Mute" }, "M") as HTMLButtonElement;
    mute.addEventListener("click", () => {
      data.muted = !data.muted;
      mute.classList.toggle("on", data.muted);
      model.setStrip(index, { muted: data.muted });
    });
    const solo = h("button", { class: data.solo ? "toggle on" : "toggle", title: "Solo" }, "S") as HTMLButtonElement;
    solo.addEventListener("click", () => {
      data.solo = !data.solo;
      solo.classList.toggle("on", data.solo);
      model.setStrip(index, { solo: data.solo });
    });
    const left = h("div", { class: "bar" });
    const right = h("div", { class: "bar" });
    if (index === "master") this.masterMeter = { left, right };
    else this.meters[index] = { left, right };

    const effects = h("div", { class: "effects" });
    data.effects.forEach((effect, effectIndex) => {
      const box = h("div", { class: "effect" });
      const remove = button("✕", "Remove effect", () => model.removeEffect(index, effectIndex), "small");
      box.append(h("div", { class: "effect-title" }, effect.type, remove));
      const ranges = PARAMETER_RANGES[effect.type as EffectType] ?? {};
      for (const [name, value] of Object.entries(effect.params)) {
        const range = ranges[name] ?? { min: 0, max: 1, step: 0.01 };
        box.append(slider(name, range.min, range.max, range.step, value, (v) => model.setEffectParameter(index, effectIndex, name, v)).row);
      }
      effects.append(box);
    });
    if (data.effects.length < MAX_EFFECTS) {
      const add = h("select", { title: "Add an effect" }, h("option", { value: "" }, "+ effect")) as HTMLSelectElement;
      for (const type of EFFECT_TYPES) add.append(h("option", { value: type }, type));
      add.addEventListener("change", () => {
        if (add.value) model.addEffect(index, add.value);
      });
      effects.append(add);
    }

    const name = h("input", { type: "text", value: data.name, class: "strip-name" }) as HTMLInputElement;
    name.addEventListener("change", () => {
      data.name = name.value;
      model.edit("Rename insert", (s) => {
        (index === "master" ? s.mixer.master : s.mixer.strips[index]!).name = name.value;
      });
    });
    return h("div", { class: index === "master" ? "strip master" : "strip" }, name, h("div", { class: "strip-body" }, h("div", { class: "meter" }, left, right), h("div", { class: "knobs" }, volume.row, pan.row, h("div", { class: "row" }, mute, solo))), effects);
  }
}
