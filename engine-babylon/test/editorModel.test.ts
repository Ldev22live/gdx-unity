import { describe, expect, it } from "vitest";
import { EditorModel, emptyScene, MAX_UNDO_STEPS } from "../src/editor/EditorModel";
import { parseSceneFile } from "../src/scene/types";
import { loadSample } from "./support";

function newModel(): EditorModel {
  return new EditorModel(emptyScene("Test"));
}

describe("EditorModel edits", () => {
  it("shouldAddAnEntityWithTheComponentsOfItsPresetAndSelectIt", () => {
    const model = newModel();
    const id = model.addEntity("box");
    expect(model.selection).toBe(id);
    const entity = model.require(id);
    expect(entity.name).toBe("Box");
    expect(entity.components.map((c) => c.type)).toEqual(["MeshPrimitiveComponent"]);
    expect(entity.parentId).toBeNull();
  });

  it("shouldGiveNewEntitiesUniqueNames", () => {
    const model = newModel();
    model.addEntity("box");
    const second = model.addEntity("box");
    const third = model.addEntity("box");
    expect(model.require(second).name).toBe("Box 2");
    expect(model.require(third).name).toBe("Box 3");
  });

  it("shouldDeleteAnEntityTogetherWithItsChildren", () => {
    const model = newModel();
    const parent = model.addEntity("empty");
    const child = model.addEntity("box", parent);
    const grandchild = model.addEntity("sphere", child);
    const other = model.addEntity("empty");
    model.deleteEntity(parent);
    expect(model.find(parent)).toBeUndefined();
    expect(model.find(child)).toBeUndefined();
    expect(model.find(grandchild)).toBeUndefined();
    expect(model.find(other)).toBeDefined();
  });

  it("shouldClearTheSelectionWhenTheSelectedEntityIsDeleted", () => {
    const model = newModel();
    const id = model.addEntity("box");
    model.deleteEntity(id);
    expect(model.selection).toBeNull();
  });

  it("shouldDuplicateASubtreeWithFreshIdsAndRemappedParents", () => {
    const model = newModel();
    const parent = model.addEntity("empty");
    const child = model.addEntity("box", parent);
    const copyId = model.duplicateEntity(parent);
    const copy = model.require(copyId);
    expect(copy.name).toBe("Empty Copy");
    const copiedChild = model.childrenOf(copyId);
    expect(copiedChild).toHaveLength(1);
    expect(copiedChild[0]!.id).not.toBe(child);
    expect(copiedChild[0]!.parentId).toBe(copyId);
    expect(model.childrenOf(parent)).toHaveLength(1);
    expect(model.selection).toBe(copyId);
  });

  it("shouldRefuseToMakeAnEntityItsOwnDescendantsChild", () => {
    const model = newModel();
    const parent = model.addEntity("empty");
    const child = model.addEntity("empty", parent);
    expect(() => model.reparent(parent, child)).toThrow(/descendants/);
    expect(() => model.reparent(parent, parent)).toThrow(/itself/);
    model.reparent(child, null);
    expect(model.require(child).parentId).toBeNull();
  });

  it("shouldSetANestedComponentField", () => {
    const model = newModel();
    const id = model.addEntity("rectangle");
    model.setComponentField(id, 0, ["color", "r"], 0.25);
    model.setComponentField(id, 0, ["width"], 320);
    const data = model.require(id).components[0]!.data;
    expect((data["color"] as { r: number }).r).toBe(0.25);
    expect(data["width"]).toBe(320);
  });

  it("shouldFailLoudlyForAMissingComponentOrUnknownPreset", () => {
    const model = newModel();
    const id = model.addEntity("empty");
    expect(() => model.setComponentField(id, 3, ["x"], 1)).toThrow(/no component at index 3/);
    expect(() => model.addEntity("dragon")).toThrow(/Unknown entity preset/);
    expect(() => model.addComponent(id, "NopeComponent")).toThrow(/Unknown component type/);
  });

  it("shouldAddAndRemoveComponents", () => {
    const model = newModel();
    const id = model.addEntity("empty");
    model.addComponent(id, "RigidBody2DComponent");
    model.addComponent(id, "BoxCollider2DComponent");
    expect(model.require(id).components.map((c) => c.type)).toEqual(["RigidBody2DComponent", "BoxCollider2DComponent"]);
    model.removeComponent(id, 0);
    expect(model.require(id).components.map((c) => c.type)).toEqual(["BoxCollider2DComponent"]);
  });
});

