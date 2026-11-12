# Portable presentation and optional household backup

Entry point: `com.jeremykenedy.firetv.ui.nativeapp.backup.BackupActivity.open(Context)`. The integration owner registers this activity as unexported and adds the Settings route. Backup owns only `backup/` and `tests/backup/`; no Main/Settings/manifest/build tooling edits were made by this lane.

The preview offers Android document-picker export/import, validation and confirmation, both cancellation paths, bounded malformed/unknown-document handling and interrupted-transaction recovery. Export suggests the maintained `fire-tv-ui-backup.txt` filename; the chooser accepts `fire-tv-ui-import.txt` or another document name. No broad storage permission, Downloads scan or broadcast import exists.

`PortableBackup` v1 carries reviewed presentation stores with exact primitive/StringSet types and stored-presence semantics. The migration module supplies strict duplicate-key/depth/node-bounded JSON parsing. Input UTF-8 is strict; input/output are limited to 1 MiB. `PresentationState.validatePortable` supplies the shared whitelist, rejects unknown fields, local media dependencies and opaque unsupported legacy settings. Missing owned choices remove stored values; explicitly stored defaults remain explicit. Account/hardware/lock/private-file stores stay outside this boundary.

`BackupPayload` adds a separately identified optional household envelope. Export requires fresh per-operation checkboxes plus the existing local export permission for each selected notes/reminders store. Unselected private content is never captured. `HouseholdContent` validates the source schemas and always forces imported notes/reminders `summaryOnly=true`, `allowExport=false`; reminders additionally remain disabled until enabled locally. A document cannot activate consent or show household details. Import confirmation explicitly explains replacement of the included private documents. Base setup snapshots never automatically capture household content.

`BackupTransaction` adds a private AtomicFile pre-journal around shared presentation apply and selected household document writes. The complete before state is written and read back before mutation; private writes commit synchronously and verify readback. Failure restores both base presentation and selected household documents. Interrupted transactions expose explicit recovery, coordinate the shared presentation journal, and retain recovery if restoration is unverified. The binary pre-journal is private recovery only, never portable output. Other keys in the household stores are preserved. No raw notes, reminders or credentials are logged.

The original flat JSON backup is recognized and refused, rather than silently partially applied. Its replace-all semantics, legacy identity/media mapping and full domain coverage still need production migration gates. Portable personal media packaging is absent and explicitly refused; named local setups separately preserve their local choices. The widget boundary audit is `WIDGET-STORE-INVENTORY.md`; in-place preservation is distinct from portable inclusion.

## Verification

- `python3 native/tests/backup/run.py`: 36 checks passed (26 strict codec checks and 10 household-consent/privacy checks).
- `python3 native/tests/backup/compile.py`: real aapt2 resources plus API36 Java8 SDK compilation passed, including Activity, coordinator and native instrumentation, with temporary outputs only.
- `python3 native/tests/backup/build_native_test.py`: wrapper builds `tests/backup/.build/backup-proof.apk` against the existing compiled preview classes and debug key. It does not rebuild the app.
- Runner: `com.jeremykenedy.firetv.ui.backupproof/com.jeremykenedy.firetv.ui.nativeapp.backup.BackupInstrumentation`.

The test-only APK registers a fixed controlled document provider. Native instrumentation requests the ordinary SAF intents through controlled picker results and performs actual resolver file I/O. It covers base export/import, picker and confirmation cancellation, malformed documents, an explicit optional-notes export/import, imported consent/detail reset, a commit failure after private preference memory mutation, exact rollback and fixture restoration. It captures/restores presentation and original notes/reminders document keys in `finally`. The provider accepts only this test package or the preview caller and exposes no arbitrary path. It has no production manifest entry.

This lane compiled but did not run native instrumentation. The integration/device owner must record native results. The fixture proves controlled provider behavior when run, not every external document provider. Production migration, process termination during native writes, physical TVs and owner acceptance remain separate gates. No device/shared build/commit/push/release was performed by this lane.

## Native backup controls completion

The Backup and restore page now uses `SourceStartup.ready` and the existing startup redirect instead of checking the application package suffix. Production remains behind the integration owner's migration gate. An unresolved presentation or household import journal disables export and import and exposes the existing explicit Restore action. Recovery, validation and rollback remain mandatory.

The page keeps its existing native menu styling and original Settings route. Scope text describes launcher organization, card choices, appearance, private local data and original launcher backup incompatibility. Household export checkboxes reset after each completed picker selection, requiring a new selection for another export.

