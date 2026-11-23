# MT.8: Fully Editable Template Audit

**Date:** 2026-11-18. **Branch:** `phase-12-3d-foundation`. **HEAD at start and end:** `0469ceb` (nothing committed). **Gate:** none in the chart.

**Why this milestone:** after MP.3, the rows that could start were MT.8 and MC.1. MC.1 needs outside research into each candidate's licence, which is better started when the owner can review the sources. MT.8 depends only on M1.1 and is an audit of this repository.

**Scope, from Appendix F7:** audit first, implement only after approval. This milestone adds audit tooling and the report. It changes no sample, graph, engine or editor behaviour, and it records the decisions it needs as proposals for the owner, not as TDs.

## 1. Acceptance criteria

| # | Criterion (chart row MT.8) | Status | Evidence |
|---|---|---|---|
| 1 | `docs/reports/fully-editable-template-audit.md` | **Met** | [The audit](fully-editable-template-audit.md) |
| 2 | A PASS, FAIL, BLOCKED, NOT APPLICABLE matrix over every existing sample, graph and project | **Met** | 45 samples × 14 gates, 20 graphs × load, validate and parity, and the one project's checks; the sample and graph tables are generated and test-checked |
| 3 | Covers hardcoded-only logic, baked-only models and music, missing editor registration, missing source, licences, broken references and non-working exports | **Met** | One gate each. No sample has baked-only music (every song is a `.song`) |
| 4 | A staged migration plan and any needed TDs | **Met** | Six stages, and four decisions for the owner (audit sections 4 and 5) |
| 5 | Existing samples preserved; nothing claimed that is unsupported | **Met** | No sample or graph changed. A gate nothing has checked is BLOCKED, never PASS (asserted) |
| 6 | Done-when: "The report and matrix exist with evidence links" | **Met** | Each gate's evidence is linked in the audit's section 2; each cell gives its reason |

## 2. What was built

- **`TemplateAudit`** (`editor-desktop/.../audit`):
  - Each sample's 14 gates come from the M1.1 `TemplateMatrix` and `EditabilityAudit`, the MP.1 `SampleInventory`, and `GraphValidator`.
  - Play and Duplicate-and-export are PASS only for samples the gallery lists, since that is what `TemplateGalleryProjectsTest` reaches.
  - Four gates are always BLOCKED until something checks them: per-domain edit with undo, exported game launches, licence verified, and seen and heard.
  - Every graph is listed with whether it loads and validates, the samples that run it, and its parity with the Java script it mirrors (from `examples.json`).
- **`TemplateAuditTest`** (5 tests):
  - two rule tests on a small project written for them:
    - Java logic, a broken reference, a baked ground and an invalid graph each FAIL;
    - a scene outside the gallery is BLOCKED for Play and export;
    - the four unchecked gates are BLOCKED;
  - every shipped sample passes save and reopen, Play, project export, references and editor registration, and none passes every gate. The 20 Java-logic samples are counted;
  - all 20 graphs load and validate, and exactly the five of M1.2 match their scripts;
  - the report's three tables are current.

## 3. Findings

| Gate | PASS | FAIL | BLOCKED | n/a |
|---|---|---|---|---|
| Source present | 38 | 7 | 0 | 0 |
| Editors open (M1.1) | 17 | 28 | 0 | 0 |
| No hardcoded-only logic | 2 | 20 | 0 | 23 |
| No baked-only content | 1 | 7 | 0 | 37 |
| Editors registered | 45 | 0 | 0 | 0 |
| References resolve | 45 | 0 | 0 | 0 |
| Graphs valid | 6 | 0 | 0 | 39 |
| Save and reopen, Play, Duplicate and export | 45 each | 0 | 0 | 0 |
| Exported game launches, Edit and undo per domain, Licence, Seen and heard | 0 | 0 | 45 each | 0 |

- **No sample is Fully Customizable**, and that agrees with the gallery's M1.7 labels.
- **The biggest gap is Java-only logic** (20 samples). Ten graph versions exist but are partial, and none is wired in.
- **Template versioning is missing:** a project made from a template does not record which template or version it came from (F4).
- **One rule corrected while building:** "Editors registered" first reused the M1.1 audit's list of file types with an editor of their own, so it failed terrain files, which World: Terrain edits. The gate now means "a component with no Details editor". Missing model and image editors are the Editors and Baked gates' findings.

## 4. Commands and results

| Command | Exit | Result |
|---|---|---|
| `GDXUNITY_UPDATE_AUDIT=1 ./gradlew :editor-desktop:test --tests "*TemplateAuditTest"` (first run) | 1 | 4 of 5; the registration rule above failed the real-sample test. Fixed, then 0 |
| `./gradlew :editor-desktop:test --tests "*TemplateAuditTest"` | 0 | 5 of 5; the tables are current |
| `./gradlew :editor-desktop:test` | 0 | **175** (170 before, +5), 0 failures, 0 errors |

Other modules were not changed or rerun: engine-desktop 147 and editor-core 539 after MP.3, earlier today. **Not run:** the full `./gradlew test --continue` and the Babylon suite (no scene, node or component changed).

## 5. Limitations and next steps

- **The Play and export gates come from headless tests:**
  - Play is 2 seconds in the game runtime's session, not editor Play with a window;
  - export writes the project half with a stand-in runtime image.
- **Graph parity is as `examples.json` records it.** The audit trusts its "Nothing left out" wording, which the M1.2 parity tests back for exactly those five graphs.
- **The audit judges files, not a person's experience.** The four BLOCKED gates need MT.9's journeys and a person.
- **Decisions for the owner** (audit section 5): asset licence policy, template versioning, how to convert baked grounds to terrain, and what happens to Java scripts once their graph matches.
- **Next:** MC.1 (authoring-sample source audit) is the remaining startable audit. Stage 1 of the plan is M1.2's remaining conversions, which is in progress.

## 6. Diff review and Git

- **New:**
  - `TemplateAudit.java` and `TemplateAuditTest.java`
  - `docs/reports/fully-editable-template-audit.md`
  - this report
- **Changed:** `docs/milestone-chart.md` (the MT.8 row and the MT overview), `docs/FEATURE_TEST_MATRIX.md`, `docs/INDEX.md`. All three were already modified in the working tree; only these rows were added.
- No commit, push, stash, reset or other Git operation was performed. HEAD is `0469ceb` at start and end.
