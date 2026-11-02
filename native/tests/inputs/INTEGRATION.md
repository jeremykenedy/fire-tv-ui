# Source TV inputs

The coordinator registers `com.jeremykenedy.firetv.ui.nativeapp.inputs.InputsSettingsActivity`
and routes the TV inputs Settings entry to it. Add a narrow manifest VIEW query for
`content://android.media.tv` for vendor system viewer visibility.

`InputRepository.load(Context)` runs on a worker and returns `Snapshot.inputs`,
`capability` and a user-facing message. IDs are exact platform TvInputInfo IDs.
`InputViews.artwork(Context, InputEntry)` resolves factory/custom artwork on a worker.
`InputViews.tile(Activity, InputEntry, Drawable, Runnable)` creates a Home focus tile
with that supplied artwork and no image IO; the three-argument overload delegates
for compatibility.
Select attempts launch, hold opens input actions. The callback refreshes the Home
inventory after name/icon changes. Refresh Home on returning from the icon picker.
`InputViews.actions` also exposes those actions to Settings.

`InputActions.open` re-reads discovery/state and uses documented TvContract
passthrough/channel URIs. Only explicit, enabled, exported, permitted system
viewer activities are accepted. DeviceRoutes.openSystemIntent supplies the shared
persistent Home handoff exemption. STARTED means dispatched, not visible hardware
tuning. Unsupported viewers and unavailable/disconnected/removed inputs return
UNAVAILABLE. There are no Amazon private input-type commands, CEC controls,
equipment configuration writes or network changes.

`native_inputs` stores `known:<id>` Boolean, `factory:<id>` last platform label,
and optional `name:<id>` string. Custom names are 1 to 60 trimmed characters with
no controls. Known inputs persist when removed. Discovery denial shows retained
items as unavailable, never as confirmed missing. Factory-name reset only removes
that input's name. `input:<id>` is the artwork key independently of the label.
Factory-icon reset affects only that key. Migration must supply an explicit legacy
alias to exact platform ID mapping; labels are never guessed as identity.

Host check: `python3 native/tests/inputs/run.py`.

Isolated proof build: `python3 native/tests/inputs/build_native_test.py`. This writes
only tests/inputs/.build. The combined preview must first contain the source module
and activity. Under an exclusive emulator lease, install input-fixture.apk and
input-proof.apk from that directory, then invoke:

```
adb -s SERIAL shell am instrument -w com.jeremykenedy.firetv.ui.inputproof/com.jeremykenedy.firetv.ui.nativeapp.inputs.InputInstrumentation
```

The fixture is a real isolated TvInputService plus an untrusted URI viewer.
The suite checks platform discovery, retained missing inputs, denied discovery,
rejection of the untrusted viewer, the native rename dialog and rendered changed
label, factory-name reset, and exact input preference restoration. It never tunes
a hardware input. Uninstall input-fixture afterward, run the same instrumentation with
`-e operation cleanup` to remove only exact fixture input metadata, then uninstall
input-proof. This also handles fixture IDs discovered by Home before the suite
snapshot. These APKs are not product dependencies; cleanup preserves unrelated
input keys.

At implementation handoff the host check and isolated Android compile/build pass.
Native instrumentation has not run. Main-to-Settings route, icon picker, D-pad/Back,
activity recreation and actual physical input switching require the coordinator's
native validation. No production deployment, hardware claim or release follows
from this module build.
