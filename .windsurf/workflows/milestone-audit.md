---
description: Read-only audit of repository state and a milestone's real implementation status
---

# Milestone audit (read-only)

Rules: `AGENTS.md` §3 (Git safety) and §9 (status words). This workflow changes no files except the report.

1. Record repository state:
   ```bash
   git rev-parse --show-toplevel
   git branch --show-current
   git rev-parse --short HEAD
   git status --short
   ```
   List every modified and untracked file in the report. These are the user's changes — do not edit them.
2. Read `AGENTS.md` (§0 lists the key docs), `docs/INDEX.md`, `docs/FEATURE_TEST_MATRIX.md`, the
   milestone section of `docs/ALL_IN_ONE_STUDIO_ROADMAP.md`, and the relevant decisions in
   `docs/TECHNICAL_DECISIONS.md`. Check the code against approved decisions as well as the roadmap.
3. For each milestone deliverable:
   - [ ] Find the class(es) implementing it (`grep` for the type name). Missing → **Not implemented**.
   - [ ] Find tests that assert its behavior. None → **Implemented, untested**.
   - [ ] Find a call site outside tests (editor/runtime). None → **Implemented, not integrated**.
   - [ ] Check for placeholders: empty method bodies, `// TODO`, no-op overrides, fabricated fallbacks.
4. Run the baseline (see `verify-build` workflow) and record per-module test counts.
5. Compare findings against claims in existing reports; list every claim that the code does not support.
6. Write `docs/reports/<milestone>-audit.md` with: state from step 1, deliverable table (status + evidence
   path), test counts, discrepancies, recommended next steps. Make no other changes.
