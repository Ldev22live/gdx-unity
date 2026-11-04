import { WAVEFORMS, FILTER_TYPES } from "../../audio/dsp";
import { LFO_TARGETS, PRESET_NAMES, type SynthPatch } from "../../audio/synth";
import { PLAYABLE_INSTRUMENTS, type InstrumentType } from "../../audio/song";
import { h } from "../dom";
import type { MusicModel } from "./MusicModel";
import { button, select, slider } from "./ui";

interface NumberField {
  key: keyof SynthPatch;
  label: string;
  min: number;
  max: number;
  step: number;
}

const OSCILLATOR_FIELDS: NumberField[] = [
  { key: "osc2Detune", label: "Osc 2 detune (cents)", min: -100, max: 100, step: 1 },
  { key: "osc2Octave", label: "Osc 2 octave", min: -2, max: 2, step: 1 },
  { key: "osc2Mix", label: "Osc 2 mix", min: 0, max: 1, step: 0.01 },
  { key: "pulseWidth", label: "Pulse width", min: 0.05, max: 0.95, step: 0.01 },
];
const ENVELOPE_FIELDS: NumberField[] = [
  { key: "attack", label: "Attack (s)", min: 0.001, max: 2, step: 0.001 },
  { key: "decay", label: "Decay (s)", min: 0.001, max: 2, step: 0.001 },
  { key: "sustain", label: "Sustain", min: 0, max: 1, step: 0.01 },
  { key: "release", label: "Release (s)", min: 0.001, max: 3, step: 0.001 },
];
const FILTER_FIELDS: NumberField[] = [
  { key: "cutoff", label: "Cutoff (Hz)", min: 20, max: 16000, step: 10 },
  { key: "resonance", label: "Resonance", min: 0.5, max: 20, step: 0.1 },
  { key: "filterEnvOctaves", label: "Filter envelope (octaves)", min: 0, max: 6, step: 0.1 },
];
const LFO_FIELDS: NumberField[] = [
  { key: "lfoRate", label: "LFO rate (Hz)", min: 0.1, max: 20, step: 0.1 },
  { key: "lfoDepth", label: "LFO depth", min: 0, max: 1, step: 0.01 },
];

/** Instrument types the picker offers (the others are only kept when a desktop song has them). */
const OFFERED: readonly InstrumentType[] = PLAYABLE_INSTRUMENTS;

/** Settings of the selected channel: its name, instrument, mixer insert and, for a synth, every synth setting. */
export class InstrumentView {
  readonly root = h("div", { class: "instrument" });

  constructor(private readonly model: MusicModel) {
    this.render();
  }

  render(): void {
    this.root.replaceChildren();
    const channel = this.model.channel;
    if (!channel) {
      this.root.append(h("div", { class: "empty" }, "Add a channel to edit its instrument."));
      return;
    }
    const model = this.model;
    const id = channel.id;
    const name = h("input", { type: "text", value: channel.name }) as HTMLInputElement;
    name.addEventListener("change", () => model.renameChannel(id, name.value));
    const type = select(
      OFFERED.includes(channel.instrument.type) ? OFFERED : [channel.instrument.type, ...OFFERED],
      channel.instrument.type,
      (value) => model.setInstrumentType(id, value),
    );
    const insert = select(
      model.song.mixer.strips.map((_, i) => String(i)),
      String(channel.mixerIndex),
      (value) => model.setChannelMixer(id, Number(value)),
      (i) => `${Number(i) + 1}: ${model.song.mixer.strips[Number(i)]!.name}`,
    );
    this.root.append(
      h("div", { class: "row" }, h("label", { class: "inline" }, "Name", name), h("label", { class: "inline" }, "Instrument", type), h("label", { class: "inline" }, "Insert", insert)),
    );

    if (channel.instrument.type === "DRUMS") {
      this.root.append(h("div", { class: "empty" }, "The drum kit answers to General MIDI notes: kick 36, snare 38, clap 39, closed hat 42, tom 45, open hat 46, crash 49."));
      return;
    }
    if (channel.instrument.type !== "SYNTH") {
      this.root.append(
        h("div", { class: "empty" }, `${channel.instrument.type} instruments are kept in the song but cannot sound in the web version.`),
        button("Switch to the synthesizer", "Use the built-in synthesizer for this channel", () => model.loadPreset(id, "Saw Lead")),
      );
      return;
    }

    const patch = channel.instrument.patch;
    const presets = h("select", { title: "Replace the sound with a preset" }, h("option", { value: "" }, "Preset…")) as HTMLSelectElement;
    for (const preset of PRESET_NAMES) presets.append(h("option", { value: preset }, preset));
    presets.addEventListener("change", () => {
      if (presets.value) model.loadPreset(id, presets.value);
    });
    const group = (title: string, ...children: HTMLElement[]) => h("div", { class: "group" }, h("div", { class: "group-title" }, title), ...children);
    const numbers = (fields: NumberField[]) =>
      fields.map((f) => slider(f.label, f.min, f.max, f.step, patch[f.key] as number, (v) => model.setPatchField(id, f.key, v as never)).row);
    const choice = <T extends string>(label: string, choices: readonly T[], value: T, set: (v: T) => void) =>
      h("label", { class: "inline" }, label, select(choices, value, set));
    this.root.append(
      h("div", { class: "row" }, presets, slider("Volume", 0, 1, 0.01, patch.volume, (v) => model.setPatchField(id, "volume", v)).row),
      h("div", { class: "groups" },
        group("Oscillators", choice("Osc 1", WAVEFORMS, patch.osc1, (v) => model.setPatchField(id, "osc1", v)), choice("Osc 2", WAVEFORMS, patch.osc2, (v) => model.setPatchField(id, "osc2", v)), ...numbers(OSCILLATOR_FIELDS)),
        group("Envelope", ...numbers(ENVELOPE_FIELDS)),
        group("Filter", choice("Type", FILTER_TYPES, patch.filterType, (v) => model.setPatchField(id, "filterType", v)), ...numbers(FILTER_FIELDS)),
        group("LFO", choice("Target", LFO_TARGETS, patch.lfoTarget, (v) => model.setPatchField(id, "lfoTarget", v)), ...numbers(LFO_FIELDS)),
      ),
    );
  }
}
