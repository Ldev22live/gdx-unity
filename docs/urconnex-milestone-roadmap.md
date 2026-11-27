# UrConnex — Creative Suite & UCX Local AI Master Roadmap

**Status:** Proposed, 26 November 2026. Preserve existing creative M-series and UCX AI milestone records; UCX-M is a coordinating roadmap, not a replacement. Repository and reported tests have not been independently verified.

## Goal
Enable manual, text-driven and voice-driven creative production using editable projects and local AI. First finish Animation Studio and Motion FX, then deliver photograph + song to video without requiring generative video models.

## Baseline and gates
M1c/M1c.1 deployed to test (eb4ef74); reported 624 unit and 87 browser tests. Real authenticated owner walkthrough, Firefox/WebKit/mobile remain pending. M2a/M2f proposed. Existing TrueForge and Ollama deployment is a foundation, not production-ready proof. Verify current repositories, service status, security and encrypted backup/restore before changes. urconnex_dev is live. Do not apply migrations, deploy, push or install large models without explicit approval.

## Architecture
Next.js UI → Spring Boot identity/tenant/entitlement/credit gateway → UCX Local AI (TrueForge + Ollama) → typed tool registry → existing editor commands and isolated processing workers → editable draft → preview → approval → render → Media library. Keep model inference and worker endpoints private. PostgreSQL authoritative; no direct model database access. Use existing job/outbox infrastructure where possible. Logical specialists share a model, not one model per specialist. Independent future PyTorch LLM remains a separate shared service for UrConnex and ForgeRoom.

## Milestones
All milestones below are proposed unless explicitly marked otherwise. Each requires documented dependencies, scoped tests, security review, build, acceptance evidence, rollback plan and approval before deployment.

| ID | Deliverable | Acceptance gate |
|---|---|---|
| UCX-M00 | Baseline reconciliation | Verify branches, deployments, existing AI milestones and real owner walkthrough; record blockers. |
| UCX-M01 | M2a/M2f navigation, entitlements, diagnostics | Server-side role/tenant/feature tests; existing editors unaffected; sanitized Bugs events. |
| UCX-M02 | M2b Media library | Authorized image/audio/video IDs, range requests, revoked asset tests. |
| UCX-M03 | M2c–M2e persistence and handoff | Private projects, revision conflict 409, atomic references, migration/relink, soft delete; encrypted backup and isolated restore before migration. |
| UCX-M04 | Animation Studio completion | Vector paths, grouping, symbols, nested compositions, easing, SVG and text animation; editable saved demo. |
| UCX-M05 | Motion FX completion | Animated effects/masks, source offsets, transitions, speed and tracking foundations; pixel/time tests. |
| UCX-M06 | Audio and rendering | Audio tracks/mixing, deterministic frame stepping, FFmpeg job, cancellation; AV sync and ffprobe validation. |
| UCX-M07 | Creative Suite alpha | Manual photo+song video saved, reopened and exported; real authenticated browser acceptance. |
| UCX-M08 | Local AI hardening | Verify TrueForge/Ollama, gateway, credentials, private endpoints, backups and resource bounds. |
| UCX-M09 | Knowledge base | Approved docs retrieval, citations, permission filtering, versioned index. |
| UCX-M10 | Tool registry | Typed schemas, allowlists, auth, budgets, revision checks, idempotency and audit. |
| UCX-M11 | Read-only assistant | Authorized inspection and guidance; no writes or cross-tenant access. |
| UCX-M12 | Controlled editing | Validated draft batches, preview, approval, undo/rollback; reject injection and stale revisions. |
| UCX-M13 | Creative specialists | Director, video, animation, motion, audio, image and reviewer roles share local inference. |
| UCX-M14 | Photo + song to video | Local-only end-to-end editable 1080p project and approved export using synthetic/licensed assets. |
| UCX-M15 | Beat synchronization | Detect beats/sections, align cuts and effects, handle uncertain tempo. |
| UCX-M16 | Text-to-animation | Editable shapes, scenes, text and motion paths from instructions. |
| UCX-M17 | Text-to-motion graphics | Editable intros, logos, adverts, kinetic typography and templates. |
| UCX-M18 | Existing tools | Controlled cross-editor image/video/audio workflows, no source overwrite. |
| UCX-M19 | Local vision | Authorized image understanding and colour/layout suggestions; benchmark model. |
| UCX-M20 | Voice editing | Local transcription, command confirmation and cancellation; no ambient recording by default. |
| UCX-M21 | AI Creative Suite beta | Authenticated end-to-end workflows, quota, progress, privacy and compatibility gates. |
| UCX-M22 | Generative images | Licensed model evaluation, isolated worker, editable outputs. |
| UCX-M23 | Generative video | Benchmark licensed models on available GPU; bounded jobs and no unsupported claims. |
| UCX-M24 | Segmentation/tracking | Editable masks and tracking with failure diagnostics. |
| UCX-M25 | Audio production | Separation, cleanup, transcription and mastering; consent for voice usage. |
| UCX-M26 | 3D generation | Authorized image-to-GLB pipeline, validation and optimization. |
| UCX-M27 | Rigging/motion capture | Skeleton, retargeting and animation validation. |
| UCX-M28 | AR Worlds | Typed placement, transform and save; real device tests. |
| UCX-M29 | URFilm | Authorized project/asset interchange. |
| UCX-M30 | ForgeRoom boundary | Separate product, versioned asset/scene interchange only. |
| UCX-M31 | DataForge | Licensed datasets, provenance, quarantine and retention. |
| UCX-M32 | Evaluation datasets | Held-out creative tasks, reproducible tool-call and render tests. |
| UCX-M33 | Feedback | Explicit opt-in, deletion and redaction; no private content by default. |
| UCX-M34 | Fine-tuning | PyTorch/PEFT experiments off production; baseline comparison. |
| UCX-M35 | Creative model evaluation | Quality, security, latency and cost regression gates. |
| UCX-M36 | Scale | Separate inference/render workers, multi-GPU only when measured demand warrants. |
| UCX-M37 | Credits | Actual compute metering, estimates, reservations/refunds and quotas. |
| UCX-M38 | Production readiness | Disaster recovery, load, observability, security and rollback. |
| UCX-M39 | Independent LLM | Versioned service adapter; Ollama fallback; no product coupling. |
| UCX-M40 | Bounded autonomous workflows | Explicit scoped approval, resumable jobs, audit, cancellation and no unrestricted actions. |