describe("EditorModel undo and redo", () => {
  it("shouldUndoAndRedoAnAddAndRestoreTheSelection", () => {
    const model = newModel();
    const first = model.addEntity("box");
    model.addEntity("sphere");
    expect(model.file.entities).toHaveLength(2);
    model.undo();
    expect(model.file.entities).toHaveLength(1);
    // Undo restores the selection as it was before the undone edit: the box, which was selected after it was added
    expect(model.selection).toBe(first);
    model.undo();
    expect(model.file.entities).toHaveLength(0);
    model.redo();
    expect(model.file.entities.map((e) => e.id)).toEqual([first]);
    model.redo();
    expect(model.file.entities).toHaveLength(2);
    expect(model.canRedo).toBe(false);
  });

  it("shouldBringBackADeletedSubtreeWithUndo", () => {
    const model = newModel();
    const parent = model.addEntity("empty");
    model.addEntity("box", parent);
    const before = JSON.stringify(model.file);
    model.deleteEntity(parent);
    expect(model.file.entities).toHaveLength(0);
    model.undo();
    expect(JSON.stringify(model.file)).toBe(before);
  });

  it("shouldDropTheRedoHistoryWhenANewEditFollowsAnUndo", () => {
    const model = newModel();
    model.addEntity("box");
    model.undo();
    expect(model.canRedo).toBe(true);
    model.addEntity("sphere");
    expect(model.canRedo).toBe(false);
  });

  it("shouldCoalesceRepeatedEditsOfTheSameFieldIntoOneStep", () => {
    const model = newModel();
    const id = model.addEntity("rectangle");
    model.setComponentField(id, 0, ["width"], 101);
    model.setComponentField(id, 0, ["width"], 102);
    model.setComponentField(id, 0, ["width"], 103);
    expect(model.require(id).components[0]!.data["width"]).toBe(103);
    model.undo();
    // One undo reverts all three typed values, back to the value before typing began
    expect(model.require(id).components[0]!.data["width"]).toBe(100);
  });

  it("shouldKeepEditsOfDifferentFieldsAsSeparateSteps", () => {
    const model = newModel();
    const id = model.addEntity("rectangle");
    model.setComponentField(id, 0, ["width"], 200);
    model.setComponentField(id, 0, ["height"], 300);
    model.undo();
    const data = model.require(id).components[0]!.data;
    expect(data["width"]).toBe(200);
    expect(data["height"]).toBe(100);
  });

  it("shouldOnlyKeepTheLastUndoSteps", () => {
    const model = newModel();
    for (let i = 0; i < MAX_UNDO_STEPS + 20; i++) model.addEntity("empty");
    let steps = 0;
    while (model.canUndo) {
      model.undo();
      steps++;
    }
    expect(steps).toBe(MAX_UNDO_STEPS);
  });

  it("shouldTrackUnsavedChanges", () => {
    const model = newModel();
    expect(model.isDirty).toBe(false);
    model.addEntity("box");
    expect(model.isDirty).toBe(true);
    model.markSaved();
    expect(model.isDirty).toBe(false);
    model.undo();
    expect(model.isDirty).toBe(true);
  });

  it("shouldTellListenersWhetherOnlyTransformsChanged", () => {
    const model = newModel();
    const id = model.addEntity("box");
    const kinds: string[] = [];
    model.addListener((kind) => kinds.push(kind));
    model.setTransform(id, { position: { x: 5, y: 0, z: 0 }, rotation: { x: 0, y: 0, z: 0, w: 1 }, scale: { x: 1, y: 1, z: 1 } });
    model.rename(id, "Crate");
    expect(kinds).toEqual(["transform", "scene"]);
  });
});

describe("EditorModel file format", () => {
  it("shouldWriteTheSameSceneThatWasLoaded", () => {
    const sample = loadSample("platformer-2d.json");
    const model = new EditorModel(sample);
    expect(parseSceneFile(model.serialize())).toEqual(sample);
  });

  it("shouldWriteAFileThatTheDesktopEditorsSchemaAccepts", () => {
    const model = newModel();
    const parent = model.addEntity("empty");
    model.addEntity("box", parent);
    model.addEntity("skyLight");
    const written = parseSceneFile(model.serialize());
    expect(written.schemaVersion).toBe(1);
    for (const entity of written.entities) {
      expect(typeof entity.id).toBe("string");
      expect(entity.transform.rotation.w).toBe(1);
      expect(Array.isArray(entity.components)).toBe(true);
    }
  });

  it("shouldNotLetLaterEditsChangeTheFileThatWasPassedIn", () => {
    const sample = loadSample("collector-3d.json");
    const before = JSON.stringify(sample);
    const model = new EditorModel(sample);
    model.deleteEntity(model.file.entities[0]!.id);
    expect(JSON.stringify(sample)).toBe(before);
  });
});
