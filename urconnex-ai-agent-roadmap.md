# UrConnex AI Agent Roadmap
## From a custom PyTorch LLM to a platform-wide assistant and ForgeRoom operator

**Date:** 17 November 2026  
**UrConnex baseline:** Alpha, as confirmed by the owner.  
**Document status:** Proposed development roadmap; creates no implementation or deployment approval.  
**Planning horizon:** First 12 months establish foundations; broad tool mastery is a multi-stage, potentially 18–30+ month programme. Dates depend on engineering capacity, hardware, tool readiness and evaluation results.

## 1. Product goal

Build a custom UrConnex agent that understands the platform, learns from verified experience, collaborates with other AI systems, and eventually operates every supported UrConnex tool available to the requesting user, including ForgeRoom.

A user should ultimately be able to describe an outcome, inspect a proposed plan, let the agent perform authorized actions, and review evidence that the result works. For creative work, results must remain editable in ForgeRoom.

Examples of the intended destination:

- “Explain the tools available to me and help me complete this workflow.”
- “Create a small editable 3D adventure scene with terrain, a house, a controllable character and music; test it and prepare an export.”
- “Use an enabled 3D provider to create a prop, inspect it, import it into my project and place it in the scene.”
- “Study this approved solution, extract a reusable workflow, and demonstrate it on a different task.”

These are targets, not claims about current alpha functionality. “All tools” means complete, tested coverage of the actual platform inventory, subject to user permissions and supported environments. It does not mean unrestricted access or guaranteed mastery of arbitrary future tools.

## 2. Baseline, boundaries and assumptions

| Area | Planning baseline | Consequence |
|---|---|---|
| UrConnex | Application is in alpha | Protect existing user journeys; release AI incrementally behind feature flags |
| Web application | Next.js 16 | Extend the existing application; confirm installed versions before implementation |
| Application API | Spring Boot 4.1.1 / Java 21 | Keep application authorization and business rules in the existing service |
| Application data | PostgreSQL | Use existing infrastructure where appropriate; introduce pgvector only when justified |
| AI service | Proposed separate Python/PyTorch service | Train, evaluate and deploy independently of the application |
| ForgeRoom | Separate engine/editor product with optional UrConnex integration | Connect through an explicit versioned protocol; preserve independent desktop operation |
| ForgeRoom readiness | 14 November roadmap is a proposal, not evidence of completed phases | Re-audit implementation and acceptance evidence before enabling capabilities |
| Hardware and staffing | Not established for this plan | Benchmark available machines before choosing model size or promising delivery dates |
| Pulse for Processing | Separate project | No Pulse code, data, tooling or database assumptions enter this roadmap |

ForgeRoom's latest roadmap records an existing assistant panel, provider interfaces, a structured plan engine and six basic tools. Those are documented foundations, not independently verified live-model capability. Reuse and audit them before designing replacements.

**Preserve the ForgeRoom sequencing decision:** R10, the autonomous LLM/game-building rollout, follows the acceptance gates for R0–R9 and their additions, unless the owner explicitly changes that roadmap. Earlier engine phases can supply deterministic commands, agent-tool contracts and tests. UrConnex AI research, alpha assistance and provider experiments can progress separately without declaring ForgeRoom R10 complete or bypassing its gates.

This document proposes integration; it does not merge UrConnex, ForgeRoom and Weskaap repositories. Weskaap can serve as a later acceptance project without becoming an application dependency.

## 3. Develop the model and the product together

### Track A — Custom PyTorch model

Start by implementing and training a small decoder-only Transformer. Use it to learn training mechanics and investigate narrow tasks such as intent classification, tool selection and structured plan generation.

An initial 10–30 million parameter model is an educational starting point, subject to hardware measurements. Its text generation or falling validation loss does not establish reliable conversation, planning, coding or 3D understanding.

### Track B — Useful alpha agent

Build a provider-neutral agent around evaluated models, tools and verifiers. A capable appropriately licensed open-weight or hosted model can provide early product functionality while the custom model develops.

Promote the custom model into specific production roles only when it meets the same benchmarks. Do not make UrConnex alpha stability depend on the research model reaching frontier-level capability.

### Track C — ForgeRoom tool readiness

