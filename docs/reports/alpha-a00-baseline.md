# Alpha A00: Baseline protection (audit)

**Date:** 2026-11-25. **Milestone:** A00 of [milestone-roadmap.md](../milestone-roadmap.md). **Status:** audit only, no source code changed. **Done-when not met:** it needs the owner's approval of a checkpoint strategy (section 3).

**Governance:** no staging, commit, push, reset, clean, stash, download, new module or dependency. The only files written in this audit are this report, [alpha-a01-ux-audit.md](alpha-a01-ux-audit.md), [alpha-a02-a11-a13-proposals.md](alpha-a02-a11-a13-proposals.md) and the screenshots in `docs/reports/alpha-a01/`.

## 1. Repository state (verified)

| Item | Value |
|---|---|
| Branch | `phase-12-3d-foundation` (as reported) |
| HEAD | `b8bace5` "Forgeroom alpha" (as reported). That title is a commit message, not release evidence |
| Working tree | `git status --porcelain -uall`: **319 entries**, 91 modified tracked files and 228 untracked files. Shown without `-uall`, about 183 lines (untracked folders collapsed); the status report's "about 170" counted those |
| Secrets | None found. No secret-like file names. A pattern scan of every changed text file (API keys, private keys, `password=` literals, `sk-…`, `AKIA…`) found nothing |
| `settings.gradle` | Modified: adds `engine-vector` (TD-104, approved 2026-11-24). Already in the build; listed so the commit includes it |

### Inventory by area

