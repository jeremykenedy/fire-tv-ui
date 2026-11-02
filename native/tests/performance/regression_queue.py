#!/usr/bin/env python3
"""Plan serial native proof; optionally rebuild test APKs after explicit classes readiness."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'
NAMESPACE = 'com.jeremykenedy.firetv.ui.nativeapp.'
# State-sensitive tests remain explicit rather than auto-discovering and executing every runner.
RECIPES = [
    ('focus', 'focus.FocusInstrumentation', 'focus-proof.apk', []),
    ('appgrid', 'appgrid.AppGridInstrumentation', 'appgrid-proof.apk', []),
    ('icons', 'icons.IconInstrumentation', 'icon-proof.apk', ['icons/.build/icon-pack-fixture.apk']),
    ('layout', 'layout.LayoutInstrumentation', 'layout-proof.apk', []),
    ('layout', 'layout.PermissionInstrumentation', 'layout-proof.apk', []),
    ('device', 'device.DeviceInstrumentation', 'device-proof.apk', []),
    ('help', 'help.HelpInstrumentation', 'help-proof.apk', []),
    ('cards', 'cards.CardControlsInstrumentation', 'cards-proof.apk', ['providers/.build/provider-fixture.apk']),
    ('cards', 'providers.ChannelRenderInstrumentation', 'cards-proof.apk', []),
    ('providers', 'providers.ProviderInstrumentation', 'provider-proof.apk', ['providers/.build/provider-fixture.apk']),
    ('cards', 'providers.HomeContentInstrumentation', 'cards-proof.apk', []),
    ('folders', 'folders.FolderInstrumentation', 'folder-proof.apk', []),
    ('locks', 'locks.LockInstrumentation', 'locks-proof.apk', []),
    ('inputs', 'inputs.InputInstrumentation', 'input-proof.apk', ['inputs/.build/input-fixture.apk']),
    ('appearance', 'appearance.AppearanceInstrumentation', 'appearance-proof.apk', []),
    ('network', 'device.network.NetworkInstrumentation', 'network-proof.apk', []),
    ('protection', 'device.protection.ProtectionInstrumentation', 'protection-proof.apk', []),
    ('screensaver_admin', 'device.screensavers.ScreensaverInstrumentation', 'saver-proof.apk',
     ['screensaver_admin/.build/saver-fixture.apk']),
    ('backup', 'backup.BackupInstrumentation', 'backup-proof.apk', []),
    ('setups', 'setups.SetupInstrumentation', 'setup-proof.apk', []),
    ('updates', 'updates.UpdatesInstrumentation', 'updates-proof.apk', []),
]
LATER_RECIPES = [
    ('notes', 'widget.notes.NotesInstrumentation', 'notes-proof.apk', []),
    ('reminders', 'widget.reminders.RemindersInstrumentation', 'reminders-proof.apk', []),
    ('worldclock', 'widget.worldclock.WorldClockInstrumentation', 'worldclock-proof.apk', []),
    ('astronomy', 'widget.astronomy.AstronomyInstrumentation', 'astronomy-proof.apk', []),
    ('timers', 'widget.timers.TimerInstrumentation', 'timer-proof.apk', []),
    ('breathing', 'widget.breathing.BreathingInstrumentation', 'breathing-proof.apk', []),
    ('photos', 'widget.photos.PhotosInstrumentation', 'photos-proof.apk', ['photos/.build/photo-fixture.apk']),
    ('quickactions', 'widget.quickactions.QuickActionsInstrumentation', 'quickactions-proof.apk', []),
    ('cards', 'cards.PlexCardsInstrumentation', 'cards-proof.apk', []),
    ('providers', 'providers.NotificationInstrumentation', 'provider-proof.apk', ['providers/.build/provider-fixture.apk']),
]
NOTES = {
    'focus': ['Requires fresh/default Fast duration; restore all native_focus values.'],
    'icons': ['Install reviewed icon-pack fixture; suite snapshots icon/focus preferences. Remove fixture afterward.'],
    'layout': ['Permission suite starts with READ_TV_LISTINGS revoked and leaves it granted. Restore original permission and flags afterward.',
               'Record native_layout/native_cards before fixture discovery; suites restore their owned preferences.'],
    'device': ['Capability proof may open native Settings or Factory UI. Return to preview Main; do not change system values.'],
    'providers': ['Grant ordinary READ_TV_LISTINGS only for suite; restore prior grant/flags afterward.',
                  'Producer suite removes TV rows in finally. Run operation=cleanup before removing producer/proof to remove exact fixture slots.',
                  'Notification runner is a separate queue item requiring exact notification-listener grant restoration.'],
    'cards': ['HomeContent injects temporary results, restores native_layout/native_cards and finishes activity.',
              'Renderer includes its stalled-pipe test provider inside proof APK; no separate producer needed.'],
    'folders': ['Restores all original native_folders types. Locks data stays untouched.'],
    'locks': ['Refuses existing owner noBackupFilesDir/launcher-locks storage. Never remove an existing store.',
              'Creates and removes only its fresh synthetic PIN/app/folder locks. Secure PIN windows prevent screenshots.'],
    'inputs': ['Uninstall input fixture, run proof with operation=cleanup, then uninstall proof to remove exact fixture input metadata.'],
    'appearance': ['Snapshot original custom base/overlay selections and referenced media; suite restores preferences.'],
    'network': ['Actual Settings Device submenu routes; canceled/unsupported export only, no connectivity changes.'],
    'protection': ['Suite restores exact keep_launcher_awake existence/value and checks sleep_timeout unchanged. No privileged grant.'],
    'screensaver_admin': ['Snapshot active/enabled dream and screen_off_timeout; suite must preserve them. Never grant WRITE_SECURE_SETTINGS.'],
    'setups': ['Uses isolated private fixture preferences/cache files, then removes them. No real owner setup deletion.'],
    'backup': ['Controlled document provider is inside backup-proof APK; confirms actual output/input, cancel/confirm/malformed and transaction rollback.',
               'Requires final SDK readiness plus rebuilt preview BackupPayload/BackupTransaction/HouseholdContent. Restores presentation and household document values.'],
    'updates': ['Empty feed only; must never download/install production APK or infer successful self-update.'],
}
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', required=True, type=Path)
parser.add_argument('--build-tests', action='store_true')
parser.add_argument('--shared-classes-ready', action='store_true')
parser.add_argument('--artifact-sha256')
parser.add_argument('--stage', choices=('initial', 'later', 'all'), default='initial')
args = parser.parse_args()
if args.build_tests:
    if not args.shared_classes_ready or not args.artifact_sha256:
        parser.error('Test builds require root classes-ready authorization and exact artifact hash.')
    artifact = ROOT / 'dist/fire-tv-ui-source-preview.apk'
    if hashlib.sha256(artifact.read_bytes()).hexdigest() != args.artifact_sha256:
        parser.error('Current preview artifact differs from the authorized hash.')
    manifest = json.loads((ROOT / 'dist/build-manifest.json').read_text())
    if manifest.get('sha256') != args.artifact_sha256 or not (ROOT / '.build/classes').is_dir():
        parser.error('Matching manifest and compiled shared classes are required.')

queue = []
built = {}
for priority, (module, runner, apk, fixtures) in enumerate(RECIPES + LATER_RECIPES, 1):
    stage = 'initial' if priority <= len(RECIPES) else 'later'
    directory = ROOT / 'tests' / module
    manifest_path = directory / 'AndroidManifest.xml'
    manifest = ET.parse(manifest_path).getroot()
    component = manifest.get('package') + '/' + NAMESPACE + runner
    declarations = [item.get(ANDROID + 'name') for item in manifest.findall('instrumentation')]
    declared = NAMESPACE + runner in declarations
    script = directory / 'build_native_test.py'
    if args.build_tests and (args.stage == 'all' or stage == args.stage) and module not in built:
        result = subprocess.run(['python3', str(script)], cwd=ROOT.parent, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, text=True, timeout=180)
        built[module] = result.returncode == 0
        args.output.parent.mkdir(parents=True, exist_ok=True)
        (args.output.parent / (module + '-build.txt')).write_text(result.stdout)
    state_notes = list(NOTES.get(module, []))
    if runner == 'cards.CardControlsInstrumentation':
        state_notes.append('Requires ordinary TV listings grant and provider fixture operation=publish before invocation; remove rows and exact fixture slots afterward.')
    if runner == 'device.screensavers.ScreensaverInstrumentation':
        state_notes.append('Success is INSTRUMENTATION_CODE: -1 with scenarios and no failure; no status=PASS field.')
    if runner == 'cards.PlexCardsInstrumentation':
        state_notes.append('Self-contained localhost PMS fixture refuses any existing Plex account/client identity; verify source_plex and test-only key/file restoration. No real account or playback claim.')
    if runner == 'providers.NotificationInstrumentation':
        state_notes.append('Snapshot exact listener grant list and producer POST_NOTIFICATIONS permission; grant only preview listener for isolated producer notifications, then restore. Suite removes only fixture notification and restores opt-in.')
    if module == 'quickactions':
        state_notes.append('Refuses existing quick-actions private file and launcher-locks directory; cleans only its fresh fixture data. Run serial with locks.')
    if module == 'photos':
        state_notes.append('Fixture supplies explicit SAF URI grants; suite restores selections and newly acquired fixture grants. Remove proof.photos.fixture and proof APK afterward.')
    queue.append({'priority': priority, 'stage': stage, 'module': module, 'component': component,
                  'declared': declared, 'build_script': str(script.relative_to(ROOT)),
                  'proof_apk': str((directory / '.build' / apk).relative_to(ROOT)),
                  'fixture_apks': ['tests/' + item for item in fixtures],
                  'build_passed': built.get(module), 'state_notes': state_notes,
                  'execution': 'Pending exact installed artifact verification and exclusive emulator-5582 lease.'})
later_queue = [item for item in queue if item['stage'] == 'later']
queue = [item for item in queue if item['stage'] == 'initial']
queue.append({'priority': len(queue) + 1, 'module': 'migration',
              'proof_apk': 'tests/migration/fixture/.build/migration-crash-proof.apk',
              'component': 'com.jeremykenedy.firetv.migrationproof/' + NAMESPACE + 'migration.MigrationCrashInstrumentation',
              'phases': [{'phase': 'interrupt', 'after': n, 'expected': 'fixture process death followed by recover'}
                         for n in (1, 2, 3)],
              'recovery': 'Run phase=recover after EACH interrupt; require restored=true, matching interrupted_after and distinct PIDs.',
              'cleanup': 'Uninstall only com.jeremykenedy.firetv.migrationproof.',
              'execution': 'Self-targeted isolated fixture. Never writes preview/production state.'})
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps({'serial': 'emulator-5582', 'artifact_sha256': args.artifact_sha256,
    'execution_authorized': False, 'builds_requested': args.build_tests, 'build_stage': args.stage, 'queue': queue, 'later_queue': later_queue}, indent=2) + '\n')
print(json.dumps({'queue': str(args.output), 'cases': len(queue), 'later_cases': len(later_queue), 'all_declared': all(item.get('declared', True) for item in queue),
                  'test_builds': built, 'device_actions': 0}))
if any(not value for value in built.values()):
    raise SystemExit(1)
