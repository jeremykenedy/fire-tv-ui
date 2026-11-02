# Opt-in typed presentation migration

This module maps reviewed maintained legacy settings into real native preference stores. It is unintegrated and never runs from startup. It does not establish complete production upgrade parity.

## Public interfaces

`LegacyPresentationMapper.plan(PreferenceSnapshot legacy, PresentationMappingContext identities, Map<String, PreferenceSnapshot> nativeBefore)` returns immutable `before`, `after`, `patches`, `mappedLegacyKeys` and `unmappedLegacyKeys`. `patches` contains only mapped native fields and excludes the launcher marker and private data. Unknown legacy/native values remain intact in the complete before/after snapshots. Existing conflicting native choices reject the entire plan. Missing source choices do not generate native defaults.

`PresentationMappingContext` carries explicit verified legacy tile-to-component, feed-hash-to-native-feed, item/series-hash-to-native-item, approved icon URI-to-choice, input-alias-to-platform-ID and installed advertised icon-pack relationships. These relationships cannot come from displayed names, fuzzy matches or guessed hash reversal. Relative component classes canonicalize to inventory identities. Missing identities, conflicting aliases, duplicate ranks and unsupported values reject rather than clamp or discard.

`LegacyPresentationMapper.ownedNativeKeys(legacyKeys, identities)` identifies reviewed native keys for saved-setup absence/removal handling. It excludes unrelated defaults and private state. Saved setups and portable import use their own presentation-only validation/journal; they must reject unsupported mapped domains instead of treating a partial plan as full compatibility.

`PresentationTransaction.apply(store, plan)` applies all mapped files with a durable private before-journal, readback, source-marker-last ordering and verified whole-operation rollback. The overload accepting `PresentationSnapshot before, after` supports a separately validated plan. `recover(store, snapshot)` restores exact original typed contents. `PresentationSnapshot` has a bounded versioned checksummed codec; unsupported files, malformed data, corruption and excessive aggregate size reject.

`AndroidPresentationStore(context)` uses only the supplied application's own preferences and `noBackupFilesDir/presentation-migration-v1.snapshot`. Recovery persists and verifies before any preference commit, remains retained after success/failure, and cannot be overwritten. Integration must quiesce every other preference writer and explicitly resolve existing recovery before a new migration. There is no recovery deletion or automatic application hook. The adapter must remain an explicit one-time migration/recovery tool, not an unreviewed bulk-clear UI action.

`LegacyJson.object(text)` is a shared strict JSON parser with duplicate-key, nesting, node, text and number bounds. JSON numbers are BigDecimal, not silently coerced integers. `LauncherMigration.portableTypes()` exposes the earlier narrow reviewed legacy portability whitelist for the backup lane. These APIs never log values or open account storage.

## Actual mappings

| Maintained legacy contract | Native destination |
| --- | --- |
| `apps_per_row`, `app_max_rows`, `app_scroll_direction` | `native_app_grid` columns, rows and horizontal |
| Comma-separated `display_order` | Verified canonical component order |
| `app_visibility.hidden` | Per-component hidden list; unrelated existing entries retained |
| Border/fill `icon_focus_style`, custom colors, `white_border` | `native_focus` independent fill/border switches and RGB, retaining new speed/growth/opacity defaults |
| `featured_rotation_seconds`, hashed feed timer overrides | `native_cards` global/per-feed timers, including Manual zero |
| Both explicit `hide_watch_next` and `enable_widgets` | Native BOTH/CARDS_ONLY/WIDGETS_ONLY mode; both disabled rejects until representable |
| Ranked `featured_feed_order` | `native_layout` stable channel slot order, retaining existing mode/widget entries |
| Ranked `featured_card_order` | Native per-source/per-feed title ordering |
| `hidden_featured_cards` | Verified individual/series hides, preserving independent hidden episodes |
| `app_visibility.customIconUri`, `factory_icon_packages` | Reviewed per-tile custom or factory choices, including exact platform inputs |
| `icon_pack_package` | Exact supported installed package in `native_icon_packs`; explicit blank means original |
| `input_names` | Exact platform ID name/known fields in `native_inputs`, preserving factory metadata |
| Base/overlay style, frozen, speed, intensity, brightness, opacity | Independent `source_appearance` layers; no clamping |
| `menu_background_palette`, `menu_overlay_palette` | Reviewed first-party palette IDs, including explicit empty default |
| Wallpaper/video references | Exact retained native URI strings without opening media |
| Verified active `mode=customv` with content URI | Native video wallpaper mode; unsupported active sources reject |

