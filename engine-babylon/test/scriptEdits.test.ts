import { describe, expect, it } from "vitest";
import { generateTypeScript } from "../src/visualscript/codegen";
import { generateJvm } from "../src/visualscript/codegenJvm";
import { createDefaultRegistry } from "../src/visualscript/nodes";
import { extractGraph, findEdit } from "../src/visualscript/sourceEmbed";
import { readSampleGraph } from "./graphSupport";

const REGISTRY = createDefaultRegistry();

describe("findEdit", () => {
  it("shouldFindNothingInAScriptThatIsWhatItsGraphGeneratesInAnyLanguage", () => {
    const graph = readSampleGraph("Hop");
    for (const source of [generateTypeScript(graph, REGISTRY), generateJvm(graph, REGISTRY, "java"), generateJvm(graph, REGISTRY, "kotlin")]) {
      const embedded = extractGraph(source);
      expect(findEdit(source, source)).toBeNull();
      expect(embedded.nodes.length).toBe(graph.nodes.length);
    }
  });

  it("shouldNameTheFirstLineEditedByHand", () => {
    const source = generateTypeScript(readSampleGraph("Hop"), REGISTRY);
    const edited = source.replace("onUpdate(", "onUpdate( /* my change */ ");

    const edit = findEdit(edited, source);

    expect(edit).not.toBeNull();
    expect(edit!.text).toContain("my change");
    expect(edited.split("\n")[edit!.line - 1]!.trim()).toBe(edit!.text);
  });

  it("shouldReportALineAddedOrRemovedAndIgnoreEndingsAndTrailingSpaces", () => {
    expect(findEdit("a\nb\nc\nextra\n", "a\nb\nc\n")).toEqual({ line: 4, text: "(an extra line: extra)" });
    expect(findEdit("a\nb\n", "a\nb\nc\n")).toEqual({ line: 3, text: "(a line is missing: c)" });
    expect(findEdit("a  \r\nb\r\n", "a\nb\n")).toBeNull();
  });
});
