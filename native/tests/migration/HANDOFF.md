# Opt-in migration foundation

This module is unintegrated. Version 1 records a typed private recovery boundary and adds one `launcher_prefs` marker. It does not perform the production upgrade or map production preferences into the preview renderer's separate preference files.

`PreferenceSnapshot` accepts exact Android preference types, including unknown keys, Long, Float and StringSet. It makes defensive copies, preserves missing versus explicitly stored defaults, and uses a bounded versioned binary recovery format. Its data is private and must never be logged, uploaded, or treated as a portable backup.

`LauncherMigration.prepare(snapshot)` validates a bounded reviewed subset of maintained presentation contracts, retains every remaining supported value verbatim, and adds `source_ui_migration_version = 1`. Unknown values are retained without claiming their domain semantics are validated. Maintained keys missing from `preference-types.json`, including `menu_transition_style`, `app_scroll_direction`, `app_max_rows` and `custom_video_uri`, are accounted for. Existing 1 through 20 columns are retained because the maintained getter supports that range; the preview grid currently supports only 2 through 12. No value is silently clamped.

`LauncherMigration.portableValues(snapshot)` exports only its explicit small presentation whitelist. It excludes unknown keys, nested JSON, account and lock values, custom media URIs, equipment identities, Home routing, sleep, network and device controls. This deliberately incomplete whitelist is not the existing portable backup format or full saved-setup parity.

`MigrationTransaction.apply(store)` validates before any write, persists and verifies a complete private recovery first, rejects a changed source, commits synchronously, and checks readback. A failed commit can mutate memory; rollback replaces the complete previous snapshot and reports whether it was verified. Recovery stays available after both success and failure. Repeat application with the same completed marker does not overwrite recovery. `recover(store, snapshot)` is an explicit restoration operation and verifies the result.

`AndroidMigrationStore(context)` uses the supplied app context's own `launcher_prefs` and `noBackupFilesDir/launcher-migration-v1.snapshot`. It never opens another package's private data. It uses AtomicFile, bounded recovery reads and synchronous typed writes. Existing recovery is never overwritten. The eventual coordinator must quiesce all other preference writers; synchronizing this module alone does not block ordinary existing preference writes. It must also resolve retained recovery before permitting a new attempt. No startup, UI, manifest or build integration invokes these APIs.

## Verification

Run `python3 native/tests/migration/run_tests.py` from the worktree. It uses temporary compilation outputs, runs strict host Java lint and sanitized fixture regressions, and compiles the entire module against Android API 36 at Java source level 8. Fixtures cover exact types, unknown values, explicit defaults, retained order/hidden/icon/input data, missing media references, malformed and oversized data, durable-recovery failure, commit failure after mutation, readback mismatch, failed rollback, explicit recovery and a source changed before commit. No production data, accounts, devices or shared build outputs are read or changed.

The Android AtomicFile and SharedPreferences adapter has SDK compilation proof only. Native persistence, process interruption, disk-full and corrective-build recovery have not been exercised.

## Remaining parity gates

- Full maintained preference/domain inventory and validation, including all appearance, feed/timer, hide/series, folder/lock, icon/input, widget and device settings. Unknown keys currently survive as opaque typed values.
- Explicit conversion into `native_app_grid`, `native_focus`, `native_icons` and layout/card/widget stores. Stable identity conversion and conflicts must be verified with sanitized fixtures; unsupported legacy dimensions must not silently reset.
- Reading and validating `saved-setups/setups.json` version 1, its `keys` and `values` absence/removal semantics, 24-setup and media limits, icon-preservation behavior, missing-media rejection without setup deletion, and retained `.image` files. This module never opens, rewrites or prunes that directory.
- Existing portable backup filenames, JSON type conversion/import, complete safe presentation coverage and UI-driven opt-in import. The private binary snapshot is a separate recovery format.
- In-place package/signature/version verification, private encrypted Plex connection and client identity/Keystore continuity, and no equipment/network/account changes. This module never opens `plex-connection.enc` or accesses keys/accounts.
- Compatibility for `.Hra`, shortcuts, accessibility bindings, receivers, Toolkit and update intents, with a reversible component plan.
- Native emulator process-restart and interrupted-write proof, then all three physical TVs with owner settings preserved. A recovery snapshot alone does not prove an installable lower-version rollback; an approved corrective version path remains necessary.