The mapping source marker is `source_ui_presentation_mapping_version = 1`. It identifies this bounded mapping schema, not completion of every production migration gate. Original `launcher_prefs` keys are never deleted. The older `LauncherMigration.prepare`/`MigrationTransaction` marker-only APIs remain a separate typed preservation foundation and do not replace the actual mapper.

## Verification

`python3 native/tests/migration/run_tests.py` uses temporary output directories, strict host Java lint and sanitized fixtures, followed by Java 8/API 36 compilation of the complete module and actual pure target models. It proves observable mapped model values, exact unknown/type preservation, absence/default distinctions, stable component/input identities, source/item/feed ordering, independent series/item hiding, unsupported dimension/identity/conflict rejection, malformed JSON bounds, journal integrity, failed commits at every changed file, failed rollback, stale-source detection and simulated process interruption followed by new-store-instance recovery. No production data, accounts, devices or shared app build outputs are accessed.

The isolated native fixture builds with `python3 native/tests/migration/fixture/build_fixture.py`. It uses only declared source and its own test signer; it does not read the shared app build or production key. APK: `fixture/.build/migration-crash-proof.apk`. Its package and instrumentation target are both `com.jeremykenedy.firetv.migrationproof`, with no Activity, Home, accessibility or device permissions. The fixture asserts that exact package before writing its own private synthetic data.

With an assigned emulator lease, install that test APK and run two separate invocations for each `after` value 1, 2 and 3:

```
adb -s SERIAL shell am instrument -w -e phase interrupt -e after 1 com.jeremykenedy.firetv.migrationproof/com.jeremykenedy.firetv.ui.nativeapp.migration.MigrationCrashInstrumentation
adb -s SERIAL shell am instrument -w -e phase recover com.jeremykenedy.firetv.migrationproof/com.jeremykenedy.firetv.ui.nativeapp.migration.MigrationCrashInstrumentation
```

The first command intentionally kills only the fixture process after a real synchronous preference commit and must not finish normally. The second must return `restored=true`, a distinct current PID and the interrupted write number. Repeat with `after=2` and `after=3`; each interruption phase resets only fixture-owned synthetic files. Remove the fixture APK after proof. No emulator execution has occurred in this lane. This prepared fixture is not physical-TV or corrective-build recovery proof.

## Remaining gates

- Complete domain inventory and coordinated consumer mappings. Every unmapped key remains available as typed original data; do not label it consumed.
- Default focus style delegates visual defaults to the inherited renderer. Its dormant custom colors and style remain explicitly unmapped; border/fill mappings do not prove exact default visual parity.
- `app_order` and `folders` have no maintained reader/writer contract. Conflicting test sentinels do not establish a format. Preserve opaque values. Legacy lock encoding and PINs must remain untouched and private.
- Mixed app/input `display_order` cannot fit the current component-only app-grid order and rejects until shared ordering supports both. Source-global `hideWatchNext`, a lone `hide_watch_next`/`enable_widgets` with unknown counterpart default, future-provider visibility and `channel_visibility` need deliberate persisted consumer support. Both flags explicitly disabled cannot be represented by the current three native content modes and reject.
- Native identity relationships must be inventoried, including unavailable apps/feeds. No automatic label-based mapping or feed-hash guessing is implemented.
- Custom icons require explicit reviewed assets or prepared native private images. The full legacy 48-icon catalog and all document providers are not certified by the eight current curated mappings.
- `mode=custom` is demonstrably used with a gradient in the maintained simple setup; do not guess it means an image wallpaper. Active private file video validation, arbitrary inherited gradient modes and unsupported sources remain unresolved. Missing references are retained and never pruned.
- The saved-setups lane owns legacy JSON version 1, declared-key absence/removal, icon-preservation, missing-media handling and `.image` retention. The backup lane owns safe portable filenames/JSON/SAF flows. This mapper never rewrites those private files or exports credentials.
- Package/signature/version verification, encrypted Plex connection/client identity/Keystore continuity, compatibility components/Toolkit/update intents, real process interruption, disk-full behavior, approved corrective-version recovery and all three physical TVs remain independent gates. No account, equipment, network, Home or production settings have been changed here.
