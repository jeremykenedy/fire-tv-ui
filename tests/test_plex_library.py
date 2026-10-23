from pathlib import Path
import subprocess
import tempfile
import unittest
from java_runtime import json_runtime

ROOT = Path(__file__).resolve().parents[1]


class PlexLibraryChecks(unittest.TestCase):
    def test_personal_library_parsing_and_server_credentials_stay_scoped(self):
        with tempfile.TemporaryDirectory() as folder:
            runtime = str(json_runtime())
            subprocess.run(['javac', '-cp', runtime, '-d', folder,
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/PlexValues.java'),
                            str(ROOT / 'app/src/com/jeremykenedy/firetv/ui/PlexLibrary.java'),
                            str(ROOT / 'tests/PlexLibraryTest.java')], check=True)
            subprocess.run(['java', '-cp', folder + ':' + runtime,
                            'com.jeremykenedy.firetv.ui.PlexLibraryTest'], check=True)
