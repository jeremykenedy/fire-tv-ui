# Next native holiday acceptance contract

These are the next checks for the integrated source preview, not completed results. Use the coordinator's verified APK and exclusive emulator/device lease. Preserve and restore the complete appearance preference file and owner wallpaper grants. Never clear app data, change production wallpaper references, install an inherited binary or reboot a physical TV for this check.

## Integration boundary

BackgroundLayer owns the composition order: system/personal image or video, procedural base, HolidaySceneView, independent transparent overlay, readable content. It calls `setScene(HolidayScenes.find(options.scene), !options.baseFrozen, options.baseSpeed)` during refresh and `setResumed` with host lifecycle. HolidaySceneView handles its own size, focus, attachment and visibility. Overlay-only application does not select or alter a scene. Scene-only application does not change overlay or custom values.

## Isolated full-screen native preview

1. Record APK package/version/hash and actual output dimensions. Back up appearance values, including absent keys. Start with scene empty and both procedural layers off; confirm retained personal wallpaper is visible.
2. Select Halloween scene-only, motion off. After decode completes, inspect the complete original still for autumn trees, pumpkins, glowing carved faces and three costumed figures. Capture two settled frames at least two seconds apart. They should match when other UI/time-dependent elements are excluded.
3. Enable scene motion at default speed with independent overlay off. Wait three seconds for asset decode/fade-in, then record at least 60 seconds in full-screen preview. Confirm one trio, actual alternating pose frames, a smooth receding path and intentional fade/reset. There must be no duplicate figures baked into the moving plate, sprite-cell borders, dark alpha rectangles, clipping, discontinuous positioning or path motion across unsuitable terrain. Review the warm pumpkin light change without flashing. A functional pixel difference does not establish polished animation.
4. Verify minimum and maximum speed. The walk cycle and path should visibly change speed without a sudden position jump. Toggle motion off while characters are distant; the complete still should appear with no blank frame, and remain unchanged. Re-enable motion and check that the view remains responsive.
5. Exercise rapid scene changes across Halloween, Christmas, winter and Off during pending decode. Confirm no stale callbacks paint the wrong scene and that returning to the owner wallpaper works. After repeated changes, inspect native memory for growth and retained Activity references. Record actual memory limits and frame timing rather than reporting the cache size as total graphics memory.
6. Open a dialog, hide the view, pause/resume the Activity and navigate away/back. Confirm motion pauses while hidden/unfocused and resumes from its current timeline without a large jump. Check that callback/decode work does not continue indefinitely after detach.

## Home and settings composition

Repeat the recognizable-scene and motion checks with actual Home controls and focused tiles. Keep labels and focus readable while leaving the requested foreground figures/scenery visible. Test a full-screen native preview independently because Home cards can obscure the lower character path. Verify that settings content scrims preserve contrast without hiding the whole overlay.

Test scene-only, overlay-only, combined, motion-off and both layers Off. Overlay-only must preserve the complete owner image/video reference, wallpaper mode, scene and all custom speed/density/opacity/frozen values. Check snow and snowflakes separately on Christmas and winter, then snow over both bright and dark personal wallpaper with scene/base off. Verify scene identity and motion values survive saved-setup apply/restore without silently applying an overlay.

## Physical TV acceptance

After bounded native emulator checks pass, record sustained motion and readability on the target Fire TV at its actual resolution and viewing distance. Verify remote responsiveness while animation and recommendations are active. The current scene files are 1672 by 941, so detail at 1080p and 4K output needs explicit visual assessment. Owner acceptance of artwork and movement remains a separate gate. The remaining holiday scenes, scene-specific animated layers and optional illustrated icons remain open in COVERAGE.md.

## Runnable native fixture for the next batch

`tests/holidays/HolidaySceneInstrumentation.java` is a native Instrumentation entry point for the next test APK. It is beyond the frozen cda56466 acceptance batch. The integration owner must include the class and declare `com.jeremykenedy.firetv.ui.nativeapp.holidays.HolidaySceneInstrumentation` in the isolated proof APK targeting the verified preview package. No production activity or manifest change is needed for the scene renderer; the fixture opens the already registered BackgroundPreviewActivity and creates a temporary native dialog surface.

The fixture writes no appearance preferences. It snapshots the complete `source_appearance` map, uses direct scene-view configuration, closes its dialogs/activity and verifies the snapshot still matches. It checks visible Christmas sky movement away from tree lights, pause/resume, hidden-view and focus-loss stability, motion-off static equality, Halloween movement, rapid scene-switch recovery, winter snow movement, winter pause/still behavior, clearing to transparency and detach. Its image comparisons use native View drawing at up to 1280 pixels wide; this is bounded behavioral evidence, not full-resolution visual acceptance or a hardware compositor frame-rate measurement.

The fixture reports changed-sample counts and pass/fail in the instrumentation result Bundle. It does not claim that changing pixels prove polished snow, convincing gait or stable frame pacing. Record full-resolution on-device screenshots and sustained screen recordings separately for the scene-quality checks above. The coordinator owns building, installing, running, preservation and cleanup; this lane only compiled the fixture.

## Winter scene continuation checks

With scene winter selected and the independent overlay off, record at least 60 seconds at default speed. Confirm the separately authored river, mountains and frosted trees remain recognizable. Snow should have small distant flakes, distinct middle silhouettes and soft foreground flakes, with wind differing from Christmas. Low mist should follow the three river bands, retain landscape detail and drift continuously without obvious closed-path edges, hard bands, flashing or a visible reset. Verify there are no Christmas lights, presents, ornaments or decoration layers.

Test scene-motion Off and pause/resume separately from the independent overlay. Motion Off should restore the complete original snowy river still. Keep private wallpaper and custom effects unchanged, and repeat the saved-setup round trip. The fixture's winter sky pixel changes prove only movement; they do not establish convincing mist or natural snowfall. Native full-resolution recordings and physical TV review are still required.

## Hanukkah, Diwali, Eid al-Fitr and Lunar New Year

Select each scene in Backgrounds and overlays with independent overlay Off. Confirm the actual subject and native composition match the inspected original artwork, then record at least 30 seconds of default-speed light animation. Candle and lantern glows should remain on the artwork objects, with no whole-screen flashing, displaced halos or new foreground obstruction. Check minimum/maximum speed, scene motion Off, Reduced motion On, focus loss and pause/resume. Full original imagery must remain when animation is disabled.

The native fixture now checks moving pixels and stable stills for all four IDs (`hanukkah`, `diwali`, `eid_fitr`, `lunar_new_year`). It still requires inclusion in the isolated test APK and execution under an exclusive device lease. These four candidates extend the collection to seven actual scenes; remaining holidays and optional illustrated icons stay explicitly pending in COVERAGE.md.