As ForgeRoom features mature, expose the same validated editor operations to the agent. The agent cannot reliably perform an editor operation that does not exist or has not passed acceptance.

These tracks share schemas and evaluation tasks, while retaining separate release gates.

## 4. Proposed architecture

~~~mermaid
flowchart TD
    UI["UrConnex interface"] --> API["Spring Boot authorization"]
    API --> H["Python agent harness"]
    H --> G["Model and mentor gateway"]
    G --> L["Custom PyTorch or open-weight model"]
    G --> M["Enabled external mentors"]
    H --> T["Validated tool gateway"]
    T --> U["UrConnex application tools"]
    T --> F["ForgeRoom command bridge"]
    T --> P["Tripo3D and Meshy adapters"]
    H --> E["Evaluation and skill memory"]
~~~

| Component | Responsibility |
|---|---|
| Next.js interface | Conversation, plan review, progress, cancellation, previews and result inspection |
| Spring Boot API | Authenticate users; enforce object/project/tenant permissions; validate application mutations |
| Python agent harness | Bounded planning, tool selection, execution state, checkpoints, recovery and evidence collection |
| Model gateway | Route tasks among custom, open-weight and optional hosted models based on measured suitability |
| Mentor adapters | Request scoped assistance or review through supported provider interfaces |
| Tool gateway | Validate typed inputs, check authorization, enforce limits, execute and report structured outcomes |
| ForgeRoom bridge | Dispatch commands to the owning editor/runtime context and return results, diagnostics and undo references |
| Experience store | Hold scoped task evidence, approved lessons, skill versions and rights metadata |
| Training pipeline | Build authorized datasets, train candidates and evaluate them outside live user sessions |

Begin with an explicit state machine rather than requiring a large orchestration framework. Evaluate frameworks when checkpointing or workflow complexity justifies one; no framework should own the domain contracts.

Prefer APIs or an agreed tool protocol over UI automation. MCP is an optional interoperability adapter, not a substitute for authorization or the application's own tool contracts.

## 5. What “learn from other agents” means

| Learning mechanism | What changes | Initial approach |
|---|---|---|
| Assisted execution | Another system helps solve the current task | Optional mentor calls with scoped context |
| Retrieval and memory | Relevant evidence becomes available on later tasks | Approved, project-scoped lessons |
| Reusable skills | The agent gains a tested procedure with preconditions and checks | Versioned workflow library |
| Supervised fine-tuning | Model weights change using authorized examples | Offline experiments after a baseline exists |
| Preference optimization or reinforcement learning | A trained policy changes according to defined feedback | Later research, after trustworthy environments and rewards exist |

Reading another agent's answer does not automatically update model weights. A stronger agent's agreement is not proof that a result is correct. Saving a summary does not remove restrictions attached to its source material.

### Mentor and creative-provider roles

| System | Intended contribution | Integration boundary |
|---|---|---|
| ChatGPT / OpenAI models / Codex | Explain workflows, propose plans, review code and suggest evaluations | Use approved APIs or supported agent interfaces; this conversation is not a background API connection |
| Claude Code / Claude | Scoped repository investigation, coding assistance and solution review | Use supported SDK/CLI/API interfaces in isolated development workspaces |
| Tripo3D | Generate 3D assets from supported inputs | Treat as an external asset-production tool with asynchronous jobs |
| Meshy | Generate or process supported 3D assets | Treat as a separate provider with its own capabilities, formats, costs and permissions |
| Local/open-weight systems | Offline execution, experiments and specialized skills | Check model and dataset licenses; benchmark locally |

Tripo3D and Meshy are creative services in this design, rather than language-model teachers. The agent can improve its orchestration of them and its asset checks; that does not teach a text-only PyTorch model to generate meshes independently.

**Rights gate:** keep the right to use a service, the right to use its output in a product, the right to retain it in memory, and the right to train on it as separate permissions. Anthropic, OpenAI, Tripo and Meshy have relevant restrictions; account-specific agreements and exceptions can differ. Do not automatically collect provider transcripts, generated meshes, derived lessons or preference labels into a training dataset. Independently verify both the result and the permitted reuse. See sources [S3]–[S6].

If a teacher dataset is unavailable, train on project-owned demonstrations, suitably licensed material, and independently authored tasks with verified rights. The agent must remain useful when any external provider is disabled.

