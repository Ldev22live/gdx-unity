# M1b.6 UX specification for the web UI: report

**Milestone:** M1b.6 in [milestone-chart.md](../milestone-chart.md) (roadmap R1b). **Gate:** none.
**Date:** 2026-11-15. **Platform:** Windows 11 Pro, Java 21 (Gradle toolchain).
**Starting and ending `git rev-parse --short HEAD`:** `d896331` (unchanged). The working tree already held many uncommitted changes from earlier milestones; the files this milestone touched are listed below.

## Why M1b.6

The other open R1 and R1b work needs a person (M1b.5, the hands-on parts of M0.4), an approval (M1.6, TD gate) or an owner decision (the rest of M1.2: new node families or splitting game rules out of player scripts). M1b.6 depends only on M1b.2 (built) and has no gate.

## Done-when and result

*The spec exists and the desktop has no web dependency.*

**Met.**

- **The spec:** [web-ui-ux-spec.md](../web-ui-ux-spec.md), proposed. It covers:
  - the boundary between desktop and web;
  - the layout of Appendix E1 (header, viewport, World strip, footer, panels, the 1024 by 640 minimum);
  - Customize, Edit Selected and Back to World;
  - Play and Stop;
  - the New Game gallery and its computed labels;
  - honest status, keyboard and accessibility, and performance;
  - where this repository's web editor (`engine-babylon/editor.html`) differs today;
  - how conformance is checked.
- **Generated vocabulary:** the spec's shared words are generated from the desktop's code: header controls with action ids and shortcuts, Customize destinations, the World strip per game mode with its "planned" messages, the footer, the template labels, and the gallery status lines.
- **No web dependency:** `WebUxContractTest` follows the editor-desktop module's project dependencies through the build files. It finds no web module (`engine-web`), no web-framework dependency (Spring, servlet, Netty, Jetty, Undertow, Javalin, Ktor, TeaVM and its LibGDX backend), and no import of one in any desktop main source.

## Choices made (for the owner to review)

- **Written from this repository only.** AGENTS.md §2 forbids reading UrConnex, so the spec says nothing about how a UrConnex web UI is built (Next.js, Spring Boot or otherwise). It defines the experience and the shared files, and treats a project server as optional and later (roadmap R2b).
- **The command list is not duplicated.** The spec points to the action inventory in [world-first-ux-audit.md](world-first-ux-audit.md), already generated from the registry, and names commands by id.
- **Labels must be computed on the web too.** Section 5 asks a web UI to show the matrix's label for the same file and never to keep a hand-written list, to keep the M1.7 rule.
- **The web editor's gaps are a list, not a plan.** Section 9 compares `engine-babylon/editor.html` with the spec. I first wrote that its Play opens the separate player page, then read `EditorApp.ts`: it plays a `structuredClone` of the scene in place. That row was corrected before publishing.

## Tests

| Test | What it shows |
|---|---|
| `WebUxContractTest` (4, new, editor-desktop) | The generated section in the spec is current (`GDXUNITY_UPDATE_AUDIT=1` rewrites it); every workspace, World strip section and template label appears in it; the desktop editor's module closure has no web module, web dependency or web import; the patterns do catch a Spring Boot dependency and a Spring import |

## Commands and exit codes

| Command | Exit | Result |
|---|---|---|
| `GDXUNITY_UPDATE_AUDIT=1 ./gradlew :editor-desktop:test --tests "*WebUxContractTest"` | 0 | Wrote section 11 of the spec |
| `./gradlew :editor-desktop:test --tests "*WebUxContractTest"` | 0 | 4/4 |
| `./gradlew cleanTest test --continue` (whole repository) | 0 | **1,954 tests, 0 failures, 0 errors, 1 skipped** (the POSIX-only exporter test), 23 modules |

**Counts (from the XML reports):** editor-desktop 154 to **158** (+4, WebUxContractTest); every other module unchanged. Repository 1,950 (after M1.2 stage 1) to **1,954**, 0 failures.

## Limits

