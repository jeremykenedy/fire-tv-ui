# Holiday scene artwork provenance and integration

These three original scene candidates were created for this project with the built-in imagegen service on November 1, 2026. No source photographs, stock images, copied launcher art, existing fictional characters or external reference images were supplied. Exact prompts are in `prompts.json`; packaged paths, actual dimensions, sizes and SHA-256 values are in `assets.json`.

This describes creation provenance, not a claim of exclusive copyright or a separate third-party license. There is no stock attribution requirement attached to an imported third-party work because none was imported. The independent UI distribution license decision remains open under U33. This record does not select or change a repository or artwork license; applicable service terms still govern use of the outputs. Do not describe these as commissioned human paintings or licensed stock photographs.

The prompts requested a 3840 by 2160 composition. The service returned 1672 by 941 PNG files. The packaged files are the untouched returned bytes, approximately 16:9. No 4K resolution claim is made. A larger final export or regeneration and owner quality review remain open if these files do not meet target-TV detail requirements.

## Artwork review

All three returned images were opened and inspected before packaging. Halloween visibly contains autumn foliage, a cottage, illuminated carved pumpkins and three small costumed characters. Christmas visibly contains snow, decorated evergreen, lights and gifts. Winter visibly contains snowy mountains, trees and a frozen river without Christmas decorations. The scenes reserve a quieter upper center for content but their lower foreground contains detail. Home cards may obscure that detail; review the actual composition on the native launcher before accepting it.

These are intentional still compositions. Animation is supplied by the independently selected transparent overlay. Halloween now also has an original derived clean landscape and a six-frame transparent character sheet. The independent native animation is described below; its moving output and visual quality remain pending review. Christmas and winter still rely on the separate seasonal overlay, and their complete animated scene acceptance remains open. The coverage table retains all 41 approved recipes with unfinished work explicit. The three candidates do not fulfill the inclusive holiday collection or its optional illustrated icon backlog.

## API contract

`HolidayScenes.ALL` exposes only the three actual scene candidates. `find(id)` returns null for unsupported scene identities. Names use the `holiday_scene_` drawable prefix and the `drawable-nodpi` directory so Android density scaling does not multiply decode size.

`HolidaySelection.apply(SharedPreferences, id, Mode)` accepts the appearance preference file supplied by the caller. Scene mode writes only `scene`; overlay mode writes only `overlay`; combined mode writes both in a single commit. It preserves wallpaper image/video references, procedural base, custom motion/density/opacity and unrelated choices. Failed commits restore the touched keys and report failure. Clearing a scene belongs to Appearance settings and must reveal the retained owner wallpaper.

`HolidaySceneLoader.load(Context, Scene, width, height, Callback)` resolves only catalog artwork and returns a cancellable request. It uses application context, one decode worker, a queue of four, a 16 MiB cache and decode bounds limited to 1920 by 1080 requested pixels. Power-of-two sampling may produce a smaller bitmap than the view. It checks source dimensions before decoding and disables density scaling. Callbacks run on the main thread, including cache hits and failure. Failure supplies null so the caller retains its background. Cancel on detach or scene change; never load per frame. Bitmaps are cache-owned and callers must not recycle them.

This is a 1080p decode cap, not a 4K native renderer acceptance claim. The consuming layer should draw proportionally, avoid repeated loading from onDraw, preserve its original background while an image is pending, and place the transparent overlay after the scene and before readable content.

## Verification and remaining native acceptance

Host checks exercise all catalog scenes and all three apply modes, exact preservation of owner wallpaper and custom values, unsupported input, and failed commit rollback including initially absent keys. Asset checks compare packaged bytes and PNG dimensions to provenance. The module compiles against Android API 34. These checks establish behavior and packaging, not visible native rendering or frame performance.

Before delivery, verify native full-screen preview and Home for scene-only, overlay-only, combined and motion-off modes. Verify settings overlay visibility independently from the base. Test both bright and dark personal wallpapers and return to them after clearing a scene. Record D-pad responsiveness, lifecycle pause/resume, rapid scene changes, decode failures and cancellation. Check physical TV readability, scene detail, snowfall depth and sustained motion separately from emulator behavior. Owner visual acceptance remains pending.

## Halloween character animation continuation

Two additional packaged resources are derived from the original project Halloween scene using the built-in imagegen edit workflow. `holiday_scene_halloween_clean.png` removes the three figures while retaining the landscape. `holiday_characters_halloween_walk.png` is a six-frame sheet arranged as three columns by two rows with real alpha transparency. Both returned files were inspected. The clean plate is 1672 by 941; the sheet is 1774 by 887, so native source rectangles use proportional integer cell boundaries rather than assuming the requested dimensions. Source prompts are in `animation-prompts.json`. A separate extracted single-pose group was explored but is not packaged or referenced.

`HolidaySceneView` is the complete native scene surface. `setScene(Scene, animated, speed)` and `setResumed(boolean)` are the integration API. It renders the original still while animation layers load or if either fails, and always uses that still when motion is off. When both animation layers are available it draws the clean background and the selected transparent gait frame. Characters follow a 15 to 45 second receding path with an intentional fade before repetition. Six pose frames advance every 80 to 220 milliseconds according to speed; subtle radial pumpkin light changes run on the same timeline. This is an authored sprite gait candidate, not a claim of articulated 3D characters or accepted motion quality.

The view schedules at most one Choreographer callback at a time with a 32 millisecond delay and bounds timeline advance to 100 milliseconds after a delayed frame. It removes callbacks when detached, hidden, unfocused, paused or motion-off. It preserves the last timeline position while paused and resets the time reference on resume. It cancels pending asset requests on scene/size changes or detach. The draw path reuses paints and rectangles and performs no asset decode or new bitmap allocation. Static fallback, clean plate and sheet may all be retained by the active view; the 16 MiB loader cache is separate from these active references. Do not interpret the cache limit as the total process graphics-memory budget.

The loader additionally exposes `loadAnimationBackground` and `loadCharacters`, resolving only resource names from catalog entries. They retain the existing bounded worker, queue, source-dimension checks and main-thread cancellable callback contract.

Three host checks now pass: selection/preservation/rollback; scene and animation asset bytes/provenance; and timeline speed, all six frames, fades, wrap bounds and delayed-frame clamping. All holiday sources compile against API 34. Native render and lifecycle checks remain pending.