## 6. Verified learning loop

1. **Scope:** record the goal, available tools, user permissions, budget and measurable completion checks.
2. **Attempt:** the local agent tries within a bounded step, time and resource budget.
3. **Escalate:** if validators fail or the task exceeds demonstrated ability, request narrowly scoped mentor assistance.
4. **Review:** treat the suggestion as a candidate plan or patch.
5. **Execute and verify:** use the actual application tools and independent tests; include visual or listening review where needed.
6. **Capture evidence:** store the task outcome, checks, relevant revisions, resource use and failure category.
7. **Admit a lesson:** retain only material permitted for that purpose, with provenance, scope and verification status.
8. **Generalize:** require the proposed skill to work on a different authorized task before promoting it.
9. **Train selectively:** use only examples explicitly admitted for model training.
10. **Release a candidate:** compare against the previous model and roll back if the relevant quality gates regress.

Failures remain useful evidence. They must not be silently converted into successful demonstrations or universal rules. Memory entries expire or require revalidation when tools, schemas or engine behavior change.

A successful automated check is not sufficient evidence for aesthetic quality, enjoyable gameplay or a correct musical result.

## 7. Milestones and acceptance gates

Effort bands below are planning allowances for focused engineering, not delivery commitments. They overlap across the three tracks and depend on the current repositories.

### A0 — Alpha baseline and task inventory
**Allowance:** 1–2 weeks. **Dependency:** none.

- Record current application health, alpha user journeys and existing regression checks.
- Inventory real UrConnex tools, existing AI integrations and ForgeRoom command interfaces.
- Classify every capability as existing, planned, unsupported or awaiting verification.
- Record hardware, privacy settings, external-provider permissions and monthly spending ceilings.
- Define an initial set of 30–50 representative tasks, separating development examples from held-out evaluation tasks.
- Add AI feature flags and a route that leaves normal application behavior available if AI is unavailable.

**Done when:** the inventory and reproducible baseline exist; an AI failure cannot prevent normal alpha use.

### A1 — PyTorch LLM foundation
**Allowance:** 4–8 weeks for the first educational prototype. **Dependency:** A0 hardware and dataset checks.

- Implement tokenization, token/position embeddings, causal attention, feed-forward blocks, normalization and output logits.
- Start with a tiny smoke-test configuration; scale to the 10–30M range only after measuring memory and throughput.
- Implement next-token training, padding/label handling, validation, checkpoints, resume and configurable generation.
- Record dataset provenance, deduplication, tokenizer version, model config and train/validation/test splits.
- Test causal masking, tensor shapes, finite gradients, checkpoint recovery and overfitting a tiny controlled sample.
- Measure validation loss, tokens per second and memory use; retain a reproducible run report.
- Keep private alpha conversations out of the initial corpus.

**Done when:** training and generation work reproducibly within hardware limits; a checkpoint resumes correctly and beats a documented simple baseline on held-out text. The model remains experimental.

### A2 — Model gateway and read-only alpha assistant
**Allowance:** 3–5 weeks. **Dependencies:** A0; A1 can continue alongside.

- Add a Python inference service with health, model metadata, bounded generation and structured errors.
- Define one internal provider contract for generation, structured outputs and capability reporting.
- Connect to the existing Spring Boot API and Next.js interface behind an alpha feature flag.
- Begin with approved documentation lookup, navigation help and explanations of existing tools.
- Enforce per-user access, request limits, cancellation and provider outage handling.
- Label experimental model responses and separate retrieved evidence from generated suggestions.

**Done when:** an opted-in alpha user can complete defined read-only tasks; access tests pass and disabling the AI feature restores the ordinary application path.

### A3 — Typed tools and supervised actions
**Allowance:** 4–6 weeks. **Dependency:** A2.

- Implement a versioned registry populated from actual tools.
- Add a small set of reversible application actions chosen from the inventory.
- Require preview and explicit execution approval for mutations in the initial release.
- Bind approval to the concrete plan, arguments and resource revisions; changed plans require renewed approval.
- Persist job state, action outcomes and cancellation status.
- Use server-side permission checks at execution time, including after an approval delay.
- Test duplicate requests, stale plans, denial, timeouts and partial failure.

