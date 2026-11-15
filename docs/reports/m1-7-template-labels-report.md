# M1.7 Template labels and the release gate: report

**Milestone:** M1.7 in [milestone-chart.md](../milestone-chart.md) (roadmap phase R1, Appendix F5). **Gate:** none.
**Date:** 2026-11-15. **Platform:** Windows 11 Pro, Java 21 (Gradle toolchain), real display.
**Starting and ending `git rev-parse --short HEAD`:** `d896331` (unchanged). The working tree already held many uncommitted changes from earlier milestones; the files this milestone touched are listed below.

## Why M1.7

M1.4 left the gallery with a fixed line, "Not yet rated Fully Customizable", and named M1.7 as the gate. M1.7 depends only on M1.1 (met) and has no approval gate. M1.2 comes earlier in the chart but means converting 14 graphs, and M1b.5 needs an unfamiliar person.

## Done-when and result

*The gallery shows the right label for every template, driven by the matrix and not by hand.*

**Met, with the limits below.** Each time a template is shown, the gallery rates it with `TemplateMatrix.evaluate` on its scene file, the same evaluation that writes the table in [FEATURE_TEST_MATRIX.md](../FEATURE_TEST_MATRIX.md). No label is stored anywhere. `TemplateRatingsTest` checks that all 40 offered templates get exactly their matrix label with its reasons, and that changing a scene changes its label with no other edit.

## The labels

From the most to the least editable (`TemplateMatrix.Label`, chosen by `Entry.label()`):

| Label | When | Today |
|---|---|---|
| **Fully Customizable** | Every used domain passes **and** every Appendix F5 gate is checked | 0. The six gates in `UNCHECKED_GATES` (a user's edit, undo, Play and Stop, export and launch, license manifest, a person's visual and listening checks) are not automated, so this label cannot be reached yet |
| **Not yet rated (checks pending)** | Every used domain passes; gates unchecked | 13 |
| **Technical Demo / Partially Editable** | A used domain fails, and any flattened content has a project-owned editable replacement | 20 (19 with Java logic, and Sculpted Rock) |
| **INCOMPLETE** | Flattened imported content with no editable source and no project-owned replacement (Appendix F1) | 7: the five landscape scenes and Mesh Colliders (baked glTF ground), Character Creator (clips baked into a glTF) |

**Flattened content and its replacement** (`TemplateMatrix.Flattened`). This is a judgement call, written out in `TemplateMatrix`'s Javadoc for the owner to confirm or change:

| Flattened content | Replacement offered | Why |
|---|---|---|
| glTF model (not the ground) | **Yes:** Customize > Edit in 3D sculpts a project-owned copy (M1b.7) | The copy is a project file the sculpt editor changes |
| glTF ground | **No** | Appendix F2 asks for a native `.terrain`; there is no conversion yet (M3.10). A sculpted copy is not a terrain |
| Clip baked into a glTF | **No** | No `.anim` equivalent is in the template. The character's `.character.json` source and the humanoid clips exist in the shipped assets, but a template does not copy them (M1.6) |
| PNG or JPEG for a sprite or tileset | **No** | There is no import into a layered `.sprite`. No shipped sample uses one; the rule is tested |
| Recorded sound clip | **No** | There is no sound editor yet (MC.5). No shipped sample uses one; the rule is tested |

No shipped song uses a SoundFont or a WAV, so the SF2 and SFZ case of Appendix F1 does not arise.

## What was built

| Part | Detail |
|---|---|
| `TemplateMatrix` | `Label` enum (text and meaning), `Flattened` record, `Entry.flattened()`, `Entry.label()` returning the label by the rules above; the table's Label column lists the flattened content and its replacement |
| `TemplateRatings` (new, editor-desktop `audit`) | Rates a gallery choice from its scene now; `Rating` holds the label, a headline ("<label>: <meaning>.") and the reasons: failing domains with evidence, flattened content with its replacement, and the unchecked gates when checks are pending. Empty starts are not templates and are refused |
| `TemplateGalleryScreen` | The fixed line is replaced by the rating: headline, then the reasons in a short scrolling list. A rating that fails is shown as "Could not rate this template: ...", never guessed |
| `EditorApplication` | Keeps the Details editors in a field; the gallery's `rating` action builds the audit from the editor's own serializers and Details editors |

## Tests

| Test | What it shows |
|---|---|
| `TemplateMatrixTest` (14; 2 new, 1 changed) | New: INCOMPLETE for a baked ground, a baked clip, a PNG and a recorded clip; Partially Editable for a glTF prop, with its replacement; INCOMPLETE outranks a Java failure; the shipped samples: exactly the seven INCOMPLETE, none Fully Customizable, checks pending exactly when nothing fails, Sculpted Rock Partially Editable. Changed: the label is the enum. The table in the feature matrix regenerated |
| `TemplateRatingsTest` (3, new) | All 40 offered templates: the gallery label equals the matrix label, every label has reasons, each failing domain and flattened item is among them; counts 7 / 20 / 13 / 0. A scene in a temporary project changed from a light to a baked ground goes from checks pending to INCOMPLETE. An empty start is refused |