- **No web UI was changed.** The spec says what one should do; this repository's web editor still has the gaps listed in section 9.
- **Not reviewed by the owner.** It is marked proposed. The roadmap's governance (Appendix E6) means any implementation that changes navigation contracts still needs approval.
- **The dependency check reads build files by pattern,** not Gradle's resolved dependency graph. A dependency that arrives only transitively through a library (none does today) would not be seen by name.
- **Manually Not Verified:** nothing in a browser; the spec is a document.

## Next

- The owner reviews the spec.
- If approved, the web editor can be brought in line in stages, starting with the thin header and the footer, which mirror M1b.2 and M1b.3.

## Files changed

- New: `docs/web-ui-ux-spec.md`, `editor-desktop/src/test/.../audit/WebUxContractTest.java`, this report.
- Changed: `docs/milestone-chart.md` (M1b.6 row), `docs/FEATURE_TEST_MATRIX.md` (a section), `docs/INDEX.md`.

## Addendum, 2026-11-15: revised for roadmap Appendix G

The roadmap's 2026-11-15 revision ([ForgeRoom_Master_Roadmap_2026-11-15_Desktop_Web_Scoped.md](../ForgeRoom_Master_Roadmap_2026-11-15_Desktop_Web_Scoped.md)) added Appendix G: Desktop is the complete studio, ForgeRoom Web in UrConnex a smaller creator, and **feature parity is not required**. The spec as first written conflicted with it: its web Customize listed every desktop workspace, and its gallery had only the desktop labels. The spec was revised to follow Appendix G, which the roadmap says wins over older web passages:

- **Section 1:** the platform split, UrConnex's own design system, and the safe-save rules of G3 (supported fields only, everything else kept, a save that cannot keep something is blocked with the reason, version checks).
- **Section 2:** the layout as a Desktop and Web comparison; the web's strip offers only what works there and writes its own planned messages; only browser-verified genre and mode profiles can be played.
- **Section 3:** the web Customize is a short task list with no desktop workspace or empty tab; Desktop-only edits are labelled handoffs ("Edit geometry in ForgeRoom Desktop").
- **Section 4:** browser Play only for verified profiles; graphs only through a tested safe interpreter; never Java, Kotlin or untrusted scripts; no native export from the web.
- **Section 5:** a desktop label (Appendix F) and a separate, computed web label (Web Editable, Web Preview Only, Desktop Required). No template has been checked in a browser, so none has a web label yet. That rule ("not yet checked on the web" until checked) is my choice, for the owner to confirm.
- **Section 6:** the five G2 capability labels.
- **Section 9:** the web editor's working Animations, Visual Scripting and Music tabs are now Desktop-only features; keeping, hiding or retiring them is left to the owner (chart decision 24). A new row asks WEB0 and WEB2 to audit whether its save keeps fields it does not know.
- **Section 10:** the G6 preservation test.
- **Section 11 (generated):** `WebUxContractTest` now heads the Customize list, the strip messages, the template labels and the gallery lines as the desktop's, with a line saying what the web uses instead.

If the owner rejects the split (decision 19), sections 3, 5 and 9 go back to the parity version.

| Command | Exit | Result |
|---|---|---|
| `GDXUNITY_UPDATE_AUDIT=1 ./gradlew :editor-desktop:test --tests "*WebUxContractTest"` | 0 | Rewrote section 11 |
| `./gradlew :editor-desktop:test --tests "*WebUxContractTest"` | 0 | 4 tests, 0 failures, 0 skipped (from the XML report) |

The full suite was not re-run for this addendum: only this test class's generated text changed, and no main source.

## Acceptance criteria (AGENTS.md §10)

1. Criteria listed and marked: met, with the limits above.
2. Commands, exit codes and counts: above.
3. New failures investigated: none; the new tests passed on their first run and the full suite passed.
4. Diff reviewed, no unrelated changes: only the files above.
5. Matrix and INDEX updated: done.
6. Limits and next steps: above.
7. **No commit, push or other destructive Git operation was performed.**
