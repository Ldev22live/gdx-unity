import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { generateTypeScript } from "../src/visualscript/codegen";
import { generateJvm, JVM_EXTENSIONS, type JvmLanguage } from "../src/visualscript/codegenJvm";
import { Graph, serializeGraph } from "../src/visualscript/graph";
import { createDefaultRegistry, NODE } from "../src/visualscript/nodes";
import { appendGraph, EMBED_BEGIN, EMBED_END, extractGraph, hasEmbeddedGraph } from "../src/visualscript/sourceEmbed";
import { Builder, readSampleGraph } from "./graphSupport";

/**
 * Scripts travel between the web and the desktop editor (TD-045). The desktop's `GraphSourceInterchangeTest` does the
 * same checks against the same files: `test/golden/<Name>.<ext>` is written by the web generators, `test/golden/desktop/`
 * by the desktop's. Each side must produce, for the sample graphs, the code the other side wrote, and must read the
 * graph out of the other side's file. Set FORGEROOM_UPDATE_GOLDEN=1 to rewrite this side's files.
 */

const REGISTRY = createDefaultRegistry();
const SAMPLES = ["Greeter", "Hop", "Spinner"];
const GOLDEN = join(import.meta.dirname, "golden");
const DESKTOP_GOLDEN = join(GOLDEN, "desktop");
const UPDATE = process.env["FORGEROOM_UPDATE_GOLDEN"] === "1";
const BLOCK_NOTE = "\n// The block below lets Forgeroom";

const LANGUAGES: { name: string; extension: string; generate: (graph: Graph) => string }[] = [
  { name: "TypeScript", extension: ".ts", generate: (graph) => generateTypeScript(graph, REGISTRY) },
  ...(["java", "kotlin"] as JvmLanguage[]).map((language) => ({
    name: language,
    extension: JVM_EXTENSIONS[language],
    generate: (graph: Graph) => generateJvm(graph, REGISTRY, language),
  })),
];

/** The code of a generated script, without the embedded graph. */
function codeOf(source: string): string {
  const at = source.indexOf(BLOCK_NOTE);
  return at < 0 ? source : source.slice(0, at);
}

function sameGraph(a: Graph, b: Graph): void {
  expect(JSON.parse(serializeGraph(a))).toEqual(JSON.parse(serializeGraph(b)));
}

describe("scripts with an embedded graph", () => {
  it("shouldReadTheGraphBackFromEveryLanguage", () => {
    const graph = readSampleGraph("Hop");
    for (const language of LANGUAGES) {
      const source = language.generate(graph);
      expect(hasEmbeddedGraph(source), language.name).toBe(true);
      sameGraph(extractGraph(source), graph);
    }
  });

  it("shouldKeepNonAsciiAndQuotesInTheEmbeddedGraph", () => {
    const graph = new Graph('Gruß "quoted" $x');
    graph.variables.push({ name: "label", type: "STRING", value: 'héllo "wörld" \ ✓' });
    const read = extractGraph(appendGraph("class A {}\n", graph));
    sameGraph(read, graph);
  });

  it("shouldRefuseAFileWithoutAnEmbeddedGraph", () => {
    expect(() => extractGraph("export class A {}\n")).toThrow(/no embedded graph/);
  });

  it("shouldRefuseADamagedBlock", () => {
    const damaged = `class A {}\n${EMBED_BEGIN}\n// !!!not base64!!!\n${EMBED_END}\n`;
    expect(() => extractGraph(damaged)).toThrow(/damaged/);
  });

  it("shouldNotChangeTheCodeAboveTheBlock", () => {
    const code = "class A {}\n";
    expect(appendGraph(code, new Graph("A")).startsWith(code)).toBe(true);
  });

  it("shouldRefuseALoopInEveryLanguageBeforeEmbedding", () => {
    const graph = new Graph("loop");
    const b = new Builder(graph);
    const begin = b.node(NODE.beginPlay);
    const print = b.node(NODE.print);
    b.exec(begin, print);
    b.exec(print, print);
    for (const language of LANGUAGES) expect(() => language.generate(graph), language.name).toThrow(/loop back/);
  });
});

describe("scripts shared with the desktop editor", () => {
  for (const name of SAMPLES) {
    for (const language of LANGUAGES) {
      it(`should write ${name}${language.extension} like the desktop and read the desktop's`, () => {
        const graph = readSampleGraph(name);
        const source = language.generate(graph);
        const own = join(GOLDEN, `${name}${language.extension}`);
        if (UPDATE) {
          mkdirSync(GOLDEN, { recursive: true });
          writeFileSync(own, source);
        }
        const desktop = join(DESKTOP_GOLDEN, `${name}${language.extension}`);
        expect(existsSync(own), `${own} is missing: run with FORGEROOM_UPDATE_GOLDEN=1`).toBe(true);
        expect(existsSync(desktop), `${desktop} is missing: run the desktop test with FORGEROOM_UPDATE_GOLDEN=1`).toBe(true);
        expect(codeOf(source)).toBe(codeOf(readFileSync(own, "utf8")));
        expect(codeOf(source)).toBe(codeOf(readFileSync(desktop, "utf8")));
        sameGraph(extractGraph(readFileSync(desktop, "utf8")), graph);
      });
    }
  }
});