## Evidence from the running editor

| Run | Result |
|---|---|
| `GDXUNITY_EDITOR_GALLERY="Landscape Valley" GDXUNITY_EDITOR_WINDOW=1920x991 ... --smoke-test=180` | Exit 0, 180 frames, 0 errors. [gallery-incomplete.png](m1-7-labels/gallery-incomplete.png): "INCOMPLETE: it holds flattened content that cannot be edited and has no editable replacement in the template", then Terrain, 3D models and the two flattened items |
| Same, `GDXUNITY_EDITOR_WINDOW=1024x640` | Exit 0, 0 errors. [gallery-incomplete-1024x640.png](m1-7-labels/gallery-incomplete-1024x640.png): the label, reasons, name field and buttons fit at the minimum window; the last reason scrolls |
| `GDXUNITY_EDITOR_GALLERY="Circuit Racer"` | Exit 0, 0 errors. [gallery-partial.png](m1-7-labels/gallery-partial.png): "Technical Demo / Partially Editable", Logic and Gameplay are Java source |
| `GDXUNITY_EDITOR_GALLERY="Terrain Valley"` | Exit 0, 0 errors. [gallery-pending.png](m1-7-labels/gallery-pending.png): "Not yet rated (checks pending)" and the checks not yet made |

## Commands and exit codes

| Command | Exit | Result |
|---|---|---|
| `GDXUNITY_UPDATE_AUDIT=1 ./gradlew :editor-desktop:test --tests "*TemplateMatrixTest" --tests "*TemplateRatingsTest" --tests "*EditabilityAuditTest"` | 0 | Regenerated the table in the feature matrix |
| The same without the variable | 0 | TemplateMatrixTest 14/14, TemplateRatingsTest 3/3, EditabilityAuditTest 8/8 |
| The four editor smoke runs above (the first round, with a 64-pixel reasons box, then again after raising it to 80) | 0 each | 180 frames, 0 errors each |
| `./gradlew cleanTest test --continue` (whole repository) | 0 | **1,945 tests, 0 failures, 0 errors, 1 skipped** (the POSIX-only exporter test), 23 modules |

**Counts (from the XML reports):** editor-desktop 148 to **153** (+5: TemplateMatrixTest +2, TemplateRatingsTest +3); every other module unchanged (editor-core 452, 1 skipped). Repository 1,940 (after M1.4) to **1,945**, 0 failures.

## Limits

- **Manually Not Verified (Headless Environment):** nobody has clicked through the gallery or read the labels. The screenshots are the editor's own frames from smoke runs that pre-select a template.
- **The replacement rules are a judgement.** Counting "sculpt a copy" as a replacement for a glTF prop, but not for a baked ground, is written out above for the owner to confirm. Changing it is a one-line change in `TemplateMatrix`, and the tests pin it.
- **Nothing was made editable here.** Licensed editable equivalents and new replacement workflows are other milestones: a `.terrain` conversion (M3.10), mesh editing (R7a), copying a character's sources into a template (M1.6) and a sound editor (MC.5).
- **The matrix's own limits stand** (M1.1): it reads scene files and does not see interfaces drawn in script code. The checks a person must make are listed as pending, never passed.
- Empty starts carry no label: they are not templates and hold only a camera, lights and ground.

## Next

- The owner confirms or changes the replacement rules.
- A person reads the labels in Project > New Game for a few templates.

## Files changed

- New: `editor-desktop/.../audit/TemplateRatings.java`, `editor-desktop/src/test/.../audit/TemplateRatingsTest.java`, this report and `m1-7-labels/*.png` (4).
- Changed: `editor-desktop/.../audit/TemplateMatrix.java` (labels, flattened content), `TemplateMatrixTest.java`, `editor-desktop/.../ui/TemplateGalleryScreen.java` (rating instead of the fixed line, body 470 to 520 pixels), `EditorApplication.java` (Details editors kept, `rating` action), `docs/FEATURE_TEST_MATRIX.md` (regenerated table, label rules, a gallery row), `docs/milestone-chart.md` (M1.7 row), `docs/TECHNICAL_DECISIONS.md` (a dated note in TD-086's limits), `docs/INDEX.md`.

## Acceptance criteria (AGENTS.md §10)

1. Criteria listed and marked: above. The done-when is met; not done: a person reading the labels.
2. Commands, exit codes and counts: above.
3. New failures investigated: none; the new tests passed on their first run and the full suite passed.
4. Diff reviewed, no unrelated changes: only the files above.
5. Matrix and INDEX updated: done.
6. Limits and next steps: above.
7. **No commit, push or other destructive Git operation was performed.**
