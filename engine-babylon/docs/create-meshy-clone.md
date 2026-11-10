# Create a Meshy-style AI 3D Studio on a Social Network with Babylon.js

> Status: design proposal. Not implemented. Nothing here is a binding technical decision (see `TECHNICAL_DECISIONS.md`).
> Scope rule (TD-020 spirit): **original implementation only.** No Meshy code, assets, models, icons, copy or trademarks. Feature *categories* below are generic to the text/image-to-3D product space.

---

## 1. Evidence and method

Target: `https://www.meshy.ai/` (public landing page, not logged in).

| Item | Detail |
|---|---|
| Tool | REA `rea-agents` 6.8.0, `capture-browser-scenario` |
| Evidence IDs | `ev_d2ac982a28ec99742b5c3a8ff4f670fa5103c06c97d178d759c18672a7791a44` (URL-only run), `ev_f6c844ed9ee15e2daaf7c095a98ecd6fab55060a57fac4e2185369e28b7e0e02` (DOM + accessibility tree + network metadata run) |
| Browser | Provider-owned temporary Chromium 156 profile, headless, cleaned up (`terminated-owned-process`) |
| Actions | Navigate to the start URL, wait 6 s. **No login, no clicks, no form input, no extra requests.** |
| Captured | Final URL, DOM snapshot (≈1.6 MB), accessibility tree, network event metadata. Request/response bodies and header values were **not** captured. |

### Limits of this analysis (read before relying on it)

- Only the **public marketing page** was inspected. The authenticated workspace (generation panels, task queue, credit accounting, community feed internals) was **not** observed.
- Everything about Meshy's *backend* (models used, queueing, storage layout) is **unknown**. Section 3 is a list of observations; Sections 4+ are *our own design*, not a reconstruction of Meshy's internals.
- One run at one point in time; marketing copy and numbers can change.

---

## 2. Observed product surface (from the public page)

Observed = read directly from the captured DOM / accessibility tree.

### 2.1 Generation
- Hero prompt: "What will you create in 3D today?" with **Upload a photo or sketch**, **Try images**, **Start Creating**.
- Hero tabs: *Quick Generation*, *3D Printing*, *Games & Media*.
- Feature tabs: *Image to 3D* (with a `/features/image-to-3d` page) and *AI Agent*.
- Claim: text or images to 3D models "in under a minute" (page metadata says 20–30 seconds).
- A "native texture" generation mode is advertised ("Cleanest and Fastest Texturing").

### 2.2 Model quality / game-ready pipeline
- One-click **PBR texturing**: albedo, normal, metallic, roughness; optional HD (4K) texture.
- **Auto-rigging** for humanoid and quadruped characters; **600+ motion presets** (walk, idle, jump, attack…).
- **Low-poly mode** with user-controlled polygon count.
- Stated compatibility: Unity, Unreal, Godot, Blender, Maya. Export: **FBX, GLB, OBJ, USDZ** (3D printing: **STL, OBJ, 3MF**).

### 2.3 3D printing
- Printability report (watertight, volume, hole count, non-manifold edges) with **auto-repair**.
- **Auto-split** into printable parts, multi-colour mapping, hand-off to slicers (Bambu Studio, OrcaSlicer, Cura, etc.), publish to model-sharing sites.

### 2.4 Social / community
- "See what the community is creating": a feed of creations with **creator handle** and two numeric counters per item (likely likes and remixes/downloads; **label not confirmed**).
- A `/discover` destination, creator profiles, and testimonial/case-study content.

### 2.5 Platform / ecosystem
- **Developer API and MCP server** ("Use Meshy in your AI agent", API key based).
- Free tier with sign-up ("Sign Up Free", "Log In", "Contact Sales").

### 2.6 Front-end stack (observed)
| Signal | Observation |
|---|---|
| Framework | Next.js (`/_next/static/...` assets served from a CDN host). |
| Assets | A dedicated CDN host serves static media and ~66 `.glb` references. |
| Third parties | Large ad/analytics surface (Google, Bing/Clarity, TikTok, Snap, LinkedIn, Meta, AppsFlyer, etc.). **Not a design goal for us** (see §10 privacy). |
| 3D viewer | The captured page did not reveal which WebGL library renders the viewer. Mentions of Babylon.js / Three.js appear only in *help text* telling users where they can import GLBs. **Viewer library: unknown.** |

### 2.7 Templates and community feed (`/templates#community-feed`)

