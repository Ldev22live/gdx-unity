# Alpha A12.8: Capability negotiation

**Date:** 2026-11-25. **Status:** implemented on both clients; the desktop editor's Save honours it.

**The rule (TD-106 point 5):**
- **A missing required capability:** the project opens **read-only**, the reason is shown, and Save is refused.
- **A missing optional capability:** the project is editable, the missing features are named, and their data is kept.
- **On save:** a client only adds ids, never removes them. Nothing is converted to a reduced feature set.

**Supported sets:**
- **Desktop:** `CapabilityPolicy.DESKTOP`.
- **Web:** `WEB_SUPPORTED`, listed in [capability-matrix.md](../contracts/capability-matrix.md).
- **Scenario data versus rules:** `scenario/1` and `ownership/1` mean the data is kept and checked. Running rules is a separate id, `scenario-run/1`, which no client supports until A05, so no client claims rule execution early.

| Check | Desktop | Web |
|---|---|---|
| Missing required → read-only with the reason | `ProjectContractTest.shouldOpenReadOnly…` (a web-like set lacking `scenario/1` and `ownership/1`) | `contract.test.ts` "opens read-only…" (`teleporters/3`) |
| Read-only refuses saving, nothing written | Same test (`SaveResult.ReadOnly`, manifest unchanged); **in the editor:** `SceneFileControllerContractTest.shouldOpenReadOnlyAndNeverSave…` (status shows the id, scene file unchanged, error says read-only) | `evaluate().access` is `read-only`; the web save UI is A13 and A14 work |
| Missing optional → limited, still editable | `ProjectContractTest.shouldEditWithLimits…` (`profile:action-rpg/3d`) | Fixture opens "limited" naming the profile |
| Optional ids kept on save | `optional` unchanged after a limited client's save | `nextManifest` keeps `optional`; `required` only grows |
| Unsupported data kept | `PreservedComponent`, preserved fields | Raw JSON (tested) |

**Classification decisions:**
- `AtmosphereComponent` is an optional web capability (`atmosphere/1`, presentation only): its data is preserved and the web names it as not drawn.
- An essential gameplay capability (a genre profile a project depends on to play at all) is not made optional to allow editing. The fixture lists `profile:action-rpg/3d` as optional because the scene stays meaningful without it: it can still be edited and laid out.
- Whether a real template's profile should be required is decided per template at A08.