## Open-source candidates
Reuse installed TrueForge (verify exact upstream/version/license), Ollama and Qwen3 14B baseline. FFmpeg/ffprobe for rendering; librosa for beats; OpenCV/Pillow for image analysis; FastAPI/Pydantic for internal Python adapters; pgvector for retrieval if justified; MCP for typed interfaces; whisper.cpp and Piper for voice; OpenTimelineIO for interchange; Inkscape, Blender, glTF-Transform for graphics; ComfyUI/Diffusers, SAM 2, MediaPipe, Demucs or maintained alternative, UniRig and TRELLIS for later evaluation. LangGraph optional only if existing harness lacks resumable orchestration. Verify code, weights, voices, datasets and output rights separately; no blanket commercial license assumption. Ollama tool docs: https://github.com/ollama/ollama/blob/main/docs/capabilities/tool-calling.mdx

## First AI workflow
Inspect authorized photo and song → determine duration/aspect ratio → analyse beats → propose plan → create draft with image transforms, keyframes, title, effects and audio → validate → low-resolution preview → user refinements → approved revision → render → ffprobe verify dimensions, streams, duration and sync → save to Media library. Preserve originals. No export/publish outside authorized scope. Use synthetic fixtures and test cancellation, tenant denial, invalid media, stale revisions, retries and insufficient quota.

## Execution and GPU policy
Current reported RTX 4000 Ada 20 GB, 8 vCPU, 32 GB RAM: start with one heavy GPU job at a time; benchmark VRAM including KV cache; unload models when needed; use CPU for audio analysis and ordinary jobs where feasible. Keep existing site responsive. Bound queue, storage, runtime, retries and tool calls. No arbitrary code, shell, SQL, unrestricted URLs or raw filesystem access from model. Independently authorize every operation; validate model outputs; treat uploaded content as untrusted. Local mode must not silently call hosted providers. External review only with explicit policy/consent. No private chat/media training, hidden reasoning capture or unauthorized commercial model distillation. Keep licenses and evaluation results in a registry.

## Immediate next action
Finish M2a/M2f, authenticated acceptance, M2b–M2e, then manual animation/motion/audio/export acceptance. AI infrastructure audit can proceed in parallel; write tools remain disabled until corresponding editor contracts pass. Stop at each approval checkpoint. No date or capacity guarantees; estimate after baseline measurement.