**Done when:** representative workflows complete through real tools; prohibited actions are blocked even when directly requested by the model; interrupted work can be inspected and safely resumed or abandoned.

### A4 — External mentors and creative-service adapters
**Allowance:** 4–6 weeks. **Dependencies:** A2–A3 and provider-use clearance.

- Add OpenAI and Claude mentor adapters independently, with capability detection and spending caps.
- Keep Claude Code development sessions separate from consumer-facing execution.
- Give development agents isolated repository copies, scoped files and bounded commands; review patches before application.
- Integrate one of Tripo3D or Meshy first; add the second after the common asset-job contract is proven.
- Implement submit/status/result handling, bounded retries, provenance and reliable result retention.
- Do not assume generation requests are idempotent: reconcile ambiguous submissions before retrying a potentially billable job.
- Record provider and model versions without hard-coding a permanent model name.

**Done when:** scoped mentor assistance and an asset-production job complete with evidence; denied data transfers, provider outages and budget exhaustion are handled predictably. ForgeRoom import is only claimed where that path is ready.

### A5 — Experience memory and reusable skills
**Allowance:** 4–8 weeks. **Dependency:** A3; A4 supplies optional permitted examples.

- Separate conversation history, project knowledge, task evidence and reusable skills.
- Start with PostgreSQL records and metadata filtering; add vector retrieval only when it improves measured retrieval quality.
- Store skill preconditions, tool/schema versions, steps, expected outputs and validators.
- Enforce user/project/tenant scope in storage and retrieval.
- Add rights, retention, deletion and training-admission fields.
- Evaluate memory against the same tasks with memory disabled.
- Retire conflicting, stale or repeatedly unsuccessful skills.

**Done when:** approved memory improves held-out task performance without crossing access boundaries; skills can be replayed and revalidated on different projects.

### A6 — ForgeRoom bridge and tool parity
**Allowance:** 6–12 weeks for a first slice; full coverage follows engine readiness.  
**Dependencies:** A3 and corresponding ForgeRoom tool acceptance.

- Audit and extend the existing ForgeRoom provider, plan and tool contracts.
- Use the editor's validated commands, serializers, ownership rules and undo system.
- Dispatch operations on the correct engine/editor thread.
- Connect a hosted agent to a desktop session only through explicit authenticated pairing; preserve offline desktop use.
- Define web/desktop support and protocol compatibility per tool.
- First validate inspect → plan → preview → approved edit → save → reload → undo on a disposable project.
- Require project/revision checks so an agent cannot apply a stale plan to a different open scene.

**Done when:** the command bridge passes conformance and undo tests in supported environments. Tool-contract work can precede R10; user-facing autonomous game building remains behind the ForgeRoom R10 gate.

### A7 — Asset and game-authoring workflows
**Allowance:** 8–16 weeks for representative vertical slices.  
**Dependencies:** A4, A6 and ForgeRoom R10 prerequisites for autonomous authoring.

- Build asset specification → generation/import → validation → derived asset → placement workflows.
- Validate supported formats, dimensions, scale, orientation, materials, textures, polycount and project budgets.
- Add rig/animation checks where supported; inspect representative assets visually.
- Preserve source/derived relationships and provider provenance.
- Build a small 2D game slice, then a 2.5D slice, then a 3D adventure/action-RPG slice using supported templates.
- Include logic, animation, audio, Play isolation, save/reload and export checks.
- Keep every generated component accessible through normal editors.

**Done when:** representative projects are playable, exportable and editable, with recorded human review and repeatable automated evidence. Export success alone does not prove a complete or enjoyable game.

### A8 — Training beyond the initial model
**Allowance:** ongoing experiments after A1 and a trustworthy A5 dataset exist.

- Fine-tune the custom model on authorized narrow tasks before expanding scope.
- Compare full fine-tuning for the small model with LoRA/QLoRA where appropriate for larger open-weight models.
- Train tool selection and structured calls against versioned schemas.
- Separate perception work from language work; evaluate a licensed vision model if screenshots must be interpreted.
- Use preference optimization or RL only after the environment, rewards and held-out checks resist shortcut exploitation.
- Test whether improvements persist without mentor calls.
- Keep model, tokenizer, dataset, skill and tool versions together in each release record.