Second REA run, same read-only method (no login, no clicks). **Caveat:** the run completed both steps and captured the DOM and accessibility tree, but then hit the 30 s cleanup deadline and REA reported `cleanup_incomplete` (browser transport/profile). I checked afterwards and found no leftover automation Chrome process; a stale `playwright_chromiumdev_profile-*` temp folder that exists predates this work and was left untouched. The page data below comes from the retained `partial_observation`, not a clean final Evidence record, so treat it as observed-but-partial. No evidence ID was issued.

Observed:
- **Template-driven generation.** Page title "AI 3D model templates". A curated *template* is a one-photo recipe: the user supplies a photo and the template supplies the style (chibi figure, brick figure, vinyl figure, marble bust, low-poly buddy, pet memorial, challenge coin, storybook asset, "me and my pet" two-photo variant). Each links to its own page (`/templates/<slug>`) and has **Use Template** and **Save Template** actions.
- **Save counter.** The numbers next to creators are on a **Save Template** button (e.g. 342, 290, 225…), so for templates the counter is *saves*. (The unlabeled counters on the home-page feed remain unconfirmed.)
- **Feed filters/tabs.** Community area with *Recommended*, *3D Printing*, *Game Development*, a *Discord* link, a *Templates* vs *Template Assets* toggle, a search box and a **New Post** button.
- **Creator attribution.** Each card shows a creator handle; the top templates are published by the official account (`Meshy_Official`). Whether regular users can publish templates through *New Post* is **not observed**.
- **Generation controls on the same page.** *Quick Generate* vs *Agent Mode*, upload photo/sketch, *Try a Sample* with five sample characters, a detail level selector ("High Detail") and a model-version selector (a flagship version label). Model names/versions are Meshy's; not reproduced here.
- **Navigation.** Products, Solutions, Community, Resources, Enterprise, Shop (external), Games, Pricing.

Inferred (not observed): templates are probably a saved bundle of *prompt + style conditioning + post-processing options* applied to a user photo. We use that idea below as our own design, not as a description of Meshy's implementation.

---

## 3. Feature map: what we would build

| # | Capability | Meshy-style behaviour (observed) | Social-network version |
|---|---|---|---|
| F1 | Text → 3D | Prompt to model | "Create post" composer with a *3D* attachment type |
| F2 | Image/sketch → 3D | Upload to model | Camera roll / sketch pad → model, shareable as a post |
| F3 | Texturing (PBR) | One-click PBR set | Re-texture any model you own or a licence permits remixing |
| F4 | Rig + animate | Auto-rig, motion presets | Animated avatars, emote packs, stickers |
| F5 | Low-poly / retopo | Poly budget | "Optimise for mobile/AR" toggle |
| F6 | Export / print | FBX/GLB/OBJ/USDZ/STL/3MF, printability | Download, AR quick-look, print hand-off |
| F7 | Community feed | Gallery + counters | The social graph itself: follow, like, remix, comment |
| F8 | API / agents | API + MCP | Public API + bot/agent posting |
| F9 | Templates | Curated one-photo recipes, Use/Save counters, category tabs | User-publishable **recipes** that others apply to their own photo (see §7.1) |

---

## 4. Architecture

```
┌──────────────────────── Browser (Babylon.js) ─────────────────────────┐
│  Feed  │  Composer  │  Model Viewer  │  Texture/Rig Studio │  AR/Print │
│        Shared @forge3d/viewer package (Scene, Camera, Loaders)         │
└───────────────▲──────────────────────────────────────┬─────────────────┘
                │ GLB (CDN, signed URLs)                │ REST + WebSocket/SSE
┌───────────────┴──────────────┐          ┌─────────────▼──────────────────┐
│  Object storage + CDN        │          │  API gateway / social backend  │
│  models, textures, thumbs    │          │  auth, posts, follows, credits │
└───────────────▲──────────────┘          └─────────────┬──────────────────┘
                │                                        │ enqueue job
┌───────────────┴────────────────────────────────────────▼─────────────────┐
│  Job queue ──► Generation workers (GPU)  ──► Post-process workers (CPU)  │
│  (text/image → mesh, texture, rig)         (validate, decimate, GLB pack) │
└───────────────────────────────────────────────────────────────────────────┘
```

Key decisions:
1. **GLB (glTF 2.0 binary) is the single interchange format.** It carries PBR materials, skeletons and animations, and Babylon.js loads it natively. FBX/OBJ/USDZ/STL/3MF are *derived exports*.
2. **Generation runs server-side.** Babylon.js is the *viewer, editor and exporter*, not the generator. The generator is a swappable provider behind an interface (§5).
3. **Async by default.** Generation is a job with a state machine; the UI streams progress.
4. **Posts reference models by ID**, never embed binary in the post row.

