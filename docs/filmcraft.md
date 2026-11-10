# VFilm — ArtCraft / FilmCraft Integration Engineering Skill

> Status: Research and architecture skill; integration is **not yet approved or implemented**.
> Updated: 2026-11-06
> Intended users: Windsurf, Codex, Claude Code, and engineering reviewers working on VFilm.

## Purpose

Evaluate and, only after explicit approval, selectively reuse components from the open-source ArtCraft ecosystem—primarily FilmCraft—to accelerate VFilm's nonlinear video editor without replacing VFilm's authoritative game-world recorder, deterministic replay, multiplayer synchronization, or AR/VR capture systems.

## Activation

Apply this skill when a task mentions VFilm, FilmCraft, ArtCraft, video editing, multi-camera editing, replay-to-video, transitions, Rust/Java interoperability, codec/export pipelines, or reuse of third-party editing code. Read this file before making architecture or implementation changes.

## Primary sources

- ArtCraft: https://getartcraft.com
- ArtCraft organization: https://github.com/storytold
- FilmCraft: https://github.com/storytold/filmcraft
- PhotoCraft: https://github.com/storytold/photocraft
- Background article: https://futurism.com/future-society/adobe-creative-suite-software-open-source-artcraft

Treat upstream READMEs and websites as claims, not verified functionality. Pin a specific commit and reproduce builds/tests before asserting that a feature works. Never execute upstream scripts blindly.

## Confirmed public-source baseline (2026-11-06)

- FilmCraft is a public Rust nonlinear video-editor repository with `LICENSE-MIT`, `LICENSE-APACHE`, `NOTICE`, and `ATTRIBUTION.md` files. GitHub identifies MIT and Apache-2.0 licenses. **Per-file and dependency obligations still require review.**
- The README describes timeline editing, multicam, color, effects, audio, captions, codec/export and interchange features.
- The documented Cargo workspace separates foundation crates, editing/interchange, render/GPU/export, engine commands, and UI/CLI frontends. This suggests possible headless reuse, but is not proof of stable embeddable APIs.
- FilmCraft describes itself as alpha and reports roughly 56% readiness for full real-world production work (maintainer estimate, measured 2026-10-10). Do not treat advertised features as production-proven.
- PhotoCraft is also public and Rust-based; it is secondary to VFilm's immediate editing requirements.

## VFilm non-negotiable architecture

VFilm records **game-world state and events**, not merely flattened camera footage. Users must be able to replay a multiplayer or AR/VR session, move virtual cameras freely, create new camera shots after recording, and edit a movie afterward.

Keep these responsibilities separate:

1. **Game engine / network server**: authoritative world simulation, entity identities, animation, physics, time, multiplayer event ordering and permissions.
2. **VFilm recorder**: timestamped state/event capture, asset references, versioned schemas, checkpoints, deterministic or explicitly bounded replay fidelity, clock synchronization and integrity checks.
3. **VFilm replay service**: seek/scrub, reconstruct state, interpolate safely, branch camera views, request rendered frames and audio from recorded world state.
4. **VFilm production editor**: eight camera preview windows, one large master production monitor, shot selection, multi-track timeline, scene editing, transitions, audio, titles, undo/redo and export orchestration.
5. **Editing/render/export adapter**: optional FilmCraft-derived or FilmCraft-compatible Rust components for timeline operations, compositing, effects, media codecs, interchange and final output.

Never flatten the authoritative replay archive into video as its sole representation. Video proxies and rendered clips are **derived media**, regenerable from the recorded world state.

## Target user interface

- Eight independently positioned virtual-camera preview windows (Camera 1–8).
- One prominent master/program monitor showing the active edit and output composition.
- Camera controls: free camera, tracking, focal length, lens/FOV, transforms, keyframes, cuts, transitions, per-camera overlays and preview quality.
- Time-synchronized previews: all camera windows reflect the same replay timestamp unless explicitly switched into a documented independent-scrub mode.
- A production timeline with video/shot, world-event, camera-cut, audio, titles, effects and annotation tracks.
- Multicam cut selection should create non-destructive edit decisions referencing recorded session/time ranges and camera parameters.
- Support frame-accurate edits, rational frame rates and an explicit conversion between world simulation ticks, server timestamps, audio samples and timeline timebase.
- Support live preview proxies with background high-quality rendering; avoid requiring eight full-resolution simultaneous renders on modest hardware.
- Keep camera preview, master output and export results consistent via shared render settings and reproducible frame identifiers.