Shared `PresentationState` now exports validated Clock/Year widget groups and original supported widget records. Import replaces a supplied grouping, while older portable documents without a grouping retain destination groups. Unsupported or private widget record data rejects the operation rather than being removed. Native fixtures include typed card timing, hidden titles/series, title order and exact two-widget grouping, and change the destination before importing.

Original flat launcher backups remain recognized and refused. Portable export is not a full original backup replacement: local media, private connections, location, timer runtime and quick actions remain on the TV. Production identity/signing/upgrade and physical device acceptance belong to the integration owner.

Verification on November 11, 2026:

* `python3 native/tests/backup/run.py`: 26 codec and 10 household privacy checks passed.
* `python3 native/tests/backup/compile.py`: Android SDK compilation passed with real generated resources.
* `python3 native/build.py --debug` and `python3 native/tests/backup/build_native_test.py`: passed.
* Exact installed preview APK SHA256: `f86ca3e393f9724cf918f4387444d3ade3fd69c1a840906791afce65a248cddc`. Disposable debug signer SHA256: `60c7e4d9841b380bce6bfb0e751ccafd8f5a1afb41392c120a3640c8add21e91`. Package version `0.1.0-preview`, code 1.
* Native instrumentation passed actual controlled-provider export/import, exact widget/card presentation roundtrip, cancellation before and after document selection, malformed input, explicit native recovery, household consent reset and failed-commit rollback. Fixture restoration reported exact original presentation and household values.
* The uniquely owned API31 Android TV AVD `fire-tv-backup-controls-20261111`, serial `emulator-5620`, contained zero accounts and zero Keystore aliases. Guard case `backup-controls-receipt-20261111`, manifest `d25a194de23661e88d9b2c2ffaf6eb3b934fa458d802e8fbf13d9290db532d15`, completed capture, restore, independent stopped verification and resolution with `VERIFIED` and `matches=true` each time.
* Sanitized native proof output and screen capture: `/tmp/fire-tv-backup-controls-20261111/`. Screen capture uses only this disposable application's external files directory; remove that owned file separately after pulling it. No physical device, owner account, production signer or production installation was used.

A first test fixture used an invalid synthetic card source and failed before opening the screen. Its durable state guard was restored, independently verified and resolved before correcting the fixture. This was a test fixture error, not an accepted application result.

The same exact APK also passed the complete native proof at 1920 by 1080, 320dpi on the owned API31 Android TV AVD. The landscape capture was inspected with every action and scope label visible without clipping. Receipt directory: `/tmp/fire-tv-backup-controls-20261111/landscape/`. Guard case `backup-controls-landscape-20261111`, manifest `90be2cb894215569f4ce8e0cfda43a56a1a23c9361d36c91c4c82bcfd6b60af1`, completed capture, restore, independent stopped verification and resolution, each `VERIFIED` with `matches=true`.

The owned screenshot file was removed from the emulator, all three owned test/application packages were uninstalled, the emulator was shut down and the newly created AVD was deleted. All existing AVDs and physical TVs were preserved.

## Original launcher document compatibility

The existing Import presentation backup action now recognizes original flat launcher JSON documents up to 8 MiB through the strict duplicate/depth/node bounded parser. The reviewed original scalar/StringSet schema is applied before preparing any state. A review dialog lists included presentation choices. Any unsupported device, account, PIN, game, cache or unknown field produces a concrete unsupported section and offers Close only. The entire document is refused; supported entries are never silently applied from a blocked file.

Supported presentation imports use `SavedSetupManager.prepareOriginalBackup` with the existing verified platform identity inventory and migration mapper. Only included choices change. Omitted grid, cards, widget groups, colors, credentials and local locks remain. Personal URIs must already match the TV's selected source references, and mapped personal icons must already exist locally. No media file, new URI grant, network setting or permission is imported. Preparation is read only and repeated at confirmation under the shared presentation lock. `BackupTransaction.applyOriginal` uses the existing durable presentation journal, checked commits/readback and rollback.

Verification on November 11, 2026:

