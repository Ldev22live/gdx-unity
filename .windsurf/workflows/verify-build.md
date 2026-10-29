---
description: Run GdxUnity builds and tests and record exact, verifiable results
---

# Verify build and tests

`gradle.properties` sets the log level to `quiet`, so a passing build prints almost nothing. Evidence comes
from exit codes and XML reports, never from silence.

1. Targeted tests for the changed module(s):
   ```bash
   ./gradlew :<module>:test --tests "*<ClassName>Test" --console=plain; echo "EXIT=$?"
   ```
2. Full suite:
   ```bash
   ./gradlew test --continue --console=plain; echo "EXIT=$?"
   ```
3. Count results per module from `*/build/test-results/test/*.xml` (attributes `tests`, `failures`,
   `errors`, `skipped`). Example (Python 3):
   ```python
   import glob, os, xml.etree.ElementTree as E
   for f in sorted(glob.glob(os.path.join('*', 'build', 'test-results', 'test', '*.xml'))):
       r = E.parse(f).getroot()
       print(os.path.normpath(f).split(os.sep)[0], r.get('name'), r.get('tests'), r.get('failures'), r.get('errors'))
   ```
   XML files from an earlier run can remain if a module did not run. Check file timestamps.
4. For each failure: open the XML `<failure>` message, decide **pre-existing** (fails on the baseline too) or
   **regression** (new), and fix regressions before reporting.
5. Compile the desktop modules even when they have few tests:
   `./gradlew :editor-desktop:compileJava :engine-desktop:compileJava`.
6. Record in the report: each command, its exit code, per-module tests/failures/errors/skipped, and the
   before/after delta.

Never report a command that was not run. Never disable, `@Disabled`, or weaken a test to get a green build.
