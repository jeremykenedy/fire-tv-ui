# Legacy folder and application order evidence

This review uses maintained source, typed schema and sanitized host tests only. It does not inspect inherited runtime bytecode, decoded resources, Smali or device private data. No format conversion is justified by the available evidence.

Verified references in this worktree:

- `app/preference-types.json:5` identifies `app_order` as String. Lines 41 and 42 identify `folder_lock_pin` and `folders` as String. This establishes outer preference types, not the serialized folder or app-order schema.
- `app/src/com/jeremykenedy/firetv/ui/SavedSetupSettings.java:22` and `:23` include `app_order` and `folders` in the presentation string whitelist. This class copies settings; it does not decode or create either payload.
- `tests/IconOverridesTest.java:31` seeds `app_order` with `app.two,app.one`; `:41` checks exact opaque preservation while an icon reset runs. `IconOverrides` never reads or writes `app_order`. The comma string is an unrelated-setting sentinel, not a serializer contract test.
- `native/tests/migration/MigrationTest.java:27` seeds `app_order` with a JSON array of fixture components. That suite validates typed recovery snapshots, not a legacy app-order parser. Its fixture does not supersede or verify a production format.
- `tests/SavedSetupsTest.java:31` and `:40` seed a synthetic `folder_lock_pin` solely to prove it is excluded from portable saved setups. No PIN format or authentication behavior can be inferred.
- Searches of maintained `app/src`, `tests`, `scripts`, typed schema and tracked fixture/setup files found no folder decoder, folder serializer, app-order decoder or app-order serializer. Git history path queries found no maintained `Folder*` or `AppOrder*` Java source.

The two incompatible app-order fixture representations must not be treated as alternative supported legacy formats. Without an actual maintained producer/consumer contract or a separately authorized sanitized export with verified identity semantics, keep both `folders` and `app_order` opaque in private recovery, preserve unknown values exactly and report migration as unmapped. Do not map labels or package names to guessed component identities. The migration worker owns this decision in the migration mapper and was informed directly.

The new original `FolderPreferences` native schema is independently documented in `INTEGRATION.md`. It is supported for new native snapshots and portable native presentation validation. It is not evidence of the old launcher format.