* 26 portable codec, 10 household privacy and 15 original codec checks passed.
* Saved setup host suite: 205 checks passed, including original preparation without mutation, included direct choices, omitted grid/color preservation, local lock preservation and refusal of device keys/new artwork references. Real Android SDK manager compilation passed.
* Source debug build and native proof APK compilation passed. Exact installed APK SHA256: `ae474b532f40063dd3d166d7202dc07a754ea9feb575d347688ec2f1eaac1554`, using the same disposable debug signer recorded above.
* API31 Android TV, 1920 by 1080: actual controlled SAF original-file import mapped grid, clock and icon radius choices; preserved omitted widget/card choices; a failed grid commit restored the exact previous presentation; a file with a sleep setting was wholly refused, showed the concrete unsupported key and offered no Import action. Existing portable/privacy/recovery cases also passed.
* Native review and refusal dialogs were visually inspected. Evidence: `/tmp/fire-tv-backup-controls-20261111/original/`.
* Durable guard case `original-backup-20261111`, manifest `40cb8b2a1e2019432e5a97327e6e6a5c1e9a56e2a5ec7686b20fbf2f92255f02`, captured an empty owner state with zero Keystore aliases; restore, independent stopped verify and resolve each returned `VERIFIED`, `matches=true`. Owned external screenshots were separately removed.

Full original backup parity remains open: ordinary original exports can include unsupported device and other nonpresentation fields and are refused as a whole. The selected local custom icon and weather widget city/unit compatibility cases additionally depend on the shared migration/inventory work; the receipt above qualifies the explicitly exercised original choices only.

## Qualified supported original presentation followup

The original import helper now uses the shared widget group presentation stage. Enabled Clock groups restore their original mode and exact records. Inactive Weather groups retain their original city and unit byte for byte. Partial imports preserve existing omitted groups, sizes, colors, card choices and unrelated hidden rows. A populated Weather group cannot bypass the active-widget migration gate by omitting its visibility fields. The known inactive original `overlay_mode=none` is retained; other overlay choices remain refused.

Icon capture, review and named setup restoration now enumerate actual artwork selections and nested `RetainedHomeChoices` artwork, while preserving `edited:*` Boolean metadata. A selected original personal icon requires the existing migration-prepared copy to match the selected source bytes through the read-only `AndroidLegacyIconMigration.verifyExisting` inventory. All mapping context fields, including ambiguous inputs, are retained. Neither review nor apply creates a missing copy or imports URI grants. Unrelated layout imports do not reopen an omitted historical source: the existing managed icon and raw historical reference remain intact if that old source has disappeared.

Final verification:

* 51 backup codec/privacy checks passed. Saved setup suite: 226 host checks and real Android SDK manager compilation passed. Source debug build and native proof APK build passed.
* Installed APK SHA256 independently matched `e42c3cab51af58068de127ef7692306fab1812b92d9ffb09d43659a31b230857`; native helper SHA256 `5cdc0879588e4f0603b08b6a81f3034d8dd38eb78929e7a9958506404e2aa06e`. The disposable debug signer remains `60c7e4d9841b380bce6bfb0e751ccafd8f5a1afb41392c120a3640c8add21e91`, version `0.1.0-preview`, code 1.
* The complete native controlled-SAF proof passed at 1920 by 1080 on API31 Android TV. It exercises original grid/clock/radius choices, enabled Clock groups, inactive Weather city/unit, inactive overlay, retained personal icon for an unavailable app, exact original/prepared image bytes, preservation after an omitted source disappears, refusal without recreating a missing prepared copy, failed-commit rollback, whole-file unsupported-device refusal, and the existing portable/privacy/recovery cases.
* Native review/refusal dialogs were visually inspected without clipping. Receipts and screenshots: `/tmp/fire-tv-backup-controls-20261111/original-supported-20261111/`.
* Guard case `original-supported-20261111`, manifest `dae02cae394cd9b84f8d5ef7c6642f5ff0349edaede8cb0893785540ba29c1a0`: capture, restore, independent stopped verification and resolution each returned `VERIFIED`, `matches=true`; no accounts or Keystore aliases were introduced.
* Owned external screenshots were removed, all three owned packages uninstalled, emulator 5620 stopped, and the newly created AVD deleted. Existing AVDs and physical devices were preserved.

The expanded proof first exposed the read-only icon API's use of a noncanonical app files path. The prepared copy existed and matched the source, but the path comparison rejected Android's storage alias. Shared fix `104a2f9` canonicalizes the trusted files parent consistently with preparation and retains target symlink rejection. Every failed run was durably restored, independently verified and resolved before retrying.

This qualifies the supported presentation cases above, not full original backup parity. Original files containing device controls, private/PIN settings, unknown settings or otherwise unverified presentation still refuse as a whole. A durable composite device/presentation transaction, broader media restoration, and currently gated presentation formats remain unqualified. Production signed-upgrade and physical-device acceptance remain separate integration gates.
