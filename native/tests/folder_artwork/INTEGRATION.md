# Folder artwork verification

The folder uses the maintained `FocusTileView.useAppArtworkPresentation(width)` API and `AppFocusViewport.attach(grid, scroll)` policy. The resting 16:9 artwork size is independent of selected growth and color/border style. Labels are separate nonfocusable children inside the stable app focus target. Both source manifests use the existing translucent panel theme for this Activity.

`FolderActivity.intent` captures the actual calling Activity decor at twentyfold downsampling, capped at 192 by 108 pixels. The bounded bitmap travels only in that Intent. Each FolderActivity renders an owned filtered copy and recycles that copy after clearing its ImageView on destruction. The Intent source remains available for recreation and is removed on final finish. Routes without a captured caller keep the prior page background fallback. No backdrop file or preference is stored.

Build the isolated preview, then run `python3 native/tests/folder_artwork/build_native_test.py`. Install only on an owned, reserved disposable emulator. The fixture adds twelve independent synthetic TV app aliases with an original vector banner. No production app identity, signing key or physical TV is involved.

Before each run, capture a durable guard for `shared_prefs,files,no_backup` using the fixture's `StateGuardInstrumentation`. Pass its case ID and manifest hash as `state_guard_case_id` and `state_guard_manifest_sha256`. Stop target Activities, then restore, verify and resolve the guard after every outcome.

The artwork runner enters the actual Home folder through remote Select. It holds initial package discovery to check stable loading positions, then uses the real installed Play Store banner for comparison with the stable reference. It checks actual selected scale, unchanged resting dimensions, independent shadow presets, border/color modes, external glyph clearance, Add apps, real app opening, member order/removal, folder controls, Back to the selected Home folder, missing members and exact manually adjusted multirow scroll restoration after recreation. Native screenshots establish the rendered state; elevation properties alone do not prove a visible shadow.

For a bounded rendering recheck after a text-only change, `visual_only=true` covers the actual Home folder entry, default dimensions, shadow/style captures and Back without repeating membership mutations.

Run the same runner with `large_text=true` after separately preserving and setting this owned emulator's system `font_scale` to 1.3. It selects the saved 120% text option and checks actual label metrics plus the bounded filtered backdrop. Restore and read back the original system setting separately from the app guard. This is one bounded existing accessibility case.

The included `FolderInstrumentation` retains the existing create, rename, remove, missing-member and manager/direct Back checks. Its control lookup accepts app artwork targets as well as ordinary Buttons. It does not change lock data or expand PIN behavior.

Copy the exact fixture `folder-*.png` files from the preview's external files directory to private delivery evidence, inspect them, and delete only those owned files. Keep screenshots, guard records, APKs and source-input verification outside the repository. Preview success does not establish production upgrade or physical TV acceptance.
