#!/usr/bin/env python3
"""Run actual-service continuity checks only on a fresh disposable emulator."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--candidate', type=Path, required=True)
parser.add_argument('--fixtures', type=Path, required=True)
parser.add_argument('--evidence', type=Path, required=True)
parser.add_argument('--kill-recovery', action='store_true', help='Kill only the owned app PID using the disposable userdebug image su binary.')
args = parser.parse_args()
if not re.fullmatch(r'emulator-\d+', args.serial):
    raise ValueError('Only an explicitly named disposable emulator is accepted.')
sdk = Path(os.environ.get('ANDROID_HOME') or Path.home() / 'Library/Android/sdk')
adb = sdk / 'platform-tools/adb'
out = args.evidence.resolve()
out.mkdir(parents=True, exist_ok=False)
target = 'com.jeremykenedy.firetv.ui'
proof = 'com.jeremykenedy.firetv.updatecontinuityproof'
packages = [target, proof, 'com.amazon.device.software.ota', 'com.amazon.venezia']

def call(*values, timeout=60):
    result = subprocess.run([str(adb), '-s', args.serial, *map(str, values)], capture_output=True, text=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout

def save(name, value):
    (out / name).write_text(value)
    return value

def settings():
    return {key: call('shell', 'settings', 'get', 'secure', key).strip() for key in
            ['always_on_vpn_app', 'always_on_vpn_lockdown', 'accessibility_enabled', 'enabled_accessibility_services']}

def vpn():
    return call('shell', 'dumpsys', 'connectivity')

def await_vpn(session):
    end = time.monotonic() + 10
    while time.monotonic() < end:
        state = vpn()
        address = '10.217.0.1/32' if session == 'Fire TV UI update protection' else '10.218.0.1/32'
        if any(line.strip().startswith('NetworkAgentInfo{') and address in line and 'VPN' in line for line in state.splitlines()):
            return state
        time.sleep(.2)
    raise RuntimeError('Expected active VPN session: ' + session)

def phase(name):
    value = save(name + '.txt', call('shell', 'am', 'instrument', '-w', '-e', 'phase', name,
                 proof + '/.ContinuityInstrumentation'))
    if 'status=PASS' not in value:
        raise RuntimeError('Native phase failed: ' + name)

if call('shell', 'getprop', 'ro.hardware').strip() not in ('ranchu', 'goldfish'):
    raise RuntimeError('Disposable emulator hardware required.')
installed = call('shell', 'pm', 'list', 'packages').splitlines()
if any('package:' + name in installed for name in packages):
    raise RuntimeError('Fixture package collision. Use a fresh emulator.')
baseline = {'settings': settings(), 'packages': installed, 'home': call('shell', 'cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')}
save('baseline.json', json.dumps(baseline, indent=2))
guard = None
resolved = False

def guard_operation(operation):
    call('shell', 'am', 'force-stop', target)
    values = ['shell', 'am', 'instrument', '-w', '-e', 'operation', operation, '-e', 'quiescent', 'true',
              '-e', 'roots', 'shared_prefs,files,no_backup', '-e', 'case_id', guard['case']]
    if 'sha' in guard:
        values += ['-e', 'manifest_sha256', guard['sha']]
    value = save('guard-' + operation + '.txt', call(*values, proof + '/com.jeremykenedy.firetv.ui.nativeapp.stateguard.StateGuardInstrumentation'))
    if 'status=VERIFIED' not in value:
        raise RuntimeError('Guard refused: ' + operation)
    return value

try:
    for name in ['provider-free-seed', 'continuity-proof', 'inert-ota', 'inert-store']:
        save('install-' + name + '.txt', call('install', args.fixtures / (name + '.apk')))
    guard = {'case': 'enabled-protection-api' + call('shell', 'getprop', 'ro.build.version.sdk').strip() + '-' + str(int(time.time()))}
    captured = guard_operation('capture')
    guard['sha'] = re.search(r'manifest_sha256=([a-f0-9]{64})', captured)[1]
    save('guard.json', json.dumps(guard))
    seed = save('seed.txt', call('shell', 'am', 'instrument', '-w', '-e', 'guard_case', guard['case'], '-e', 'guard_sha', guard['sha'], proof + '/.SeedInstrumentation'))
    if 'status=PASS' not in seed:
        raise RuntimeError('Seed refused')
    save('install-candidate.txt', call('install', '-r', args.candidate))
    phase('unconsented')
    phase('lifecycle')
    call('shell', 'am', 'start', '-n', target + '/.MainActivity')
    save('vpn-before-replace.txt', await_vpn('Fire TV UI update protection'))
    call('shell', 'am', 'start', '-n', proof + '/.CompetingActivity')
    save('same-package-replacement.txt', call('install', '-r', args.candidate))
    save('vpn-after-replace.txt', await_vpn('Fire TV UI update protection'))
    save('services-after-replace.txt', call('shell', 'dumpsys', 'activity', 'services', target))
    if args.kill_recovery:
        time.sleep(2)
        save('services-before-process-death.txt', call('shell', 'dumpsys', 'activity', 'services', target))
        if call('shell', 'getprop', 'ro.debuggable').strip() != '1':
            raise RuntimeError('Process-death proof needs the disposable userdebug image.')
        pid = call('shell', 'pidof', target).strip()
        if not pid.isdecimal():
            raise RuntimeError('Expected exactly one owned app process.')
        save('kill-command.txt', call('shell', 'su', '0', 'kill', '-9', pid))
        time.sleep(1)
        save('vpn-after-process-death.txt', await_vpn('Fire TV UI update protection'))
        new_pid = call('shell', 'pidof', target).strip()
        if not new_pid.isdecimal() or pid == new_pid:
            raise RuntimeError('Owned app process did not restart.')
        save('process-death.json', json.dumps({'before': pid, 'after': new_pid}))
    phase('compete')
    save('vpn-competitor.txt', await_vpn('Disposable competing VPN'))
    phase('competing-retained')
    save('vpn-after-retained.txt', await_vpn('Disposable competing VPN'))
    save('competing-package-replacement.txt', call('install', '-r', args.candidate))
    save('vpn-after-competing-replace.txt', await_vpn('Disposable competing VPN'))
    save('result.json', json.dumps({'status': 'PASS', 'scope': 'Actual service, explicit consent, Off/On, Appstore scope, same-version APK replacement, competing owner; synthetic completed startup, not legacy migration.'}))
except Exception as failure:
    save('failure.json', json.dumps({'status': 'FAIL', 'reason': str(failure)}))
    save('failure-connectivity.txt', vpn())
    save('failure-services.txt', call('shell', 'dumpsys', 'activity', 'services', target))
    raise
finally:
    if guard and 'sha' in guard:
        call('shell', 'am', 'force-stop', target)
        call('install', '-r', args.fixtures / 'provider-free-seed.apk')
        for operation in ['restore', 'verify', 'resolve']:
            guard_operation(operation)
        resolved = True
    if guard is None or resolved:
        for name in packages:
            save('uninstall-' + name + '.txt', call('uninstall', name))
        final = settings()
        save('final-settings.json', json.dumps(final, indent=2))
        if final != baseline['settings']:
            raise RuntimeError('System settings differ; preserve emulator for review.')
        home = call('shell', 'cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')
        if home != baseline['home']:
            raise RuntimeError('Home resolution changed; preserve emulator for review.')
print(out)
