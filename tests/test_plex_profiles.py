from pathlib import Path
import os
import subprocess
import tempfile
import unittest
from java_runtime import json_runtime

ROOT = Path(__file__).resolve().parents[1]


class PlexProfileChecks(unittest.TestCase):
    def test_authorized_roster_and_profile_credentials_stay_separate(self):
        android = Path.home() / 'Library/Android/sdk/platforms/android-36/android.jar'
        if not android.is_file():
            self.skipTest('Android 36 SDK is required for the connection types.')
        runtime = os.pathsep.join([str(json_runtime()), str(android)])
        source = ROOT / 'app/src/com/jeremykenedy/firetv/ui'
        with tempfile.TemporaryDirectory() as folder:
            subprocess.run(['javac', '-cp', runtime, '-d', folder,
                            *[str(source / name) for name in [
                                'PlexHomeUsers.java', 'PlexConnection.java',
                                'PlexSession.java', 'PlexFeedSettings.java']],
                            str(ROOT / 'tests/PlexHomeUsersTest.java'),
                            str(ROOT / 'tests/PlexProfileConnectionTest.java')], check=True)
            for name in ['PlexHomeUsersTest', 'PlexProfileConnectionTest']:
                subprocess.run(['java', '-cp', folder + os.pathsep + runtime,
                                'com.jeremykenedy.firetv.ui.' + name], check=True)
