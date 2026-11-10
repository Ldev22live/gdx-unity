import { type Mesh, VertexBuffer } from "@babylonjs/core";
import { type Atlas, frameUv, type Sprite, SpriteAnimationState } from "../sprites/sprite";

/** What the Sprite Animator component of the scene file asks for before its sprite has loaded. */
export interface SpriteAnimatorConfig {
  /** The tag that plays first; null plays every frame. */
  tag: string | null;
  speed: number;
  loop: boolean;
  playOnStart: boolean;
  flipX: boolean;
  flipY: boolean;
  /** Drawn size in scene units; 0 means the sprite's own size. */
  width: number;
  height: number;
  centered: boolean;
}

/**
 * A Sprite Animator on the web (TD-066): one plane whose texture coordinates follow the animation. Scripts reach it as {@code entity.sprite}. The sprite file
 * loads after the scene is built, so what a script asks for before that (a tag to play) is kept and applied when it arrives.
 */
export class SpriteBinding {
  private state: SpriteAnimationState | null = null;
  private atlas: Atlas | null = null;
  private shownFrame = -1;
  /** The reason the sprite could not be loaded, or null. */
  loadError: string | null = null;

  constructor(
    readonly mesh: Mesh,
    private readonly config: SpriteAnimatorConfig,
  ) {}

  /** Called by the loader with the decoded sprite; sizes and shows the plane. */
  attach(sprite: Sprite, atlas: Atlas): void {
    this.atlas = atlas;
    const next = new SpriteAnimationState(sprite);
    next.speed = this.config.speed;
    next.loop = this.config.loop;
    if (this.config.tag !== null) next.play(this.config.tag, true);
    if (!this.config.playOnStart) next.pause();
    this.state = next;
    const width = this.config.width > 0 ? this.config.width : sprite.width;
    const height = this.config.height > 0 ? this.config.height : sprite.height;
    this.applyScale();
    this.mesh.position.x = this.config.centered ? 0 : width / 2;
    this.mesh.position.y = this.config.centered ? 0 : height / 2;
    this.mesh.setEnabled(true);
    this.applyFrame();
  }

  get loaded(): boolean {
    return this.state !== null;
  }

  private require(): SpriteAnimationState {
    if (!this.state) throw new Error(this.loadError ?? "The sprite is not loaded yet");
    return this.state;
  }

  /** Plays a tag from its start, or keeps playing it when it already plays (so it may be called every frame); null plays all frames. Throws for an unknown tag. */
  play(tag: string | null, restart = false): void {
    if (!this.state) {
      this.config.tag = tag;
      this.config.playOnStart = true;
      return;
    }
    this.state.play(tag, restart);
    this.config.tag = tag;
  }

  pause(): void {
    this.config.playOnStart = false;
    this.state?.pause();
  }

  resume(): void {
    this.config.playOnStart = true;
    this.state?.resume();
  }

  stop(): void {
    this.config.playOnStart = false;
    this.state?.stop();
    this.applyFrame();
  }

  /** Mirrors the sprite sideways (a character turning round); takes effect at once, also before the sprite has loaded. */
  setFlipX(flip: boolean): void {
    this.config.flipX = flip;
    this.applyScale();
  }

  setFlipY(flip: boolean): void {
    this.config.flipY = flip;
    this.applyScale();
  }

  private applyScale(): void {
    if (!this.state) return;
    const sprite = this.state.sprite;
    const width = this.config.width > 0 ? this.config.width : sprite.width;
    const height = this.config.height > 0 ? this.config.height : sprite.height;
    this.mesh.scaling.set(this.config.flipX ? -width : width, this.config.flipY ? -height : height, 1);
  }

  setSpeed(speed: number): void {
    if (!(speed >= 0)) throw new Error(`speed cannot be negative or NaN: ${speed}`);
    this.config.speed = speed;
    if (this.state) this.state.speed = speed;
  }

  get playing(): boolean {
    return this.state?.playing ?? false;
  }

  get finished(): boolean {
    return this.state?.finished ?? false;
  }

  get currentTag(): string | null {
    return this.state?.tag ?? this.config.tag;
  }

  /** The index of the frame shown now. */
  get frame(): number {
    return this.require().frame;
  }

  /** Moves the animation on by {@code seconds} and shows the frame it reaches. */
  update(seconds: number): void {
    if (!this.state) return;
    this.state.advance(seconds);
    this.applyFrame();
  }

  private applyFrame(): void {
    if (!this.state || !this.atlas) return;
    const frame = this.state.frame;
    if (frame === this.shownFrame) return;
    this.shownFrame = frame;
    const uv = frameUv(this.atlas, frame);
    this.mesh.updateVerticesData(VertexBuffer.UVKind, [uv.u0, uv.v0, uv.u1, uv.v0, uv.u1, uv.v1, uv.u0, uv.v1]);
  }
}