---

## 5. Server contract (provider-agnostic)

```ts
type JobKind = 'text_to_3d' | 'image_to_3d' | 'retexture' | 'rig' | 'remesh';
type JobState = 'queued' | 'running' | 'succeeded' | 'failed' | 'canceled';

interface GenerationJob {
  id: string;
  kind: JobKind;
  state: JobState;
  progress: number;            // 0..100, monotonic
  input: { prompt?: string; imageAssetId?: string; modelAssetId?: string;
           polyBudget?: number; pbr?: boolean; hdTexture?: boolean };
  output?: { glbAssetId: string; thumbnailAssetId: string; stats: ModelStats };
  error?: { code: string; message: string };
  costCredits: number;
}

interface ModelStats { triangles: number; materials: number; hasSkeleton: boolean;
                       animations: string[]; boundingBox: [number, number, number]; }
```

Endpoints (illustrative): `POST /v1/jobs`, `GET /v1/jobs/:id`, `GET /v1/jobs/:id/events` (SSE), `POST /v1/jobs/:id/cancel`, `POST /v1/posts` with `{ modelAssetId, caption, licence }`.

The generator behind this is intentionally undefined: self-hosted open models, or a commercial API under its own terms. **Check licence/ToS for any provider before shipping user-generated output.**

---

## 6. Babylon.js implementation

Packages: `@babylonjs/core`, `@babylonjs/loaders` (glTF), `@babylonjs/serializers` (GLB/STL/OBJ export), `@babylonjs/materials` (optional), `@babylonjs/inspector` (dev only), `@babylonjs/gui` (in-canvas controls).

This repo already has `engine-babylon/` (Vite + TypeScript, `editor.html`, `index.html`). The viewer below belongs in a **reusable package** consumed by both the feed and the studio.

### 6.1 Reusable viewer

```ts
import { Engine, Scene, ArcRotateCamera, Vector3, HemisphericLight,
         CubeTexture, SceneLoader, Color4 } from '@babylonjs/core';
import '@babylonjs/loaders/glTF';

export interface ViewerOptions { autoRotate?: boolean; envUrl?: string; }

export class ModelViewer {
  readonly engine: Engine;
  readonly scene: Scene;
  readonly camera: ArcRotateCamera;

  constructor(canvas: HTMLCanvasElement, opts: ViewerOptions = {}) {
    this.engine = new Engine(canvas, true, { preserveDrawingBuffer: true, stencil: true });
    this.scene = new Scene(this.engine);
    this.scene.clearColor = new Color4(0, 0, 0, 0);
    this.camera = new ArcRotateCamera('cam', Math.PI / 2, Math.PI / 2.4, 3, Vector3.Zero(), this.scene);
    this.camera.attachControl(canvas, true);
    this.camera.wheelPrecision = 60;
    this.camera.useAutoRotationBehavior = !!opts.autoRotate;
    new HemisphericLight('fill', new Vector3(0, 1, 0), this.scene).intensity = 0.4;
    if (opts.envUrl) this.scene.environmentTexture = CubeTexture.CreateFromPrefilteredData(opts.envUrl, this.scene);
    this.engine.runRenderLoop(() => this.scene.render());
    new ResizeObserver(() => this.engine.resize()).observe(canvas);
  }

  async load(url: string) {
    this.scene.meshes.slice().forEach(m => m.dispose());          // simple replace
    const result = await SceneLoader.ImportMeshAsync('', '', url, this.scene);
    this.frame(result.meshes);
    return result;
  }

  private frame(meshes: any[]) {
    this.scene.createDefaultCamera(true, true, false);            // fit bounds
    // then copy radius/target onto this.camera, or use meshes' bounding info directly
  }

  dispose() { this.engine.stopRenderLoop(); this.scene.dispose(); this.engine.dispose(); }
}
```

### 6.2 The feed problem: many models, one GPU context

A scrolling feed cannot hold 50 live WebGL canvases (browsers cap contexts; memory explodes).

| Technique | Use |
|---|---|
| **Static thumbnail by default** | Pre-rendered PNG/WebP (generated server-side or via `CreateScreenshotUsingRenderTargetAsync`). |
| **One shared viewer, mounted on demand** | A single `Engine` rendered into an offscreen canvas; mount into the card the user hovers/taps/scrolls into view (IntersectionObserver). |
| **Hard cap of live viewers** | 1–3. Dispose the previous before mounting the next. |
| **Budget by device** | `engine.getCaps()` and `navigator.deviceMemory` choose texture size/triangle cap. |
| **Progressive loading** | Poster → low-LOD GLB (`MSFT_lod` or separate URL) → full GLB. |
| **Compression** | Draco or meshopt geometry, KTX2 (Basis) textures. Babylon supports these via the glTF loader. |

