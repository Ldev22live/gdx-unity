/**
 * Keyboard state for scripts. Keys are `KeyboardEvent.code` values ("KeyW", "ArrowUp", "Space"). "Just pressed" is
 * true from the frame a key goes down until {@link endFrame} is called, like `Gdx.input.isKeyJustPressed`.
 */
export class Input {
  private readonly down = new Set<string>();
  private readonly justPressed = new Set<string>();

  attach(target: HTMLElement | Window): () => void {
    const onDown = (event: Event) => {
      const key = event as KeyboardEvent;
      if (!key.repeat) this.justPressed.add(key.code);
      this.down.add(key.code);
    };
    const onUp = (event: Event) => this.down.delete((event as KeyboardEvent).code);
    const onBlur = () => this.down.clear();
    target.addEventListener("keydown", onDown);
    target.addEventListener("keyup", onUp);
    window.addEventListener("blur", onBlur);
    return () => {
      target.removeEventListener("keydown", onDown);
      target.removeEventListener("keyup", onUp);
      window.removeEventListener("blur", onBlur);
    };
  }

  isDown(...codes: string[]): boolean {
    return codes.some((code) => this.down.has(code));
  }

  isJustPressed(...codes: string[]): boolean {
    return codes.some((code) => this.justPressed.has(code));
  }

  /** Test and tooling hook: press a key without a DOM event. */
  press(code: string): void {
    this.justPressed.add(code);
    this.down.add(code);
  }

  release(code: string): void {
    this.down.delete(code);
  }

  /** Call once per frame after scripts ran. */
  endFrame(): void {
    this.justPressed.clear();
  }
}
