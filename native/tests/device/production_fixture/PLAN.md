# Production-package candidate validation plan

This plan has not been executed. No production candidate was built, installed, selected as Home or granted accessibility access by this lane. The existing standalone callback fixture proof is narrower and remains valid separately.

## Build and ownership

The integration owner reviews native/production-src/AndroidManifest.xml, production-res XML and production-build.py first. The candidate preserves com.jeremykenedy.firetv.ui.MainActivity (alias to nativeapp.MainActivity), .Hra, .HomeRedirectService and .BackupReceiver. The root native/build.py and preview manifest are unchanged by this work.

Static checks:

```sh
python3 native/production-build.py --check
python3 native/tests/device/production_contract_test.py
python3 native/tests/device/run.py
```

After that review, the integration owner can run:

```sh
python3 native/production-build.py --review-candidate --output-dir /tmp/fire-tv-source-production-candidate
```

The output is fire-tv-ui-source-production-package-candidate.apk, package com.jeremykenedy.firetv.ui, version 0.1.0-source-candidate/code1, debug-review signer only. It is not an upgrade or release artifact. The script accepts no production signing key, publishes nothing and installs nothing. Temporary compilation uses only native/src, native/production-src, source resources and Android SDK36. Its own generated R remains under the existing source import namespace com.jeremykenedy.firetv.ui.preview; this is newly generated Java, not imported bytecode. The build manifest records every source/resource input, toolchain and compiled class.

## Reserve a fresh dedicated emulator

Do not use an emulator/profile that already contains com.jeremykenedy.firetv.ui. Current emulator5582 has an existing production-package app and must not be uninstalled/replaced to make this test fit. Do not use physical TVs. Reserve one operator and the exact serial/model/API before any installation or input. Never reboot or wipe a device for this test.

Record candidate hash/package/signer/version and inventory before installation. Capture only the exact Home role holder/default component, selected accessibility-service list and accessibility_enabled value/presence needed for restoration. Keep unrelated service entries. No shell command may erase all accessibility services as test setup. Preserve the current foreground component.

Install the candidate on the dedicated emulator. Before any OS service selection, prove that Home/default service selections remain unchanged and absent saved Home flags stay off. A manifest declaration is not consent or delivery proof.

## Exact ordered receiver contract

Use the same shell command form as Toolkit, with no action and no Intent package field:

```sh
adb -s SERIAL shell am broadcast --include-stopped-packages -n com.jeremykenedy.firetv.ui/.BackupReceiver --es operation home-preference-state
```

Success state data is exactly home-preference=unset, home-preference=on or home-preference=off, result0. home-preference-on/off/clear return result0 data ok and alter only fire_tv_ui_home_enabled. Clear restores absence. Explicit master off suppresses retained source per-mode values while Toolkit changes the OS route.

home-on/off return result0 data ok only after the requested OS route reads back correctly before and after the private-flag commit. On requires both original service components selected and accessibility_enabled1. Off requires both absent, even if unrelated services remain selected/enabled. These operations do not write secure settings, bind/unbind services or remove unrelated services. Full commits deliberately update the related legacy/source Home flags. A no-backup journal restores their exact prior boolean/absent states after a failed or interrupted commit. Unknown operations fail without mutation. Non-Home legacy BackupReceiver operations remain a separate parity gate and are not advertised by this Home-only implementation.

Exercise absent/on/off/clear, malformed saved-value rejection, route mismatch and full on/off using only sanitized fixture preferences. Capture result strings, private Home flags and OS route readback. Verify unrelated preferences/services remain unchanged. Simulate interrupted private commits with the host Store fixture first; a native process-death injection must run only on the dedicated candidate and preserve its journal before recovery.

## Prove the DUMP caller boundary

Build the separate unprivileged sender fixture:

```sh
python3 native/tests/device/production_fixture/build.py
```

It requests no permissions, shares no production UID and sends home-preference-on from its own app process. Install only on the reserved dedicated emulator, snapshot the target Home preference, then trigger:

```sh
adb -s SERIAL shell am broadcast -n com.jeremykenedy.firetv.ui.homepermissionfixture/.UnprivilegedSender
```

Expect result0 data permission-boundary-preserved and an unchanged target preference. Confirm the system denied the target receiver's android.permission.DUMP boundary; absence of a target receiver is not permission proof, so first establish a successful authorized shell state read. The source receiver requires explicit component identity and ordered delivery, permits the Toolkit null action, and relies on Android's manifest permission check for sender identity. It never trusts an Intent-supplied caller package or Binder.getCallingUid during asynchronous delivery.

## Real OS service and Home events

Only after a new explicit emulator lease authorizes OS selection, use native Accessibility settings or a bounded test harness to add the candidate's original service components while preserving all baseline selections. No code in the candidate enables itself. Before setting app flags, verify that even an OS-selected service with off/absent choices does not redirect Home. Then explicitly select Home mode through the sanctioned command/setup API after OS readback.

Exercise Home from a separate benign app, actual system Home, a Dream and the source Main. Record OS-dispatched key events/native foreground ActivityRecords and screenshots, not direct service callback invocations. Repeated Home must not recreate a visible Main, queue old launches or leave transient menus trapped. Toggle both modes off and verify the service relinquishes the key-filter request and leaves system Home available. Only .Hra may request key filtering; the preserved .HomeRedirectService adapter is window-only.

Test window fallback separately from direct key routing. Exact resolved system Home windows may redirect after the settle period; Settings follow-on windows must cancel it. Unsupported OEM subclasses and platforms that never deliver Home must be reported, not guessed. A declared key capability is not physical key-delivery proof.

Open Factory UI and native Settings through the source Device menu with routing active. Verify actual native windows remain usable and the no-backup exemption marker survives process death. Do not substitute force-stopping the production-package candidate for service-process restart evidence: force-stop can change service/package behavior. Prepare a separate signed test instrumentation phase that kills only its target process after the external window is visible, then observe OS service reconnection and native-window retention. Follow with a real Home press and verify source return. This process-death harness and real OS scenarios are still pending; the prior standalone fixture only proves persisted policy and captured synthetic callbacks.

## Restore and report

Restore the exact accessibility list/value presence and Home role/default selections captured before the test, preserving unrelated entries. Read them back, remove both test APKs, and restore the original foreground app. Do not clear production data or change device power/network/account state. If the baseline role holder was absent, preserve absence rather than inventing a new preferred Home.

Record artifact hash/signer, serial/model/API, observed routes/key delivery, receiver authorization/results, saved-choice preservation, process-restart evidence, cleanup and restoration. Release the lease explicitly. These results do not close migration, physical TV, full feature parity, signing, release or owner-acceptance gates.
