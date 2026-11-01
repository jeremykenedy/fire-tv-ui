from pathlib import Path
import os
import subprocess
import tempfile
import unittest
from java_runtime import json_runtime

ROOT = Path(__file__).resolve().parents[1]


class InputNameChecks(unittest.TestCase):
    def test_rename_reset_and_original_input_identity(self):
        jar = json_runtime()
        with tempfile.TemporaryDirectory() as temp:
            subprocess.run(['javac', '-cp', str(jar), '-d', temp,
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/InputNameValues.java'),
                            str(ROOT / 'tests/InputNameValuesTest.java')], check=True)
            subprocess.run(['java', '-cp', temp + os.pathsep + str(jar),
                            'com.jeremykenedy.firetv.ui.InputNameValuesTest'], check=True)
