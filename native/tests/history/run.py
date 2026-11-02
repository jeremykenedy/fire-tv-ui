#!/usr/bin/env python3
"""Check history source behavior and compile Android views without a device."""
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp'
ANDROID = Path.home() / 'Library/Android/sdk/platforms/android-36/android.jar'
sys.path.insert(0, str(ROOT.parent / 'tests'))
from java_runtime import json_runtime

with tempfile.TemporaryDirectory(prefix='history-test-') as folder:
    jar = json_runtime()
    repository = SOURCE / 'widget/history/HistoryRepository.java'
    subprocess.run(['javac', '-cp', str(jar), '-d', folder, repository,
                    ROOT / 'tests/history/HistoryRepositoryTest.java'], check=True)
    subprocess.run(['java', '-cp', f'{folder}:{jar}',
                    'com.jeremykenedy.firetv.ui.nativeapp.widget.history.HistoryRepositoryTest'], check=True)
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
                    '-d', folder, SOURCE / 'HomeDialogBuilder.java', SOURCE / 'layout/WidgetProvider.java',
                    SOURCE / 'layout/LayoutItem.java', SOURCE / 'migration/LegacyJson.java',
                    *[SOURCE / 'locks' / name for name in ('LauncherLocks.java', 'LockStore.java', 'LockState.java', 'LockResult.java')],
                    *[SOURCE / 'widget/feeds' / name for name in ('FeedLinks.java', 'FeedSource.java', 'FeedParser.java')],
                    *sorted((SOURCE / 'widget/history').glob('*.java'))], check=True)
