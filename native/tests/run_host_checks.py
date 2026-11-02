#!/usr/bin/env python3
"""Run the reviewed source-preview host suites without devices or production adapters."""
import argparse
from contextlib import contextmanager
import os
from pathlib import Path
import subprocess
import sys
import tempfile

NATIVE = Path(__file__).resolve().parents[1]
SUITES = (
    'focus/run.py', 'appgrid/run.py', 'cards/run.py', 'layout/run.py',
    'providers/run.py', 'menu_transition/run.py', 'migration/run_tests.py', 'folders/run_tests.py',
    'locks/run.py', 'inputs/run.py', 'icons/test_provenance.py',
    'appearance/run.py', 'setups/run_tests.py', 'backup/run.py',
    'network/run.py', 'protection/run.py', 'screensaver_admin/run.py',
    'updates/run.py', 'plex/run.py', 'worldclock/run.py', 'timers/run.py',
    'notes/run.py', 'watchlist/run.py', 'astronomy/run.py', 'quickactions/run.py',
    'reminders/run.py', 'photos/run.py', 'breathing/run.py',
    'release/test_build.py', 'state_guard/run.py',
    'accessibility/run.py', 'allapps/run.py', 'ambient/run.py', 'calendar/run.py',
    'commands/run.py', 'dailycontent/run.py', 'discovery/run.py', 'feeds/run.py', 'history/run.py',
    'livetv/run.py', 'nowplaying/run.py', 'puzzles/run.py', 'radar/run.py',
    'status/run.py', 'uv/run.py', 'weather/run.py', 'remotebuttons/run_host.py',
    'layoutschedule/run_tests.py', 'yearprogress/run.py', 'updateblock/run.py', 'statusbar/run.py', 'widgetgroups/run.py',
)
DEVICE_TESTS = ('HomeRoutingPolicyTest', 'HomeSetupPolicyTest', 'HomeCommandPolicyTest')


def run(*command):
    subprocess.run([str(part) for part in command], cwd=NATIVE.parent, check=True)


@contextmanager
def compiled_classes(path):
    """Expose this build's classes to existing harnesses without replacing prior output."""
    expected = NATIVE / '.build/classes'
    if not path.is_dir() or not list(path.rglob('*.class')):
        raise ValueError('Compile the unsigned preview before running host checks: missing classes directory.')
    created = False
    if expected.exists() or expected.is_symlink():
        if expected.resolve() != path.resolve():
            raise ValueError('native/.build/classes belongs to another build; use an isolated checkout.')
    else:
        expected.parent.mkdir(parents=True, exist_ok=True)
        expected.symlink_to(path.resolve(), target_is_directory=True)
        created = True
    try:
        yield
    finally:
        if created:
            expected.unlink()


def device_policies():
    # The existing device wrapper also compiles production adapters; this preview checks only its pure policies.
    source = NATIVE / 'src/com/jeremykenedy/firetv/ui/nativeapp/device'
    with tempfile.TemporaryDirectory(prefix='native-device-policies-') as temporary:
        models = ('HomeRoutingPolicy', 'HomeSetupPolicy', 'HomeCommandPolicy')
        run('javac', '-Xlint:all', '-Werror', '-d', temporary,
            *(source / (name + '.java') for name in models),
            *(NATIVE / 'tests/device' / (name + '.java') for name in DEVICE_TESTS))
        for name in DEVICE_TESTS:
            run('java', '-cp', temporary, 'com.jeremykenedy.firetv.ui.nativeapp.device.' + name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classes-dir', required=True, type=Path,
                        help='Classes from this unsigned preview build, normally OUTPUT/.build/classes.')
    args = parser.parse_args()
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
               or Path.home() / 'Library/Android/sdk')
    android = sdk / 'platforms/android-36/android.jar'
    compatibility_sdk = Path.home() / 'Library/Android/sdk/platforms/android-36/android.jar'
    if not android.is_file() or not compatibility_sdk.is_file() or android.resolve() != compatibility_sdk.resolve():
        parser.error('Existing host harnesses require the same Android 36 SDK at ~/Library/Android/sdk.')
    with compiled_classes(args.classes_dir):
        device_policies()
        for suite in SUITES:
            print('Host checks: ' + suite, flush=True)
            run(sys.executable, NATIVE / 'tests' / suite)
    print(f'All {len(SUITES) + 1} reviewed host suites passed. Native/device behavior is a separate qualification gate.')


if __name__ == '__main__':
    main()
