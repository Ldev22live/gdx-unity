---
description: Review a working-tree diff for correctness, conventions and release readiness
---

# Review a diff

1. `git status --short` and `git diff --stat`. Separate your changes from pre-existing user changes.
2. For each changed file, check:
   - [ ] **Correctness:** null/invalid input, disposed state, empty scene/hierarchy, parent transforms,
         float edge cases (NaN, zero length, parallel ray/plane).
   - [ ] **Consistency:** renderer output matches hit-tests; serializer covers every persisted field;
         editor mutations are undoable commands.
   - [ ] **Resources:** each `Disposable` has one owner; `dispose()` idempotent; no use after dispose.
   - [ ] **Conventions:** `AGENTS.md` §6 — named constants, no inline FQNs, no swallowed exceptions in new
         code, no fabricated fallbacks, Javadoc on public API.
   - [ ] **Module boundaries:** no engine → editor dependency; logic in `editor-core`, not `editor-desktop`.
   - [ ] **Approved decisions:** the change complies with the relevant TD-xxx in `docs/TECHNICAL_DECISIONS.md`.
   - [ ] **Hot paths:** no new per-frame allocations in `render`/`update`.
3. Tests: each behavior change has an asserting test that fails without it. No test was weakened or disabled.
4. Docs: status words follow `AGENTS.md` §9; `docs/FEATURE_TEST_MATRIX.md` reflects the change; links are
   repository-relative.
5. Report findings ordered by severity, each with `path:line`, the concrete failure scenario, and a fix.
