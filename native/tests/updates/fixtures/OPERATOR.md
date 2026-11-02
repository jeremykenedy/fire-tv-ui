# Isolated update fixture operator procedure

This harness targets only `com.jeremykenedy.firetv.ui.updatefixture`. It has no
Home intent, production signer, account, network permission or production package
identifier. All certificates are newly generated local test keys. Test URLs are
syntax-only fixture metadata: no server or published release is implied and the
fixture reads bundled assets instead of making network requests.

Build with `python3 native/tests/updates/build_fixtures.py`. Output is local ignored
`native/tests/updates/.fixtures/`. `artifacts.json` records each fixture's hash,
package, version and signer label; `apksigner verify --print-certs` can inspect the
actual certificate fingerprints. Files are:

- `installed-v1.apk`: base fixture with bundled test APKs and updater screen.
- `upgrade.apk`: same fixture package, higher version, same local signer.
- `wrong-signer.apk`: higher version, unrelated local signer.
- `wrong-package.apk`: unrelated fixture package, same local signer.
- `older.apk`: same package and signer, version 1 instead of reviewed version 2.
- `fixture-proof.apk`: instrumentation targeting the isolated fixture only.

No installation or device action was performed by the builder. The exclusive
operator reserves an emulator and records its serial/API/foreground state before
installing `installed-v1.apk` and `fixture-proof.apk`. Never install these as a
launcher or substitute production/preview package IDs. Inspect the APKs first.

Instrumentation component:
`com.jeremykenedy.firetv.ui.updatefixture.proof/com.jeremykenedy.firetv.ui.updatefixture.FixtureInstrumentation`

Use `am instrument -w -e phase PHASE COMPONENT` through the explicitly selected
ADB serial. Prepared phases are:

- `engine`: actual Android archive inspection of accepted/rejected fixture APKs,
  cancellation, partial cleanup and new-coordinator private journal recovery.
  Nothing is installed; temporary verified bytes are removed afterward.
- `stage`: same engine checks, followed by native staged screen verification.
  Retains the private verified artifact. The operator may force-stop only the
  fixture package, then run `recover` to prove real process restart.
- `recover`: revalidates retained bytes, package, signer and increasing version,
  opens the real staged screen and confirms that staged bytes are not reported
  as installation success.
- `cancel`: requires the operator to have granted this isolated fixture ordinary
  install-source permission in Android settings. Opens the actual native installer
  through the explicit Install control, observes an external package-installer
  window, presses Back, verifies the fixture remains at version 1 and checks that
  the source UI reports no verified installation. No installer approval is sent.
- `install`: opens the same native installer and returns `WAITING_OWNER`. The
  owner must explicitly confirm the isolated fixture update. The harness never
  presses the installer approval control. After installation/process restart, run
  `verify` to check the retained journal against installed version 2 and the actual
  archive/installed signing certificates.
- `verify`: confirms version 2 and the signer through PackageManager, then removes
  the private fixture artifact/journal. It does not trust an installer result code.

Run cancellation before acceptance because an upgrade to version 2 requires a
fresh version-1 fixture for further upgrade scenarios. Reset only this isolated
fixture if needed, preserving unrelated app/device state. At completion uninstall
only the fixture and proof packages and restore the recorded foreground app.

Prepared and built locally; no native phase has been executed by the updater lane.
Actual installer UI behavior, process restart and installed outcomes remain
unproven until the exclusive operator runs these phases and records evidence.