## Research and reuse assessment workflow

### Phase A — License and provenance audit (mandatory before reuse)

1. Clone FilmCraft into a separate read-only research workspace, **not inside the VFilm source tree**.
2. Record upstream URL, branch, exact commit SHA, release tag, audit date and license-file hashes.
3. Inspect `LICENSE-MIT`, `LICENSE-APACHE`, `NOTICE`, `ATTRIBUTION.md`, Cargo manifests, third-party code, assets, codecs, fonts and test media.
4. Map crate-level licenses, SPDX identifiers, bundled assets and transitive dependencies; check patent and codec-distribution implications separately.
5. Document notice, attribution, source redistribution and modification-tracking obligations. Apache-2.0/MIT are generally permissive, but **do not infer that every file or bundled asset is freely reusable**.
6. Escalate ambiguous licensing to qualified legal review. Do not copy code until the review is approved.

Deliver: `docs/vfilm/filmcraft-license-audit.md` with a go/no-go decision for each candidate crate.

### Phase B — Capability and quality audit

Audit actual implementation, tests and public APIs for:

- Timeline model, track types, clip edits, trimming, ripple/roll/slip/slide, markers, snapping, undo/redo.
- Multicam editing and switching, timecode and frame-rate handling.
- Transition and effect representation, keyframes, compositing and color.
- Audio mixing, sync, fades, resampling and output.
- Media decoding/encoding, image sequences, render queues, export presets and metadata.
- Project serialization, interchange formats (e.g. OTIO if supported), crash recovery and migration.
- Headless/CLI operation, GPU/CPU fallback, threading, cancellation and error reporting.
- Windows/Linux/macOS build reproducibility, test coverage, performance, memory behavior and known limitations.

For each capability record **Implemented / Partial / Missing / Unverified**, source path, API, test evidence, license and integration cost. Do not mistake README claims for test evidence.

Deliver: `docs/vfilm/filmcraft-capability-matrix.md`.

### Phase C — Integration spike (isolated proof of concept)

Compare three options:

**Option 1 — Headless Rust sidecar (preferred initial spike)**
- Run a separately versioned Rust editing/render/export process.
- Communicate over a versioned local IPC protocol (e.g. gRPC, named pipes or local sockets, selected after benchmarking).
- Transfer edit decision lists, asset references, timeline timecodes, frame requests and output metadata; avoid moving raw 4K/8K frames over JSON.
- Support shared memory or bounded frame buffers where justified by measurements.
- Crash isolation, cancellation, backpressure, progress events, health checks and safe restarts.

**Option 2 — Rust native library via JNI / Panama FFM**
- Use a narrow, stable C ABI with opaque handles, explicit allocation/free, ABI versioning and well-defined thread ownership.
- Never pass Rust-native data layouts or panics across the boundary.
- Compare packaging and crash risks against sidecar architecture.

**Option 3 — Port or reimplement selected algorithms**
- Reuse only well-understood, independently testable algorithms when IPC/FFI is unsuitable and license review permits.
- Preserve upstream attribution and document maintenance burden.

Do not assume FilmCraft offers stable public crates or a production-ready library API. Verify exports and boundaries in the pinned revision.

Deliver: `docs/vfilm/filmcraft-integration-options.md`, measured prototype and recommendation.

## Data contract: non-destructive world-state editing

Define a versioned edit-decision format containing:

- Session ID, recording schema version, replay snapshot/checkpoint references and content hashes.
- World-time range, simulation tick/timebase, synchronization mapping and sequence frame rate.
- Virtual-camera ID, transform and lens parameters, keyframe curves, interpolation rules and camera-cut decisions.
- Shot/clip IDs, track IDs, transitions, speed changes, nested sequences and effects.
- Audio tracks, event-derived sound and voice assets, synchronization offsets, fades and mix settings.
- Render resolution, aspect ratio, color space, codec settings, output destination and provenance.

Keep the original world recording immutable. Editing commands create reversible decisions and project versions. Changes to the multiplayer world or replay format must not silently invalidate old VFilm projects.

## Proposed high-level flow

