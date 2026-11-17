# Grouped legacy widgets

`WidgetGroupPresentationPlan.prepare` prepares supported groups and content flags inside the presentation journal. Original launcher preferences stay intact. IDs, ordering, widget sizes and JSON bytes are retained. Missing or empty-string configuration uses the original four empty columns in the native target without adding or replacing the original source key. Explicit `[]` stays `[]` and displays four empty columns.

The original defaults for both content flags are false. A hidden content row maps to `NONE`. Otherwise enabled widgets map to `WIDGETS_ONLY`, and disabled widgets map to `CARDS_ONLY`. This preserves the original grouped layout replacing the ordinary card row. `BOTH` remains a separate native user choice. Conflicting existing native content modes or a visible singleton clock block migration.

Enabled migration supports empty-data DigitalClock and YearProgress records with their exact known fields, within the original four columns. Enabled Weather accepts only an empty location with its original unit. Populated-city enabled Weather, unknown types, unknown fields, malformed flags and configurations with more than four enabled groups remain blocked before journal publication. Their source data is preserved.

## Original source evidence

Read from the locally decoded maintained 1.17.1 recipe at `/Users/jeremykenedy/sites/.worktrees/fire-tv-ui-card-regressions/.build/decoded/smali`:

* `Q3/M7.smali:13254` and `Q3/M7.smali:13394`: both flag reads use false defaults.
* `Q3/T3.smali:2182` and `Q3/T3.smali:2228`: `j()` reads enabled widgets and `m()` reads the hidden-row flag.
* `Q3/T5.smali:1172`: the hidden-row condition skips the entire ordinary Home content branch. `Q3/T5.smali:1213` selects grouped widgets when enabled. `Q3/T5.smali:1655` calls the grouped renderer and skips the ordinary card branch.
* `Q3/ya.smali:21798`: the original grouped renderer iterates four columns and supplies empty groups for missing indexes.
* `Q3/T3.smali:412`: absent or empty-string configuration follows the default four-empty-group constructor branch.
* `Q3/W3.smali:164`: enabling widgets also clears the hidden-row preference through the original UI control.

`WidgetGroups.displayColumnCount()` and `withDisplayColumns()` provide the four-column display without changing stored records. The view and editor use the padded copy. The Home host must size the collection using `displayColumnCount()` and allow explicit zero saved groups to render those empty slots. Widget start/stop still belongs to the Home host.

## Verification

`python3 native/build.py --debug`, `python3 native/tests/widgetgroups/run.py`, Python compilation and `git diff --check` passed. Pure tests cover flag presence/defaults, exact source retention, conflicting target modes, unsupported-record rejection, padded columns and interrupted enabled journal publication with recovery and retry.

Fresh owned Google TV API34 emulator5624 passed `EnabledWidgetInstrumentation`: migrated clock/year Home rendering, native record focus and focus retention, hidden row, disabled card row, explicit native mixed mode, exact raw retention, four padded empty columns, and adding a clock through the padded-column native menu with Dpad input. The installed candidate APK bytes and all helper signers matched. Durable StateGuard capture, restore, independent verify and resolve all returned `VERIFIED` and `matches=true` with an empty original app baseline. Receipts: `/tmp/fire-tv-enabled-widgets-20261111/final/`. Earlier attempts are retained separately; they exposed native-menu timing and a proof fixture that overwrote discovered visibility. The final helper waits for the actual menu label and retains discovery choices between fixture phases.

This proof uses synthetic saved settings and a synthetic recommendation card. Actual original-to-independent production upgrade and physical TV acceptance remain separate qualification work. Populated-city enabled Weather is still blocked; replacing functioning original weather with a setup-required tile does not prove equivalent behavior.

## Approved empty Weather state

The original standalone Weather type is ordinal6 (`Q3/b9.1.smali:106`). Its renderer reads the per-record location at `Q3/pa.smali:1090`, passes it to its loader at `Q3/pa.smali:1194`, and branches null data to the circular progress state at `Q3/pa.smali:2058`. `Q3/oa.smali:143` checks location length and skips the request for an empty location. An empty city therefore has no functioning forecast to retain. The native tile deliberately replaces that indefinite spinner with an actionable exact-location prompt. This is the approved useful empty-state improvement, not a pixel-equivalence claim.

`EmptyWeatherMigrationTest` verifies all C/F and Small/Big combinations from migrated records. Three repeated empty refreshes produce `SETUP_REQUIRED` and zero intercepted connection openings. Only explicitly selecting and saving coordinates permits a connection attempt. The UUID and original empty location/unit fields remain unchanged; configured choices are stored per instance. The fake transport prevents live network requests during the test.

The expanded final native proof also verifies empty Weather's actual Home setup prompt, per-record setup routing through its menu, retained Celsius/empty coordinates and cancel without record or singleton changes. It checks explicit `[]` in full Home at width896dp, the visible fourth column at212dp, and Dpad addition there. All guarded restoration steps passed again. Receipt: `/tmp/fire-tv-enabled-widgets-20261111/empty-final/`. This run includes the root coordinator's agreed Home `displayColumnCount()` fix copied for verification; that shared file is intentionally excluded from this agent's commits.
