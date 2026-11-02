#!/usr/bin/env python3
"""Check only the Toolkit setting-command module in isolated host and SDK directories."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/com/jeremykenedy/firetv/ui/nativeapp'
MODULE = SOURCE / 'device/commands'
SDK = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
           or Path.home() / 'Library/Android/sdk')
with tempfile.TemporaryDirectory(prefix='fire-tv-setting-command-') as folder:
    work = Path(folder)
    subprocess.run(['javac', '-d', str(work), str(MODULE / 'SettingCommandPolicy.java'),
                    str(Path(__file__).with_name('SettingCommandPolicyTest.java')), str(MODULE / 'LegacySettingTransaction.java'), str(SOURCE / 'device/protection/LegacyDeviceProtection.java'), str(Path(__file__).with_name('LegacySettingTransactionTest.java')), str(SOURCE / 'device/screensavers/ScreensaverEntry.java'), str(SOURCE / 'device/screensavers/LegacyDreamSelection.java'), str(Path(__file__).with_name('LegacyDreamSelectionTest.java'))], check=True)
    subprocess.run(['java', '-cp', str(work),
                    'com.jeremykenedy.firetv.ui.nativeapp.device.commands.SettingCommandPolicyTest'], check=True)
    subprocess.run(['java', '-cp', str(work), 'com.jeremykenedy.firetv.ui.nativeapp.device.commands.LegacySettingTransactionTest'], check=True)
    subprocess.run(['java', '-cp', str(work), 'com.jeremykenedy.firetv.ui.nativeapp.device.screensavers.LegacyDreamSelectionTest'], check=True)
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options',
                    '-bootclasspath', str(SDK / 'platforms/android-36/android.jar'),
                    '-d', str(work / 'android'), *map(str, sorted(MODULE.glob('*.java'))),
                    str(SOURCE / 'device/HomeRoutingPolicy.java'), str(SOURCE / 'device/protection/LegacyDeviceProtection.java'),
                    str(SOURCE / 'device/screensavers/ScreensaverEntry.java'), str(SOURCE / 'device/screensavers/LegacyDreamSelection.java'),
                    str(SOURCE / 'device/screensavers/ScreensaverSystem.java'),
                    str(SOURCE / 'device/screensavers/ReviewedPreviews.java')], check=True)
    print('Setting command Android SDK compilation passed; no device or shared APK build used')