### 6.3 Studio panels (post-generation editing)

| Feature | Babylon.js approach |
|---|---|
| PBR preview/tweak | `PBRMaterial` / `PBRMetallicRoughnessMaterial` slots for albedo, metallic-roughness, normal, emissive, AO. Show a channel viewer by temporarily swapping a debug material or using `NodeMaterial`. |
| Retexture | Server job returns new texture set; client swaps textures on the same material (no mesh reload). |
| Rig preview | Loader exposes `skeletons` and `animationGroups`; render a bone overlay with `SkeletonViewer`. Drive a motion library by retargeting clips (server-side retargeting recommended). |
| Motion picker | `AnimationGroup.play(true)`, scrub with `goToFrame`, blend with `AnimationGroup.weight`. |
| Poly budget preview | `Mesh.simplify` (quadric decimation) for an instant client-side preview; the authoritative remesh stays server-side. |
| Wireframe / stats | `material.wireframe`, `scene.getActiveIndices()`, `SceneInstrumentation` for FPS and draw calls. |
| Transform / pivot | `GizmoManager` for move/rotate/scale, bounding-box gizmo for scale-to-real-world-units. |
| Turntable thumbnail | Render N frames into an offscreen canvas → WebP/MP4 for the feed. |

### 6.4 Export

```ts
import { GLTF2Export, STLExport } from '@babylonjs/serializers';

const glb = await GLTF2Export.GLBAsync(scene, 'model', { shouldExportNode: n => n.metadata?.exportable });
// glb.glTFFiles['model.glb'] is a Blob.

const stl = STLExport.CreateSTL(meshes, /*binary*/ true, 'model', /*isLittleEndian*/ true);
```

| Format | Where produced |
|---|---|
| GLB | Client (`GLTF2Export`) or server (canonical copy) |
| STL / OBJ | Client serialisers OK for previews; **server** for print-grade output |
| 3MF, FBX, USDZ | **Server-side conversion only** (no first-party Babylon exporter). USDZ is needed for iOS AR Quick Look. |
| Print repair/auto-split | Server (manifold repair, boolean ops). Client only *visualises* the result and report. |

### 6.5 Printability panel (visual only on the client)
Server returns `{ watertight, holes, nonManifoldEdges, volumeCm3 }` plus a list of problem edge/vertex indices; the client draws them with `LinesMesh`/`MeshBuilder.CreateLineSystem` over the model.

### 6.6 AR
- Android: WebXR `immersive-ar` via `scene.createDefaultXRExperienceAsync({ uiOptions: { sessionMode: 'immersive-ar' } })`.
- iOS: provide the derived USDZ via an `<a rel="ar">` link (Quick Look).

---

## 7. Social layer on top of the models

| Concept | Design |
|---|---|
| Post | `{ id, authorId, modelAssetId, caption, tags, licence, createdAt, remixOf? }` |
| Remix | Fork a model, run retexture/re-rig as a new job; `remixOf` forms a provenance chain; show "remixed from @user". |
| Licence | Per-post: *All rights reserved*, *CC-BY*, *CC-BY-NC*, *CC0*, *Remix-OK*. Enforced server-side on remix and download. |
| Engagement | Likes, comments (pinned to a **3D point + camera angle**: store `{ position, normal, cameraState }` so clicking a comment flies the camera there). |
| Collections | Boards of models; "collab boards" for teams. |
| Discovery | Tags, follow graph, text search, visual similarity (embeddings of the thumbnail). |
| Creator tools | Series, challenges ("model of the week"), analytics. |
| Credits | Per-job cost; free daily allowance; paid packs; refunds on failed jobs. |

### 7.1 Templates as the social primitive

Recipes make a good viral loop: a post is not just a model, it is a model **plus a reusable way to make more**.

```ts
interface Template {
  id: string; slug: string; authorId: string;
  title: string; description: string;
  category: '3d_printing' | 'game_dev' | 'avatar' | 'other';
  inputs: { kind: 'photo' | 'photo_pair' | 'text'; label: string }[];
  recipe: {                        // consumed by the job pipeline (§5)
    kind: JobKind; stylePrompt: string; polyBudget?: number;
    pbr?: boolean; printReady?: boolean; postProcess?: string[];
  };
  exampleAssetIds: string[];       // before/after previews
  saves: number; uses: number;     // saves = bookmark; uses = jobs started from it
  licence: string; status: 'draft' | 'published' | 'removed';
}
```