```text
Multiplayer Game / AR-VR Engine / Network Server
                     |
            World-State Recorder
                     |
       Versioned Session + Checkpoints
                     |
              Replay Service
                     |
       Virtual Camera Render Service
          /                       \
  Eight Camera Previews      Master Monitor
          \                       /
             VFilm Edit Timeline
                     |
        FilmCraft Adapter (optional)
                     |
       Effects / Mix / Composite / Export
                     |
            Final Movie + Metadata
```

## Candidate reuse priority

| Candidate | Initial assessment | Required proof |
|---|---|---|
| Timeline editing data model / commands | High-value candidate | Headless APIs, stable timebase, undo correctness |
| Transitions and effects | High-value candidate | GPU/CPU parity, deterministic outputs, license |
| Audio mixing and synchronization | High-value candidate | Long-session drift tests and multichannel support |
| Export / codecs / containers | High-value candidate | Actual format support, licensing, hardware fallback |
| Multicam UI behavior | Useful reference, not drop-in | Eight-preview + world replay integration |
| FilmCraft desktop UI | Lower-priority direct reuse | UI framework compatibility and docking feasibility |
| PhotoCraft image tools | Optional later phase | Independent need and licensing review |
| VFilm world-state recorder/replay | **Must remain VFilm-owned** | No replacement by raster video editor |

These are hypotheses to validate, not implementation-complete claims.

## Quality and security gates

- Unit tests for timebase conversion, edit decisions, undo/redo, camera cuts and serialization.
- Integration tests: 8-camera seek/scrub, master-monitor consistency, world-event alignment, audio sync and export.
- Golden-frame tests for representative transitions, color and camera outputs.
- Long-session replay tests; variable tick rate, packet loss/reordering, disconnected users, missing assets and version migration.
- Performance profiling: 8 previews + master monitor, GPU VRAM budget, proxy generation, export throughput, backpressure and cancellation.
- Fuzz/negative tests for corrupt recordings, hostile media files, path traversal, decompression bombs and malformed edit projects.
- Sandboxed processing of untrusted assets; no secrets in logs or render workers; permissions on multiplayer recordings and voice data.
- Reproducible builds and pinned dependencies; provenance/SBOM and license scanning.
- Never report PASS without command, environment, exit status and artifact evidence.

## Working rules for AI coding agents

1. Inspect the **actual VFilm repository** first; do not assume its language, modules, network protocol or recording schema. Java/LibGDX compatibility is a candidate based on the broader engine context, not a verified VFilm fact.
2. Keep VFilm and unrelated projects separate; do not import code from other local workspaces.
3. Start with an **audit and architecture recommendation**, not a bulk port or a rewrite.
4. Preserve recorder and replay semantics; never substitute video capture for world-state recording.
5. Favor small adapters and versioned interfaces over invasive vendor forks.
6. Make build/run/test steps platform-specific and reproducible.
7. Do not fetch, clone, install, execute or modify third-party code without recording provenance and checking trust boundaries.
8. Do not copy upstream source until license and dependency review is complete.
9. Ask approval before major dependency introduction, license-sensitive reuse, commits, pushes, or production deployment.
10. Mark each finding as **Verified**, **Inferred**, **Proposed**, or **Blocked**.

## Required first assignment

Produce a **read-only feasibility assessment** before implementing integration:

1. Inventory current VFilm architecture and contracts.
2. Pin and audit FilmCraft repository revision, licenses and dependencies.
3. Map FilmCraft crates and commands to VFilm timeline, transitions, audio, compositing and export needs.
4. Identify what can be reused unchanged, adapted, wrapped, or must be built in VFilm.
5. Compare Rust sidecar, native FFI and selective porting with evidence and risks.
6. Design an eight-preview-plus-master architecture and non-destructive world-state timeline contract.
7. Propose a small end-to-end proof of concept: recorded session -> replay -> two virtual cameras -> cut timeline -> export one movie. Then scale to eight previews.
8. Provide a phased roadmap, acceptance criteria, test plan, license obligations and explicit go/no-go gates.

Write findings to:

- `docs/vfilm/filmcraft-feasibility.md`
- `docs/vfilm/filmcraft-license-audit.md`
- `docs/vfilm/filmcraft-capability-matrix.md`
- `docs/vfilm/filmcraft-integration-options.md`
- `docs/vfilm/vfilm-editor-architecture.md`

**Stop after the read-only audit and request implementation authorization.** No commit or push unless explicitly authorized.