**Done when:** a candidate measurably improves its assigned role without unacceptable regressions. Increased parameter count or training time is not a release criterion.

### A9 — Broad platform coverage and controlled autonomy
**Allowance:** 3–6+ months of expansion, then continuous maintenance.  
**Dependencies:** proven earlier slices and the relevant engine/product features.

- Close gaps in the UrConnex and ForgeRoom tool matrices by domain.
- Offer bounded execution profiles: explain, preview, supervised execution and explicitly delegated low-risk work.
- Keep consequential operations subject to their own policy and approval requirements.
- Add long-running jobs, notifications, conflict recovery and cost reporting where the application supports them.
- Run regression suites when tools, models, providers or skills change.
- Release to opted-in cohorts; use observed failures to guide expansion.

**Done when:** every advertised tool has an adapter, permission policy, verifier and user acceptance evidence. Publish remaining gaps rather than implying universal mastery.

## 8. Complete tool coverage

### UrConnex coverage

A0 must discover the actual feature set. Candidate categories include account/profile operations, search, collaboration, communications, content/assets, project management and creative tools, but their presence is not assumed.

For each actual command record:

| Field | Required evidence |
|---|---|
| Identity | Stable tool ID, schema version, owning service |
| Availability | Role, object/project/tenant scope, web/desktop support |
| Inputs and outputs | Typed schemas, validation rules, machine-readable outcome |
| Side effects | Read/write classification, external effects, resource and monetary cost |
| Execution | Timeout, retry policy, idempotency or reconciliation strategy |
| Recovery | Undo, rollback or compensation capabilities and limitations |
| Verification | Postconditions, test fixture and observed acceptance result |
| Release | Feature flag, supported model/skill versions, known gaps |

Report implemented coverage and verified coverage separately. Use the A0 inventory as the denominator; do not remove difficult tools to inflate the percentage. Track newly added tools and deliberately policy-restricted operations visibly.

### ForgeRoom coverage

| Domain | Target agent operations | Verification |
|---|---|---|
| Projects and scenes | Create/open, inspect, save, duplicate, reload | Round-trip integrity and project ownership |
| Entities and prefabs | Create, select, transform, configure, instantiate, overrides | Scene diff and undo/redo |
| Assets | Find, import, derive, organize, resolve references | Provenance, dependencies and reopening |
| Materials | Edit supported parameters and graphs, assign slots | Validation and rendered preview |
| Terrain and world | Sculpt, paint, scatter, place spawn points | Editable data, collision and performance checks |
| 2D/2.5D | Sprites, tiles, layers and supported game profiles | Playability and supported export |
| 3D modeling | Supported mesh edits, UV and modifier operations as implemented | Geometry checks and preserved source/derived relationship |
| Animation | Skeletons, clips, timelines and state machines as implemented | Playback and transition tests |
| Visual scripting | Create/connect nodes, configure events, validate graphs | Validator plus runtime behavior |
| Scenarios | Players, objectives, triggers and campaigns as implemented | Deterministic scenario tests |
| Audio/music | Patterns, notes, instruments, mixing and cues as implemented | Structural checks plus listening review |
| UI/effects | Supported UI, particles, lighting and cameras | Visual and interaction checks |
| Play/testing | Start/stop isolated runtime, inspect diagnostics | Authoring state restored after Play |
| Export | Select supported target, package and smoke-test output | Export launches with expected content |

This table describes required future coverage. Missing engine features remain explicit blockers. Desktop support does not imply web parity.

## 9. Tool execution and recovery contract

Every call should carry task/run identity, an authenticated execution context, project scope, schema version, arguments, expected resource revision and a request key where meaningful. Credentials are injected by the gateway, never generated by or exposed to the model.

Results should distinguish completed, rejected, failed, cancelled and still-running work. Return structured errors, changed resources, evidence references, job handles and undo/compensation references where supported.

Within ForgeRoom, group reversible edits into the existing command/undo mechanism. Across Spring Boot, a local editor and external generation services, a single database transaction is impossible: use staged workflows, durable checkpoints and explicit compensation. A paid generation job or published message may not be reversible; communicate that before execution.

Untrusted prompts, retrieved content, imported assets and provider responses cannot grant permissions or modify execution policy. A proposed tool call is input to the gateway, not authority to run it.

