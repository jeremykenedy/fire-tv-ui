# Quick actions widget integration

Register `new QuickActionsWidgetProvider()` with the existing widget registry. Its stable slot is `widget:local:quick-actions`; it defaults hidden. No manifest activity, permission, remote feed, background work or provider credentials are needed by the product module. `configure(Activity)` opens the actual native list and editor. Empty is SETUP_REQUIRED; all targets missing or unsupported is UNAVAILABLE. Saved actions survive unavailable apps. Partial availability keeps the widget READY and labels missing actions.

The list supports 12 user-selected TV apps or HTTPS content links, stable UUIDs, names, edits, order and confirmed removal. Choosing an entry opens its action menu. Only the separately labeled Open now action executes it. Saving/editing/ordering never opens another app. There are no device power, equipment, OS toggle, broadcast, service, arbitrary intent, intent-extra or custom URI commands. Non-HTTPS schemes are explicitly unsupported. Content links must resolve to an enabled exported activity inside the selected app without a required permission; no other app or generic app-open fallback is substituted. This establishes an app-provided content handoff, not direct playback or entitlement.

`QuickActionLaunch.open` gates both the user's selected app component and the actual resolved link activity through `LauncherLocks.guard`, skipping the duplicate guard when identical. It resolves again after PIN entry and refuses changed destinations. Target removal/disabled state/link incompatibility produce a fixed unavailable message without logging links or exception details.

Configuration stays in private `noBackupFilesDir/quick-actions-v1.txt`. The file is atomic, versioned and bounded to 64 KiB; names are bounded to 40 characters and links to 2048 characters. It has no portable export or saved-setup import. Backup/setup workers were informed to exclude this file and retain it during presentation restores; the layout-owned widget visibility/order may still be captured. The editor rejects userinfo and URL fragments, warns against passwords/access tokens, and disables URL state saving/autofill. No URL is shown in the Home summary or logged. URL query strings can contain personal information, which is why this store stays local. Existing legacy favorites/deep links have no verified automatic mapping and are not claimed migrated.

## Verification

`python3 native/tests/quickactions/run.py` passes host behavior/security regressions with strict host lint, then compiles the full module, real lock dependencies, fixture and instrumentation against Android API 36 in a temporary directory. Cases cover stable identity/order/edit/remove, immutable snapshots, bounded action count, duplicate/invalid identities, malformed storage and unsafe URI schemes/userinfo/fragments. No shared build, data or device access occurs.

Native proof is prepared but was not run by this worker. After the coordinator builds the integrated preview, run `python3 native/tests/quickactions/build_native_test.py`, install its `.build/quickactions-proof.apk` only on the exclusively leased emulator, and run:

```
adb -s emulator-5582 shell am instrument -w com.jeremykenedy.firetv.ui.quickactionsproof/com.jeremykenedy.firetv.ui.nativeapp.widget.quickactions.QuickActionsInstrumentation
```

The proof APK includes an external exported fixture app with a deliberately narrow HTTPS intent filter. The suite refuses existing quick-action or lock storage, checks real supported/unsupported package-scoped resolution and native state/order/remove persistence, opens the actual configuration list without execution, observes separate external app and content-link screens, then creates a synthetic component lock and proves Back on its PIN dialog prevents dispatch. It restores its previously absent action file and lock directory, reporting readback. It uses DONT_SUPPRESS_ACCESSIBILITY_SERVICES. Run serially with the standalone lock suite, never concurrently. Uninstall the proof package and return to preview Home afterward.

This native fixture does not prove the Home visibility/settings route, keyboard entry/validation/edit/remove UI, every installed app's content-link handling, actual media playback, native polish or physical-TV behavior. The coordinator must verify the ordinary Widgets route, opt-in visibility, D-pad/Back, preview summary and editor behavior separately. No native runtime success is claimed until that proof runs.

No devices, shared APK build outputs, commits, pushes or releases were changed by this lane.