| Area | Modified | Untracked | Notes |
|---|---|---|---|
| Engine and editor modules | engine-core 4, engine-scene 5, engine-render 10, engine-sculpt 1, engine-sprite 3, engine-desktop 3, editor-core 20, editor-sprite 2, editor-desktop 16, engine-babylon 5 | engine-vector 15, engine-sculpt 6, editor-sculpt 9, editor-sprite 7, editor-desktop 10, engine-desktop 6, engine-render 4, engine-sprite 3, engine-scene 1, editor-core 1 | The source of M7a.1 to M7a.6, MC.2, MC.3, MC.4, M3c.6, M3c.7 and M3b.7 slice 4 (their reports list the files) |
| Samples and templates | 6 samples, gallery, licences manifest | 2 new samples (`hybrid-side-scroller-25d.json`, `third-person-yard-3d.json`), 2 materials, 8 model files, 3 texture files, 1 script, `asset-classification.json` | |
| `fixtures/` | | 17 (6.3 MB) | Mesh-source fixtures (MC.2, M7a.6) |
| `asset-sources/` | | **52 files, 148 MB** | Downloaded originals kept as sources (MC.1, M7a.6, OpenGameArt). Includes a 43 MB `.blend`, 22 MB and 11.6 MB zips, a 17 MB `.xcf` and a `.db` file (probably a Windows thumbnail cache) |
| `docs/` | 11 (matrices, chart, INDEX, TDs, migration) | 76 (reports and their images, `docs/assets/` 41 files 9.1 MB, `milestone-roadmap.md`, `25-nov-forgeroom-current.md`, today's screenshot) | |
| `tools/` | 1 | 4 (`blend_to_static_glb.py`, two OpenGameArt converters, `test_blend_fixture.py`) | |
| Root | `AGENTS.md` | | |

The full listing is reproducible with `git status --porcelain=v1 -uall`. It is not copied here; it changes with every edit.

## 2. Risks to the work

- **Nothing since `b8bace5` is committed.** A disk failure or an accidental `git clean` or `reset` would lose about two weeks of work: R7a, MC.2 to MC.4, M3c.6 and M3c.7, slice 4.
- **`asset-sources/` (148 MB of binaries)** should not go into an ordinary commit without a decision. The options are Git LFS, a separate archive with hashes recorded in the manifest, or an ordinary commit. The `.db` file should be excluded whichever is chosen.
- **`engine-babylonjs/`** is an empty, untracked folder. Git does not track it; it is harmless. Remove it only with approval.

## 3. Proposed checkpoint (needs approval; nothing staged)

Commits on the current branch, in this order. Each is checked by its tests before the next:
1. **Engine and editor source with tests.** Includes `settings.gradle`, `engine-vector`, the sculpt, sprite and render changes, and tools. Gate: the full Gradle run below.
2. **Samples, templates, fixtures and the licence manifest.** Gate: the sample audits, `SampleMigrationsTest` and the web suite.
3. **Documentation:** reports, matrices, chart, TDs, this audit.
4. **`asset-sources/`** only after the owner chooses LFS, an archive, or an ordinary commit.

The commit messages and the push are separately authorised (AGENTS.md §3). An alternative is one commit for 1 to 3 if the owner prefers.

## 4. Test baseline

**Fresh runs today (2026-11-25), Windows 11, 16 GB, `--max-workers=1`:** see section 4.1. Earlier runs in today's session are listed for history only. They are **not** merged into the fresh result.

### 4.1 Fresh full run: **not obtained**

**The attempt:** `.\gradlew.bat test -x :editor-desktop:test --continue --max-workers=1 --offline -x generateAssetList`, after `--stop`, started about 12:25.

**What happened:** Claude Code stopped it while it was still compiling, because the machine was critically low on memory (0.5 to 1.8 GB of 16 GB free during today's runs, with browsers using most of the rest). No module wrote results, so **there is no fresh full-suite result, and none is claimed.**

**Next attempt:**
1. Close the browsers first, or start Claude Code with `CLAUDE_CODE_DISABLE_BG_SHELL_PRESSURE_REAP=1`.
2. Run the modules in small foreground batches, each finishing within 10 minutes: the engine modules first, then the editor modules, then `editor-desktop` in class groups.

### 4.2 Earlier today (history, not a fresh full run)

- editor-core 616 tests (1 skipped), engine-desktop 162 (plus 7 new passing), engine-render 213: 0 failures.
- The editor-desktop sample, scenario and audit tests passed.
- Babylon 520 of 521.

## 5. Failures and open classifications

| Item | Classification | Proposed handling |
|---|---|---|
| Babylon `shouldSupportEveryComponentTypeUsedByTheSamples` fails on `action-rpg-valley-3d.json` (`AtmosphereComponent`) | **Pre-existing**, from an uncommitted sample change that predates these sessions; not a regression from Alpha work | Owner decision: list `AtmosphereComponent` as known-unsupported on the web (and test that the scene still loads and plays without it), or port atmosphere to Babylon. Recommendation: list it now, port during A13/A14 if wanted |
| TeaVM `engine-web` build | Broken (M9.4, recorded) | Propose: unsupported for Alpha, excluded from release checks, documented. The Babylon player is the web runtime. `docs/deploy-steps.md` and `engine-web/EMBEDDING.md` still describe TeaVM deployment and should be marked historical |
| Template audit marks glTF "FAIL (no mesh editor)" | Obsolete rule since R7a | Fix in A00 or A08 with capability-specific tests: static glTF editable; skinned and extension-heavy models still FAIL honestly |
| Web depth ordering of flat 2D among themselves | Known gap (MC.4) | A13/A14 |

## 6. Reconciliation of the status registers

- **The roadmap now names `docs/milestone-roadmap.md` as authoritative.** `milestone-chart.md`, `FEATURE_TEST_MATRIX.md` and `BACKEND_SUPPORT_MATRIX.md` are evidence registers.
  - A one-line note at the top of the chart saying so is proposed. Not added in this audit.
- **The directive names `docs/14-nov-forgeroom-roadmap-revised.md`, which does not exist.**
  - The 14 November documents present are `forgeroom-roadmap-revised-2026-11-14.md`, `ForgeRoom_Master_Roadmap_2026-11-14_UX_Updated.md` and `ForgeRoom_Master_Roadmap_2026-11-14_Fully_Editable_Templates.md`.
  - The chart's source is `ForgeRoom_Master_Roadmap_2026-11-15_Desktop_Web_Scoped.md`.
  - Please confirm which one was meant. The 15 November master is treated as the historical baseline.
- **`docs/milestone-chart - Copy.md`** exists beside the chart. It looks like an accidental copy; remove it only with approval.
- **Suspended:** M3b.7 further slices (Racing + 3D and later) are suspended by the roadmap until unified Alpha acceptance.
