import { parseGraph, type Graph } from "./graph";

/** Reads a text file the page can reach; replaced in tests. */
export type TextFetcher = (path: string) => Promise<string>;

export const fetchText: TextFetcher = async (path) => {
  const response = await fetch(path);
  if (!response.ok) throw new Error(`${path}: HTTP ${response.status}`);
  return response.text();
};

/**
 * The graphs the web player and the Visual Scripting tab share, by the path a Graph Script component names
 * (`graphs/Spinner.graph`). A graph the tab is editing replaces the file of the same path, so Play runs the edit
 * without saving first; a graph not in the store is read from the page's files and kept.
 */
export class GraphStore {
  private readonly graphs = new Map<string, Graph>();
  private readonly pending = new Map<string, Promise<Graph>>();

  constructor(private readonly fetcher: TextFetcher = fetchText) {}

  /** Puts {@code graph} in the store under {@code path}, replacing the file's version. */
  set(path: string, graph: Graph): void {
    this.graphs.set(path, graph);
  }

  /** The graph at {@code path} if it is in the store already. */
  peek(path: string): Graph | undefined {
    return this.graphs.get(path);
  }

  has(path: string): boolean {
    return this.graphs.has(path);
  }

  /** The graph at {@code path}, read from the page's files when it is not in the store. @throws Error naming the path. */
  async load(path: string): Promise<Graph> {
    const known = this.graphs.get(path);
    if (known) return known;
    let loading = this.pending.get(path);
    if (!loading) {
      loading = this.fetcher(path)
        .then((text) => {
          const graph = parseGraph(text);
          // An edit that arrived while the file was loading wins
          const existing = this.graphs.get(path);
          if (existing) return existing;
          this.graphs.set(path, graph);
          return graph;
        })
        .catch((error: unknown) => {
          throw new Error(`Graph ${path}: ${error instanceof Error ? error.message : String(error)}`);
        })
        .finally(() => this.pending.delete(path));
      this.pending.set(path, loading);
    }
    return loading;
  }
}
