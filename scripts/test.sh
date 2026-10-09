#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$TASK_ROOT"
mkdir -p .build/tests
javac -encoding UTF-8 -d .build/tests \
  app/src/com/jeremykenedy/firetv/ui/Screensavers.java \
  app/src/com/jeremykenedy/firetv/ui/TimeoutValues.java tests/ScreensaversTest.java
java -cp .build/tests com.jeremykenedy.firetv.ui.ScreensaversTest
python3 -m unittest discover -s tests -p 'test_*.py' -v
