#!/usr/bin/env python3
"""Isolated quick-action model regressions and Android SDK compilation; no device/build output access."""
from pathlib import Path
import os
import subprocess
import tempfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp'
SDK = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
           or Path.home() / 'Library/Android/sdk')
with tempfile.TemporaryDirectory(prefix='fire-tv-quick-actions-') as folder:
    work = Path(folder)
    subprocess.run(['javac', '-Xlint:all', '-Werror', '-d', work,
                    SOURCE / 'widget/quickactions/QuickActions.java', HERE / 'QuickActionsTest.java'], check=True)
    subprocess.run(['java', '-cp', work,
                    'com.jeremykenedy.firetv.ui.nativeapp.widget.quickactions.QuickActionsTest'], check=True)
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options',
                    '-bootclasspath', SDK / 'platforms/android-36/android.jar', '-d', work / 'android',
                    SOURCE / 'Pages.java', SOURCE / 'layout/LayoutItem.java', SOURCE / 'layout/WidgetProvider.java',
                    SOURCE / 'appgrid/AppInventory.java', *sorted((SOURCE / 'locks').glob('*.java')),
                    *sorted((SOURCE / 'widget/quickactions').glob('*.java')),
                    HERE / 'QuickActionsInstrumentation.java', HERE / 'QuickActionFixture.java'], check=True)
print('Quick actions provider, settings, resolver, private storage and model compile against Android SDK 36.')