## 10. Experience and training records

A proposed record should contain:

- User/project scope and visibility.
- Goal, task category and environment snapshot.
- Model/provider, tool, engine and skill versions.
- Approved actions and observable execution outcomes.
- Validator results, human review and failure category.
- Cost, latency, retries and whether mentor assistance was needed.
- Source provenance and permitted retention/retrieval/training uses.
- Retention/deletion state and review expiry.
- Dataset split and training-admission decision.

Store concise observable evidence and useful explanations; do not depend on private hidden reasoning. Keep secrets and unnecessary personal conversation content out of logs and datasets.

Use separate retrieval indexes or enforced filters for private project data and reusable global skills. Removing a record from retrieval and removing its influence from an already trained model are different operations; define the training-data deletion process before admitting personal data.

## 11. Evaluation and promotion rules

Proposed starting gates below are targets to calibrate after A0, not measured results or guarantees.

| Measure | Initial gate |
|---|---|
| Schema conformance | 100% of accepted calls conform; malformed calls never execute |
| Authorization | Every defined negative test blocks access; any observed cross-scope leak blocks release |
| Read-only alpha tasks | At least 90% pass across 30–50 representative cases, with repeated trials |
| Initial mutation workflows | At least 95% pass on controlled fixtures and no unexplained state corruption |
| Undo/recovery | Every designated reversible fixture restores the expected state |
| Creative workflows | Required structural, Play, reload and export checks pass, plus recorded visual/listening review |
| Learned skill promotion | Passes distinct tasks/projects beyond the demonstration |
| Model promotion | Improves the assigned held-out task metric without material authorization or quality regression |
| Latency and cost | Measured p50/p95 and cost per successful task remain within the A0 budget |

Maintain frozen evaluation cases outside training and memory. Split by project/task family when possible, deduplicate similar demonstrations, and prevent mentor-generated solutions from leaking into the held-out set.

Compare configurations under equivalent tools and budgets: base model; base plus retrieval; custom model; custom plus skills; mentor-assisted mode. Track end-to-end success, local-only success, escalation rate and cost per success. A lower escalation rate is useful only if correctness is maintained.

## 12. Deployment and hardware strategy

| Environment | Role |
|---|---|
| Development laptop | Model implementation, tiny experiments, unit tests and client development |
| CPU VPS | API, orchestration, queue and memory; model inference only if measured performance permits |
| GPU worker | Training, fine-tuning and heavier inference; select capacity after profiling |
| Application environment | Existing UrConnex web/API workloads with independently deployable AI integration |
| ForgeRoom desktop/web runtime | Owns editor state, command execution and local validation |

Keep training off the alpha application host. Benchmark inference before committing to a model size. Preserve checkpoints, configs and dataset manifests outside disposable workers.

Start with the existing PostgreSQL system and ordinary job records where adequate. Add queues, vector infrastructure or distributed workers when measured load or recovery needs justify them.

## 13. Indicative delivery sequence

| Period from start | Practical outcome | Gate |
|---|---|---|
| Weeks 1–2 | Baseline, tool inventory, hardware and rights register | A0 |
| Months 1–3 | PyTorch prototype and limited read-only alpha assistant | A1–A2 |
| Months 3–6 | Supervised application tools, optional mentors, first provider adapter and memory | A3–A5 |
| Months 6–12 | Wider application coverage, ForgeRoom bridge contracts and representative tested slices | A6 onward, subject to engine readiness |
| Months 12–24+ | Creative workflows, evaluated specialist models and broader tool coverage | A7–A9 and ForgeRoom R10 |
| Continuing | New tools, regression checks, skill revalidation and model improvements | Ongoing measured release gates |

Broad capability may take longer than this window. Re-estimate after the first two completed milestones. A 12-month foundation does not imply training a frontier model or mastering every engine feature within a year.

## 14. Repository and ownership proposal

Create a separate urconnex-ai repository:

~~~text
urconnex-ai/
  llm/                 # PyTorch architecture, tokenizer, training, inference
  agent/               # Planner, executor, policies, durable run state
  providers/           # Model, mentor and creative-service adapters
  tools/               # Versioned gateway contracts and adapters
  memory/              # Evidence, retrieval and skill lifecycle
  evaluation/          # Held-out tasks, graders and release comparisons
  api/                 # Python service
  configs/             # Versioned model and runtime configuration
  tests/               # Unit, contract and integration coverage
  docs/                # Decisions, tool matrices, runbooks and reports