- **Use Template** → opens the composer with the recipe locked; the user only adds a photo. This creates a `GenerationJob` with `templateId` for attribution.
- **Save Template** → bookmark to the user's collection; drives the *saves* counter and ranking.
- **Credit the author** on every post made from a template; show "made with *Template* by @author", and pay creators with credits or badges when their template is used.
- **Quality gate:** before a user template is published, run it on a fixed set of safe test photos and require a minimum success/quality score. Templates also go through moderation (§10) since they carry a prompt.
- **Feed tabs** mirror the observed layout: *Recommended*, category tabs, *Templates* vs *Models*, search, and a **New Post** entry.

Babylon.js role: a template card shows a **before photo → after model** pair. The after model uses the thumbnail-first/shared-viewer pattern from §6.2; the template detail page mounts one live `ModelViewer`.

---

## 8. Data model (sketch)

```
user(id, handle, plan, credits_balance)
asset(id, owner_id, kind[glb|texture|thumb|source_image], uri, bytes, sha256, stats_json)
job(id, owner_id, kind, state, progress, input_json, output_asset_id, cost, created_at)
post(id, author_id, asset_id, caption, licence, remix_of, visibility, created_at)
reaction(post_id, user_id, kind) ; comment(id, post_id, user_id, body, anchor_json)
follow(follower_id, followee_id) ; collection(id, owner_id) ; collection_item(...)
credit_ledger(id, user_id, delta, reason, job_id)     -- append-only
template(id, slug, author_id, category, recipe_json, saves, uses, status)
template_save(template_id, user_id) ; job.template_id (nullable FK)
```

---

## 9. Phased delivery

| Phase | Deliverable | Exit criteria |
|---|---|---|
| P0 | `ModelViewer` package, GLB display, thumbnail pipeline | Loads a GLB in a card; 60 fps on a mid-range phone |
| P1 | Feed with shared-viewer pattern and posts | 100 posts scroll with ≤ 2 live WebGL contexts |
| P2 | Job API + text/image-to-3D via one provider | Job reaches `succeeded`; GLB appears in composer preview |
| P3 | Studio: PBR tweak, wireframe/stats, turntable | User edits and re-publishes |
| P4 | Rig + motion presets; animated posts | Skeleton overlay + clip playback |
| P5 | Export (GLB/STL/OBJ), printability panel, USDZ | Print-ready STL passes a slicer import test |
| P6 | Remix, licences, 3D-anchored comments | Provenance chain visible |
| P6b | Templates: publish, Use/Save, author attribution | A template produces a post credited to its author |
| P7 | Credits, public API, bot posting | Ledger reconciles with job costs |

Fit with gdx-unity: reuse `engine-babylon/` for the viewer/editor shell and `engine-web/` for embedding; keep the viewer package free of editor-only dependencies so the social app can ship a small bundle.

---

## 10. Risks and open questions

| Area | Risk / question |
|---|---|
| **Legal / IP** | Generated output provenance, training-data claims, per-provider ToS, user uploads of copyrighted images. Needs a takedown flow and clear licences. |
| **Moderation** | Prompts and images can produce abusive or unsafe 3D content. Text + image classifiers on input, render-based classifiers on output, report/appeal flow. |
| **Cost** | GPU inference dominates. Per-user rate limits, queue priority by plan, spend caps. |
| **Performance** | Mobile GPU memory limits; mandatory compression, LOD and triangle/texture budgets. |
| **Privacy** | Meshy's page loads many ad/analytics trackers. We should **not** replicate that; consent-first analytics only. |
| **Safety of uploads** | Treat uploaded GLB/images as untrusted: size limits, validate with a glTF validator, strip metadata, sandbox converters. |
| **Unknowns** | Meshy's authenticated UI, queue behaviour, credit pricing, community counters' meaning, and viewer library were **not observed**. A logged-in capture (with the account holder's consent and within Meshy's ToS) would be required to learn more. |

---

## 11. Next steps

1. Decide the generator provider strategy (self-host vs API) and record it as a technical decision.
2. Build P0 in `engine-babylon/` and measure feed performance on a real mid-range phone.
3. Prototype the shared-viewer pattern before any other social feature; it is the main technical risk.
4. Define licence enum and moderation policy before enabling remix or public posting.
