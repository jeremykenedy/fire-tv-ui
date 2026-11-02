# Original source app folders

Owned source: `nativeapp/folders/`. This module does not write app-grid, favorites, application visibility or installed-package state.

Register the nonexported activity `com.jeremykenedy.firetv.ui.nativeapp.folders.FolderActivity` in the source manifest. Settings can open the manager with an ordinary explicit Activity intent. Home can enumerate `FolderPreferences.load(context).folders` and use `FolderActivity.intent(context, folder.id)` to open a folder. Use `folder:` plus the exact folder ID as its focus/lock key. Name is presentation, never identity. Both folder entry and component dispatch pass through `LauncherLocks.guard`; the manager exposes folder lock controls using the shared lock module.

`FolderOptions` holds immutable ordered folders and ordered stable component membership. Public operations create, rename, remove, move, addApp, removeApp and moveApp preserve unrelated folders. Short Android component aliases such as `package/.Main` normalize to the same full component identity used by inventory and locks; duplicate short/full aliases collapse without changing member order. Missing applications are retained and shown as unavailable until explicitly removed. An app can belong to multiple folders. Removing a folder does not uninstall apps, remove favorites or change the ordinary grid. Limits are 100 folders, 500 members per folder, 80-character names and 256 KiB serialized data.

`FolderPreferences.STORE` is `native_folders`; keys are integer `schema_version=1` and string `folders`. JSON is an ordered array of objects containing `id`, `name`, and `components`. Public `decode`/`encode` validate native backups. Malformed or unknown-version data is kept and yields an unavailable screen with no destructive reset action. Saving commits the complete snapshot atomically. Do not infer legacy `launcher_prefs.folders` serialization from this new schema; original legacy migration remains separate and currently opaque.

Native controls: create button; Select opens folder; long-press folder opens rename/order/lock/remove controls; open folder offers add-app and folder controls; Select dispatches installed apps; long-press member opens move/remove controls. Manager Back restores folder opener focus; direct folder Back exits. Current folder and focused stable key survive Activity recreation, with lock reauthentication on reentry. Package inventory and artwork resolve on a worker executor.

Verification completed in this worker:

- `python3 native/tests/folders/run_tests.py`: pass. Behavior checks cover stable identity, rename, ordering, explicit member removal, unavailable member retention, independent folder membership, immutable snapshots, limits and invalid input.
- `python3 native/tests/folders/compile_sources.py`: pass against Android 36 using isolated temporary classes. Includes Activity, store, model, instrumentation and current shared locks dependencies.

Prepared native suite, not executed by this worker:

- After coordinator builds the source preview: `python3 native/tests/folders/build_native_test.py`.
- Instrumentation package `com.jeremykenedy.firetv.ui.folderproof`, runner `com.jeremykenedy.firetv.ui.nativeapp.folders.FolderInstrumentation`.
- APK `native/tests/folders/.build/folder-proof.apk`.
- Suite checks actual create/rename name dialogs using injected keyboard text and native Save controls, long-press/remove confirmation, real D-pad folder entry/Back opener focus, direct-folder route, recreation, missing member visibility, native serialized persistence, rename/order/remove snapshots, corrupt-data preservation and no changes to ordinary grid/favorites. It restores every original native-folder preference type and reports readback. It does not modify the lock store.

The suite seeds baseline folder data and separately creates, renames and removes a synthetic folder through actual native dialogs. Keyboard text uses instrumentation input events; dialog button discovery uses UI Automation without suppressing existing accessibility services. It has been compiled but not executed by this worker, so keyboard/dialog behavior remains unverified until the native operator runs it. Lock PIN prompts are covered by the separate fresh-store locks suite; this folder suite does not modify that private store. Real app playback, physical remote behavior, legacy upgrade migration and production acceptance remain unverified. Coordinator owns combined builds, device leases and integrated/native evidence. No device input, installation, APK build, commit, release or publication occurred in this worker.

Legacy evidence follow-up: `LEGACY-EVIDENCE.md` records exact source/schema/test references and why neither existing app-order sentinel establishes a production serializer format. Migration was instructed to retain unsupported legacy values opaquely rather than guessing.

Cross-module review follow-up: native second-member recreation proof now checks that saved focus does not reset to the first member. `FolderActivity` carries the saved key past the initial safe manager screen and through folder lock reauthentication. App-grid discovery uses the separately approved `saveDiscoveredOrder` helper, which preserves unrelated absent/explicit controls and rolls back a failed order write; ordinary full saves remain for explicit user grid changes. See `CROSS-REVIEW.md` for the reviewed launch boundaries and outstanding native proof.
