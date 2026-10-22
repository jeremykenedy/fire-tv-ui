import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
from java_runtime import json_runtime


class PublishedUpdateChecks(unittest.TestCase):
    def test_stable_release_integrity_and_version_comparisons(self):
        jar = json_runtime()
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            context = folder / 'android/content/Context.java'
            assets = folder / 'android/content/res/AssetManager.java'
            assets.parent.mkdir(parents=True)
            context.parent.mkdir(parents=True, exist_ok=True)
            context.write_text('''package android.content;
public class Context { public android.content.res.AssetManager getAssets() { return null; } }''')
            assets.write_text('''package android.content.res;
public class AssetManager { public java.io.InputStream open(String path) { return null; } }''')
            sources = [ROOT / 'app/src/com/jeremykenedy/firetv/ui' / name for name in
                       ('ScreensaverCatalog.java', 'ReleaseUpdates.java', 'UpdateVersions.java')]
            subprocess.run(['javac', '-cp', str(jar), '-d', temp, str(context), str(assets),
                            *map(str, sources), str(ROOT / 'tests/ReleaseUpdatesTest.java')], check=True)
            subprocess.run(['java', '-cp', temp + os.pathsep + str(jar),
                            'com.jeremykenedy.firetv.ui.ReleaseUpdatesTest'], check=True)
