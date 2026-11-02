#!/usr/bin/env python3
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
project = root / 'src'
source = project / 'com/jeremykenedy/firetv/ui/nativeapp/device/updates'
sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or Path.home() / 'Library/Android/sdk')
android = sdk / 'platforms/android-36/android.jar'
with tempfile.TemporaryDirectory(prefix='legacy-update-tests-') as out:
    subprocess.run(['javac', '-Xlint:all', '-Werror', '-d', out, str(source / 'UpdateBlockPolicy.java'), str(source / 'UpdateBlockStatus.java'),
                    str(root / 'tests/updateblock/UpdateBlockPolicyTest.java')], check=True)
    subprocess.run(['java', '-cp', out, 'com.jeremykenedy.firetv.ui.nativeapp.device.updates.UpdateBlockPolicyTest'], check=True)
    subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options', '-bootclasspath', str(android),
                    '-d', str(Path(out) / 'android'), str(source / 'UpdateBlockPolicy.java'),
                    str(source / 'UpdateBlockService.java'), str(source / 'UpdateBlockStatus.java'), str(source / 'UpdateBlockActivity.java'), str(root / 'production-src/com/jeremykenedy/firetv/ui/ProtectionService.java'), '-sourcepath', str(project), str(project / 'com/jeremykenedy/firetv/ui/nativeapp/SourceStartup.java')], check=True)
    print('Update blocker service compiles against Android API 36; no device or network used.')