~~~

Keep checkpoints, private datasets, keys and generated bulk assets out of source control. Reference versioned artifacts through manifests.

UrConnex web/API changes stay in their existing repositories. ForgeRoom commands, bridge implementation and engine tests stay in ForgeRoom. Define a shared protocol with conformance tests; avoid copying business rules or maintaining competing tool registries.

## 15. First implementation milestone

Begin with A0 and the first slice of A1:

- [ ] Record the alpha baseline and actual repository versions.
- [ ] Inventory tools and document the ForgeRoom R10 dependency.
- [ ] Measure CPU/GPU/RAM and establish a compute budget.
- [ ] Create the separate AI repository and reproducible Python environment.
- [ ] Implement a tiny decoder-only Transformer with causal-mask tests.
- [ ] Use a small dataset with documented training rights.
- [ ] Train, save, resume and generate from a checkpoint.
- [ ] Record held-out loss, throughput, resource use and limitations.
- [ ] Define the model-provider interface before adding any mentor.
- [ ] Publish a milestone report distinguishing implemented, integrated and observed behavior.

The first milestone ends with a working research model and trustworthy baseline. Later milestones earn application actions, mentor integration, ForgeRoom operation and increasing autonomy through evidence.

## 16. Long-term completion criteria

The intended mature agent:

1. Completes the advertised UrConnex workflows within the requesting user's authority.
2. Operates every advertised ForgeRoom tool through validated commands in supported environments.
3. Plans, previews, executes, verifies and explains multi-step work.
4. Requests scoped assistance from enabled external systems when useful and permitted.
5. Learns reusable skills from verified, rights-cleared experience.
6. Demonstrates increased independent success on held-out tasks.
7. Produces editable, reloadable, playable and exportable creative projects.
8. Handles interruption, conflicts, partial failure, provider loss and budget limits predictably.
9. Preserves application and engine boundaries and remains useful when a provider is unavailable.
10. Publishes capability gaps and evidence instead of claiming unrestricted or universal intelligence.

Independent text planning, independent coding, tool use, visual understanding and independent 3D generation are distinct capabilities. Developing a competitive 3D foundation model would require its own licensed dataset, architecture, compute budget and roadmap; it is not assumed as an automatic outcome of this agent programme.

## 17. Sources and verification notes

Internal context:

- Owner confirmation on 17 November 2026 that UrConnex is in alpha.
- Current conversation's PyTorch-first direction and requested integration with other agents and ForgeRoom.
- ForgeRoom roadmap dated 14 November 2026, version 5, particularly the existing six-tool baseline, agent parity rule and R10 sequencing. Reviewed as planning context; no repository or live acceptance audit was performed for this document.

Official references checked on 17 November 2026:

- **[S1] Claude Agent SDK overview:** https://code.claude.com/docs/en/agent-sdk/overview — supported programmatic agent integration; confirm current authentication and account eligibility during implementation.
- **[S2] Tripo API introduction:** https://docs.tripo3d.ai/get-started/introduction.html — text/image-to-3D API integration.
- **[S3] Anthropic Commercial Terms:** https://www.anthropic.com/legal/commercial-terms — relevant limits on competing products/models; the agreement applicable to the selected account controls.
- **[S4] OpenAI Services Agreement:** https://openai.com/policies/services-agreement/ — relevant restrictions and permitted exceptions; check the agreement applicable to the selected service.
- **[S5] Tripo Terms:** https://www.tripo3d.ai/terms — relevant restrictions on competing development/training using outputs and other product-use conditions.
- **[S6] Meshy Terms:** https://www.meshy.ai/terms-of-use — relevant restrictions on competitive use and training with generated digital assets.
- **[S7] Meshy Text-to-3D API:** https://docs.meshy.ai/en/api/text-to-3d — asynchronous preview/refine workflow; model identifiers and availability can change.

Provider documentation supports the integration options, not a claim that those integrations already exist in UrConnex. Recheck terms, endpoint schemas and supported models before enabling a provider or admitting its material into memory or training.

