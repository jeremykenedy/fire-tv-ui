#!/usr/bin/env python3
"""Capture a bounded native comparison against one explicitly identified installed APK."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import os
import subprocess
import time
import threading
import queue

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
PACKAGE = 'com.jeremykenedy.firetv.ui.preview'
MAIN = PACKAGE + '/com.jeremykenedy.firetv.ui.nativeapp.MainActivity'
PROOF_PACKAGE = 'com.jeremykenedy.firetv.ui.performanceproof'
PROOF_COMPONENT = PROOF_PACKAGE + '/com.jeremykenedy.firetv.ui.nativeapp.providers.PerformanceInstrumentation'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--expected-apk-sha256', required=True)
parser.add_argument('--source-ref', required=True)
parser.add_argument('--output', required=True, type=Path)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
adb_path = Path.home() / 'Library/Android/sdk/platform-tools/adb'

def adb(*command, binary=False):
    completed = subprocess.run([str(adb_path), '-s', args.serial, *command], check=True,
                               stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=40)
    return completed.stdout if binary else completed.stdout.decode()

def capture(name, *command):
    value = adb(*command)
    (args.output / name).write_text(value)
    return value

def pref_hashes():
    snapshot = adb('shell', 'am', 'instrument', '-w', '-e', 'snapshot_only', 'true', PROOF_COMPONENT)
    match = re.search(r'INSTRUMENTATION_RESULT: preferences_digest=([a-f0-9]{64})', snapshot)
    if not match:
        raise RuntimeError('Native preference snapshot failed: ' + snapshot)
    return match.group(1)

activities = adb('shell', 'dumpsys', 'activity', 'activities')
match = re.search(r'topResumedActivity=.*?\su0\s+([^\s}]+)', activities)
if not match:
    raise RuntimeError('Cannot identify foreground activity; no device mutation performed.')
foreground = match.group(1)
if foreground != MAIN:
    raise RuntimeError('Baseline requires foreground preview Main; found ' + foreground)
installed = adb('shell', 'pm', 'path', PACKAGE).strip().removeprefix('package:')
installed_bytes = adb('exec-out', 'cat', installed, binary=True)
actual_hash = hashlib.sha256(installed_bytes).hexdigest()
if actual_hash != args.expected_apk_sha256:
    raise RuntimeError('Installed APK differs from expected baseline; no device mutation performed.')
if adb('shell', 'pm', 'list', 'packages', PROOF_PACKAGE).strip():
    raise RuntimeError('Performance proof package already exists; preserve it and resolve ownership first.')
manifest = ROOT / 'dist/build-manifest.json'
build_manifest = json.loads(manifest.read_text())
if build_manifest.get('sha256') != actual_hash:
    raise RuntimeError('Local build manifest does not identify the installed APK.')
source_matches = {}
for name, digest in build_manifest['sourceInputs'].items():
    source = subprocess.check_output(['git', 'show', args.source_ref + ':native/' + name], cwd=ROOT.parent)
    source_matches[name] = hashlib.sha256(source).hexdigest() == digest
if not all(source_matches.values()):
    raise RuntimeError('Source ref does not match the artifact source manifest.')
(args.output / 'source-identity.json').write_text(json.dumps({'source_ref': args.source_ref,
    'all_inputs_match': True, 'input_count': len(source_matches), 'inputs': source_matches}, indent=2) + '\n')
proof = HERE / '.build/performance-proof.apk'
adb('install', '-r', '-t', str(proof))
metadata = {'preferences_before': None}
installed_proof = True
try:
    metadata = {'serial': args.serial, 'installed_apk_sha256': actual_hash, 'source_ref': args.source_ref,
                'foreground_before': foreground, 'started_utc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
                'model': adb('shell', 'getprop', 'ro.product.model').strip(),
                'api': adb('shell', 'getprop', 'ro.build.version.sdk').strip(),
                'os_build': adb('shell', 'getprop', 'ro.build.fingerprint').strip(),
                'preferences_before': pref_hashes(), 'startup': [], 'host_load_averages': os.getloadavg(),
                'limitations': ['Emulator measurements are not physical Fire TV performance.',
                                'am start -W reports Android activity launch timing, not fully loaded inventory.',
                                'D-pad workload duration includes instrumentation and deliberate 20ms pacing.',
                                'FrameMetrics describe this View window, not video FPS or physical input latency.',
                                'Synthetic 64 tiles and 8 cards do not prove large installed-package enumeration.',
                                'Cancellation covers local resource/cache delivery, not a stalled network/provider.']}
    adb('shell', 'am', 'start', '-W', '-n', MAIN)
    time.sleep(.35)
    for name, key in [('window_animation_scale', 'window_animation_scale'),
                      ('transition_animation_scale', 'transition_animation_scale'),
                      ('animator_duration_scale', 'animator_duration_scale')]:
        metadata[name] = adb('shell', 'settings', 'get', 'global', key).strip()
    capture('display.txt', 'shell', 'dumpsys', 'display')
    capture('package.txt', 'shell', 'dumpsys', 'package', PACKAGE)
    capture('memory-before.txt', 'shell', 'dumpsys', 'meminfo', PACKAGE)
    capture('gfx-before.txt', 'shell', 'dumpsys', 'gfxinfo', PACKAGE)
    (args.output / 'home-before.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    manifest = ROOT / 'dist/build-manifest.json'
    if manifest.exists():
        build_manifest = json.loads(manifest.read_text())
        if build_manifest.get('sha256') == actual_hash:
            (args.output / 'baseline-build-manifest.json').write_text(json.dumps(build_manifest, indent=2) + '\n')
    for kind in ('cold', 'warm', 'cold', 'warm'):
        if kind == 'cold':
            adb('shell', 'am', 'force-stop', PACKAGE)
        else:
            adb('shell', 'am', 'start', '-W', '-a', 'android.settings.SETTINGS')
        launch = adb('shell', 'am', 'start', '-W', '-n', MAIN)
        metadata['startup'].append({'kind': kind, 'android_am_start': launch})
        time.sleep(.35)
    adb('shell', 'dumpsys', 'gfxinfo', PACKAGE, 'reset')
    proof = HERE / '.build/performance-proof.apk'
    metadata['instrumentation_sha256'] = hashlib.sha256(proof.read_bytes()).hexdigest()
    process = subprocess.Popen([str(adb_path), '-s', args.serial, 'shell', 'am', 'instrument', '-w', PROOF_COMPONENT],
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    lines = []
    output_lines = queue.Queue()
    def read_output():
        for line in process.stdout:
            output_lines.put(line)
        output_lines.put(None)
    threading.Thread(target=read_output, daemon=True).start()
    deadline = time.monotonic() + 40
    try:
        while True:
            line = output_lines.get(timeout=max(.01, deadline - time.monotonic()))
            if line is None:
                break
            lines.append(line)
            if 'INSTRUMENTATION_STATUS: phase=workload_done' in line:
                capture('gfx-workload.txt', 'shell', 'dumpsys', 'gfxinfo', PACKAGE, 'framestats')
                capture('memory-workload.txt', 'shell', 'dumpsys', 'meminfo', PACKAGE)
        process.wait(timeout=max(.01, deadline - time.monotonic()))
    except (queue.Empty, subprocess.TimeoutExpired):
        process.kill()
        adb('shell', 'am', 'force-stop', PACKAGE)
        raise RuntimeError('Native performance workload exceeded the 40-second bound.')
    result = ''.join(lines)
    (args.output / 'native-performance.txt').write_text(result)
    metadata['instrumentation_passed'] = 'INSTRUMENTATION_RESULT: status=PASS' in result
finally:
    adb('shell', 'am', 'start', '-W', '-n', foreground)
    time.sleep(.2)
    try:
        metadata['preferences_after'] = pref_hashes()
    except Exception as failure:
        metadata['preferences_after'] = None
        metadata['restoration_error'] = str(failure)
    finally:
        if installed_proof:
            metadata['proof_uninstalled'] = 'Success' in adb('uninstall', PROOF_PACKAGE)
        adb('shell', 'am', 'start', '-W', '-n', foreground)
    capture('memory-restored.txt', 'shell', 'dumpsys', 'meminfo', PACKAGE)
    metadata['preferences_unchanged'] = metadata['preferences_before'] == metadata['preferences_after']
    after = adb('shell', 'dumpsys', 'activity', 'activities')
    resumed = re.search(r'topResumedActivity=.*?\su0\s+([^\s}]+)', after)
    metadata['foreground_after'] = resumed.group(1) if resumed else None
    time.sleep(1)
    (args.output / 'home-restored.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    (args.output / 'baseline.json').write_text(json.dumps(metadata, indent=2) + '\n')
print(json.dumps({'output': str(args.output), 'instrumentation_passed': metadata.get('instrumentation_passed'),
                  'preferences_unchanged': metadata['preferences_unchanged'],
                  'foreground_restored': metadata['foreground_after'] == foreground}))
if not metadata.get('instrumentation_passed') or not metadata['preferences_unchanged']:
    raise SystemExit(1)
