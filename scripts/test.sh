#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$TASK_ROOT"
mkdir -p .build/tests
javac -encoding UTF-8 -d .build/tests \
  app/src/com/jeremykenedy/firetv/ui/Screensavers.java \
  app/src/com/jeremykenedy/firetv/ui/TimeoutValues.java \
  app/src/com/jeremykenedy/firetv/ui/SleepTimerOptions.java \
  app/src/com/jeremykenedy/firetv/ui/InputValues.java \
  app/src/com/jeremykenedy/firetv/ui/DiagnosticValues.java \
  app/src/com/jeremykenedy/firetv/ui/StorageValues.java \
  tests/StorageValuesTest.java \
  tests/DiagnosticValuesTest.java \
  tests/InputValuesTest.java \
  tests/ScreensaversTest.java
java -cp .build/tests com.jeremykenedy.firetv.ui.ScreensaversTest
java -cp .build/tests com.jeremykenedy.firetv.ui.StorageValuesTest
java -cp .build/tests com.jeremykenedy.firetv.ui.DiagnosticValuesTest
java -cp .build/tests com.jeremykenedy.firetv.ui.InputValuesTest
python3 -m unittest discover -s tests -p 'test_*.py' -v
