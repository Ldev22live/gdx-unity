---
description: Inspect, plan, implement, verify and report one milestone or feature in GdxUnity
---

# Implement a milestone or feature

Follow `AGENTS.md` throughout. Stop and ask before anything irreversible or architectural (new dependency,
new script runtime, module changes, version upgrades).

## A. Inspect
- [ ] Run steps 1–2 of the `milestone-audit` workflow. Note pre-existing modified files; do not overwrite them.
- [ ] Confirm the feature does not already exist (`grep` for likely class names and roadmap terms).
- [ ] Read the approved decisions in `docs/TECHNICAL_DECISIONS.md` that touch the area (e.g. TD-008 resources,
      TD-016 gizmos, TD-018 commands, TD-019 scripting). The plan must comply or explicitly propose a change.
- [ ] Read the milestone's section in `docs/ALL_IN_ONE_STUDIO_ROADMAP.md` and any `docs/milestones/` doc.
- [ ] Run the `verify-build` workflow to get the baseline test counts. Record any pre-existing failures.

## B. Plan (write it in the reply before coding)
- [ ] Smallest complete change; list affected modules and files.
- [ ] Expected behavior and failure cases (null/invalid input, disposed state, empty scene).
- [ ] Test list: one test per behavior and failure case, each named `should…`.
- [ ] Docs to update. Out-of-scope items.

## C. Implement
- [ ] Put logic in the lowest correct module (`AGENTS.md` §4); keep GL/UI code thin.
- [ ] Editor mutations go through `EditorCommand`; new components get a serializer and registration.
- [ ] Every `Disposable` has one owner and an idempotent `dispose()`.
- [ ] Named constants, imports (no inline FQNs), Javadoc on public API.
- [ ] Write tests alongside; confirm each new test fails without the change (reason it through or check).

## D. Verify
- [ ] `verify-build` workflow: targeted tests, then full `./gradlew test --continue`.
- [ ] `review-diff` workflow on your own changes.
- [ ] If the feature is user-facing, write or extend a manual smoke checklist (pattern:
      `docs/milestones/milestone-6-transform-gizmos.md` §7). If you cannot observe the screen, mark it
      **Manually Not Verified (Headless Environment)**.

## E. Report (`docs/reports/<milestone>-report.md` + reply)
Use the checklist in `AGENTS.md` §10. Update `docs/FEATURE_TEST_MATRIX.md` and `docs/INDEX.md`.

## F. Stop
Do not commit, push, or start the next milestone. Wait for the user.
