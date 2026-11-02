# Source preview update contract

The Updates screen is `com.jeremykenedy.firetv.ui.nativeapp.updates.UpdatesActivity`.
The integration owner adds its non-exported activity and the non-exported
`UpdateFileProvider`, with authority `${applicationId}.updates` and
`grantUriPermissions=true`. Downloading needs INTERNET; requesting installation
needs REQUEST_INSTALL_PACKAGES. No production release pins change.

`UpdatesActivity.releaseSource()` returns no release in this preview. There is no
independently published compatible feed to configure. The real Check action
reports that state and never downloads the production package. A reviewed source
adapter can later implement `UpdateCoordinator.Source`, returning a `Release` or
null. Metadata is not accepted through intents, external files or the APK itself.
A source adapter must authenticate its reviewed metadata before constructing the
contract. SHA-256 received from an untrusted source is not a trust anchor.

The immutable release contract includes repository, tag, package, exact byte
length, SHA-256, increasing version code, version label and release notes. The
coordinator accepts its own installed package only, verifies exact archive
package/version and the installed signing-certificate set/count, then offers a
separate explicit Install action. It rechecks the bytes, archive and current
installed identity before handing a read-only content URI to the native installer.
Installer return codes are not installation proof; installed version and signing
certificates are checked independently. Cancellation and failures delete partial
artifacts. Closing the screen cancels worker work. A verified artifact and bounded private
journal persist for up to six hours. Recreation rechecks the cache path, digest,
size, archive and currently installed identity before offering Install again or
confirming the exact installed version and signer. Incomplete unreferenced updater
cache files are removed. Installer return during asynchronous restoration waits for
that revalidation. A staged/downloaded file alone never establishes installation.

`ReleaseTransport` allows only HTTPS GitHub release hosts, at most five redirects,
15-second connect/read timeouts, a two-minute overall deadline and at most 256 MiB
subject to the exact reviewed size. Cancellation during a blocked network read
can take up to the read timeout. No background check or automatic installer runs.

Verification performed by this lane:

- `python3 native/tests/updates/run.py`: 72 assertions passed. Includes malformed
  metadata, foreign package, signer/version/digest/size mismatch, installed identity
  changes, cancellation, interrupted streams, partial-file cleanup and truthful
  installation confirmation.
- Isolated temporary javac compilation against Android 36 passed for production
  update classes, Pages and UpdatesInstrumentation. No shared build output changed.

The integration owner builds `native/tests/updates/build_native_test.py` after the
combined app build. Its instrumentation component is
`com.jeremykenedy.firetv.ui.updatesproof/com.jeremykenedy.firetv.ui.nativeapp.updates.UpdatesInstrumentation`.
This suite proves the direct native empty-feed screen, focus, Check action and
absence of download/install actions. It has not been run by this lane. Settings
navigation, fixture APK native installer returns, real update feeds, production staged
self-update/catalog integration, actual process-death recovery and physical-TV
behavior remain unproven. An isolated fixture suite is now prepared and locally
built; see `fixtures/OPERATOR.md` for the exclusive operator procedure. This lane changed no shared routing,
manifest, build, release pin or device state and created no commit.

## Staged fixture follow-up

`UpdateCoordinator.stage` imports reviewed APK streams through the same verification
path as network downloads. It does not accept unreviewed external metadata as a
trust anchor. `StagedUpdateStore` persists only app-private verified state. Toolkit's
source-release JSON is a production-only, blocked planning contract with
`signatureVerified=false`, `publishedReleaseVerified=false` and
`installationAllowed=false`; it cannot authorize preview staging. The native
preview has no production metadata-import route or silent production installation.

`python3 native/tests/updates/build_fixtures.py` successfully built and signed
isolated version-1/version-2, wrong-package and wrong-signer fixtures plus proof
instrumentation. All outputs and newly generated test keys stay in ignored
`.fixtures/`. No shared build or device changed. The prepared native phases exercise
real Android archive/signature inspection, staged acceptance/rejection, cancellation,
cleanup, process restart and native installer cancellation/owner-confirmed acceptance.
None of these native phases was run by this lane.

Screensaver catalog work may reuse `ReleaseTransport.open(url, expectedBytes,
cancellation)` only after its own reviewed repository/tag metadata validation.
The shared read-only provider also permits `verified-catalog-*.apk`; the catalog
module owns those files, while updater orphan cleanup owns `verified-update-*.apk`.
The self-update verifier remains restricted to its own installed package.
