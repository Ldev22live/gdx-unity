import type { ProfileFix } from "../runtime/PlayProfiles";
import type { EditorModel } from "./EditorModel";
import { COMPONENT_TEMPLATES, newId } from "./presets";

/** The desktop's `CharacterController3DComponent` defaults (radius, height, slope, step, mass), scene units. */
const CHARACTER_DEFAULTS = { radius: 30, height: 80, slopeLimitDegrees: 45, stepHeight: 30, mass: 70, gravityScale: 1, layer: 0, collisionMask: 65535 };
/** The camera a fix adds (the desktop's `ProfileFixes.CAMERA_NAME`). */
export const CAMERA_NAME = "Main Camera";

/**
 * Applies a safe fix the web's Play offered (TD-110; the desktop's `ProfileFixes.apply`): make an entity the player, give the player
 * a Character Controller 3D, or add a camera, as one undo step.
 *
 * @returns why it could not be applied, or null when it was
 */
export function applyProfileFix(model: EditorModel, fix: ProfileFix): string | null {
  if (fix.kind === "ADD_CAMERA") {
    model.edit(fix.label, "scene", (file) => file.entities.push({
      id: newId(), name: CAMERA_NAME, active: true, parentId: null,
      transform: { position: { x: 0, y: 0, z: 0 }, rotation: { x: 0, y: 0, z: 0, w: 1 }, scale: { x: 1, y: 1, z: 1 } },
      components: [COMPONENT_TEMPLATES["CameraComponent"]!()],
    }));
    return null;
  }
  const entity = fix.entityId === undefined ? undefined : model.find(fix.entityId);
  if (!entity) return `The entity this fix is for is no longer in the scene`;
  const type = fix.kind === "MAKE_PLAYER" ? "PlayerComponent" : "CharacterController3DComponent";
  if (entity.components.some((c) => c.type === type)) return `${entity.name} already has ${type === "PlayerComponent" ? "Player" : "a Character Controller 3D"}`;
  const data = fix.kind === "MAKE_PLAYER" ? { useProfileMovement: false } : { ...CHARACTER_DEFAULTS };
  model.edit(fix.label, "scene", () => model.require(entity.id).components.push({ type, enabled: true, data }));
  return null;
}
