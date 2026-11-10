/** Keys other than letters, digits and function keys, by libGDX's name, and the `KeyboardEvent.code` the browser reports for them. */
const NAMED_KEYS: Record<string, string> = {
  UP: "ArrowUp",
  DOWN: "ArrowDown",
  LEFT: "ArrowLeft",
  RIGHT: "ArrowRight",
  SPACE: "Space",
  ENTER: "Enter",
  ESCAPE: "Escape",
  TAB: "Tab",
  BACKSPACE: "Backspace",
  SHIFT_LEFT: "ShiftLeft",
  SHIFT_RIGHT: "ShiftRight",
  CONTROL_LEFT: "ControlLeft",
  CONTROL_RIGHT: "ControlRight",
  ALT_LEFT: "AltLeft",
  ALT_RIGHT: "AltRight",
};

/** A key of a Key Down or Input Axis node: the libGDX name (upper case) and the browser's key code. */
export interface KeyName {
  name: string;
  code: string;
}

function codeOf(name: string): string | undefined {
  if (/^[A-Z]$/.test(name)) return `Key${name}`;
  const digit = /^NUM_([0-9])$/.exec(name);
  if (digit) return `Digit${digit[1]}`;
  if (/^F([1-9]|1[0-2])$/.test(name)) return name;
  return NAMED_KEYS[name];
}

/**
 * Reads the comma separated key names of a Key Down or Input Axis node. Names are libGDX's (`A`, `LEFT`, `SPACE`, `SHIFT_LEFT`),
 * in any case. The desktop knows more keys than the web player maps.
 *
 * @throws Error naming the key that is not known, or has no browser equivalent
 */
export function parseKeyNames(names: string): KeyName[] {
  return names.split(",").map((raw) => {
    const name = raw.trim().toUpperCase();
    const code = codeOf(name);
    if (!code) {
      throw new Error(`Unknown key '${raw.trim()}'. Use libGDX key names such as A, LEFT, SPACE or SHIFT_LEFT (the web player knows letters, NUM_0 to NUM_9, F1 to F12, the arrows, Space, Enter, Escape, Tab, Backspace, and the left and right Shift, Control and Alt keys)`);
    }
    return { name, code };
  });
}
