#!/usr/bin/env python3
"""Check selected-feed parsing, transport policy and SDK compilation in temporary output."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp'
ANDROID = Path.home() / 'Library/Android/sdk/platforms/android-36/android.jar'
with tempfile.TemporaryDirectory(prefix='selected-feed-') as output:
    core = [SOURCE / 'widget/feeds' / name for name in ('FeedSource.java', 'FeedParser.java', 'FeedClient.java', 'FeedSnapshot.java')]
    subprocess.run(['javac', '-d', output, SOURCE / 'migration/LegacyJson.java', *core,
                    ROOT / 'tests/feeds/FeedTest.java'], check=True)
    subprocess.run(['java', '-cp', output, 'com.jeremykenedy.firetv.ui.nativeapp.widget.feeds.FeedTest'], check=True)
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', ANDROID,
                    '-d', output, SOURCE / 'HomeDialogBuilder.java', SOURCE / 'layout/WidgetProvider.java', SOURCE / 'layout/LayoutItem.java',
                    SOURCE / 'migration/LegacyJson.java',
                    *[SOURCE / 'locks' / name for name in ('LauncherLocks.java', 'LockStore.java', 'LockState.java', 'LockResult.java')],
                    *sorted((SOURCE / 'widget/feeds').glob('*.java'))], check=True)
