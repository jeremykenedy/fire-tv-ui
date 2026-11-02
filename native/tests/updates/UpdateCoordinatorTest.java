package com.jeremykenedy.firetv.ui.nativeapp.updates;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;

public final class UpdateCoordinatorTest {
    private static final byte[] APK = "fixture-reviewed-apk".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final String PKG = "com.example.preview";
    private static int assertions;
    private static class Packages implements UpdateCoordinator.PackageInspector {
        UpdateCoordinator.Identity installed = identity(PKG, 1, "trusted");
        UpdateCoordinator.Identity archive = identity(PKG, 2, "trusted");
        @Override public UpdateCoordinator.Identity installed(String name) { return installed; }
        @Override public UpdateCoordinator.Identity archive(File file) { return archive; }
    }
    private interface Action { void run() throws Exception; }
    private static void expectFailure(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Unsafe input accepted"); }
        catch (IOException | IllegalArgumentException | IllegalStateException | InterruptedException expected) { assertions++; }
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); assertions++; }
    private static UpdateCoordinator.Identity identity(String pkg, long version, String... signers) {
        return new UpdateCoordinator.Identity(pkg, version, signers);
    }
    private static UpdateCoordinator.Release release(String pkg, String url, String digest, long bytes, long version) {
        return new UpdateCoordinator.Release("owner/repo", "v2", pkg, url, digest, bytes, version, "2.0", "Reviewed fixture release notes");
    }
    public static void main(String[] args) throws Exception {
        final File cache = Files.createTempDirectory("update-tests-").toFile();
        final Packages packages = new Packages();
        final UpdateCoordinator coordinator = new UpdateCoordinator(PKG, cache, packages);
        final String url = "https://github.com/owner/repo/releases/download/v2/update.apk";
        final String hash = UpdateCoordinator.hex(MessageDigest.getInstance("SHA-256").digest(APK));
        final UpdateCoordinator.Release release = release(PKG, url, hash, APK.length, 2);
        final UpdateCoordinator.Transport transport = (candidate, cancellation) -> new ByteArrayInputStream(APK);
        final UpdateCoordinator.Progress progress = percent -> check(percent >= 0 && percent <= 100);
        check(coordinator.check(cancellation -> null, new UpdateCoordinator.Cancellation()) == null);
        for (final String badUrl : new String[]{"http://github.com/owner/repo/releases/download/v2/update.apk",
                "https://github.com.evil.test/owner/repo/releases/download/v2/update.apk",
                "https://user@github.com/owner/repo/releases/download/v2/update.apk",
                "https://github.com:443/owner/repo/releases/download/v2/update.apk",
                "https://github.com/other/repo/releases/download/v2/update.apk",
                "https://github.com/owner/repo/releases/download/v3/update.apk",
                url + "?token=bad", url + "#fragment", url + "/../evil.apk",
                "https://github.com/owner/repo/releases/download/v2/%2e%2e.apk"}) {
            expectFailure(() -> release(PKG, badUrl, hash, APK.length, 2));
        }
        expectFailure(() -> release(PKG, null, hash, APK.length, 2));
        expectFailure(() -> release(PKG, url, "untrusted", APK.length, 2));
        expectFailure(() -> release("../package", url, hash, APK.length, 2));
        expectFailure(() -> release(PKG, url, hash, APK.length, -1));
        expectFailure(() -> release(PKG, url, hash, 0, 2));
        expectFailure(() -> release(PKG, url, hash, UpdateCoordinator.MAX_BYTES + 1, 2));
        expectFailure(() -> coordinator.check(c -> release("com.production.ui", url, hash, APK.length, 2), new UpdateCoordinator.Cancellation()));
        expectFailure(() -> coordinator.check(c -> release(PKG, url, hash, APK.length, 1), new UpdateCoordinator.Cancellation()));
        UpdateCoordinator.Verified verified = coordinator.download(release, transport, new UpdateCoordinator.Cancellation(), progress);
        check(coordinator.prepareInstall(verified, new UpdateCoordinator.Cancellation()).isFile());
        packages.installed = identity(PKG, 1, "attacker");
        expectFailure(() -> coordinator.prepareInstall(verified, new UpdateCoordinator.Cancellation()));
        packages.installed = identity(PKG, 2, "trusted");
        expectFailure(() -> coordinator.prepareInstall(verified, new UpdateCoordinator.Cancellation()));
        packages.installed = identity(PKG, 1, "trusted");
        // An installer RESULT_OK is deliberately not an input to confirmation.
        check(!coordinator.installationConfirmed(verified));
        packages.installed = identity(PKG, 2, "trusted");
        check(coordinator.installationConfirmed(verified));
        packages.installed = identity(PKG, 2, "attacker");
        check(!coordinator.installationConfirmed(verified));
        packages.installed = identity("com.other.app", 2, "trusted");
        check(!coordinator.installationConfirmed(verified));
        packages.installed = identity(PKG, 1, "trusted");
        File file = coordinator.prepareInstall(verified, new UpdateCoordinator.Cancellation());
        Files.write(file.toPath(), "tampered-same-length".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        expectFailure(() -> coordinator.prepareInstall(verified, new UpdateCoordinator.Cancellation()));
        verified.discard();
        for (final UpdateCoordinator.Identity archive : new UpdateCoordinator.Identity[]{null,
                identity("com.other.app", 2, "trusted"), identity(PKG, 1, "trusted"), identity(PKG, 3, "trusted"),
                identity(PKG, 2, "attacker"), identity(PKG, 2), identity(PKG, 2, "trusted", "trusted"), identity(PKG, 2, "trusted", "attacker")}) {
            packages.archive = archive;
            expectFailure(() -> coordinator.download(release, transport, new UpdateCoordinator.Cancellation(), progress));
            check(cache.list().length == 0);
        }
        packages.archive = identity(PKG, 2, "trusted");
        expectFailure(() -> coordinator.download(release(PKG, url, "0".repeat(64), APK.length, 2), transport, new UpdateCoordinator.Cancellation(), progress));
        expectFailure(() -> coordinator.download(release(PKG, url, hash, APK.length + 1, 2), transport, new UpdateCoordinator.Cancellation(), progress));
        expectFailure(() -> coordinator.download(release(PKG, url, hash, APK.length - 1, 2), transport, new UpdateCoordinator.Cancellation(), progress));
        UpdateCoordinator.Cancellation cancelled = new UpdateCoordinator.Cancellation(); cancelled.cancel();
        expectFailure(() -> coordinator.download(release, transport, cancelled, progress));
        final UpdateCoordinator.Cancellation during = new UpdateCoordinator.Cancellation();
        expectFailure(() -> coordinator.download(release, transport, during, p -> during.cancel()));
        expectFailure(() -> coordinator.download(release, (r,c) -> new InputStream() {
            int count;
            @Override public int read() throws IOException { if (count++ > 3) throw new IOException("Network interrupted"); return APK[0]; }
        }, new UpdateCoordinator.Cancellation(), progress));
        check(cache.list().length == 0);
        final StagedUpdateStore store = new StagedUpdateStore(cache);
        UpdateCoordinator.Verified staged = coordinator.stage(release, new ByteArrayInputStream(APK), new UpdateCoordinator.Cancellation(), progress);
        store.save(staged);
        check(store.hasPending());
        UpdateCoordinator.Verified restored = new StagedUpdateStore(cache).restore(coordinator, new UpdateCoordinator.Cancellation());
        check(restored != null && coordinator.prepareInstall(restored, new UpdateCoordinator.Cancellation()).isFile());
        packages.installed = identity(PKG, 2, "trusted");
        restored = new StagedUpdateStore(cache).restore(coordinator, new UpdateCoordinator.Cancellation());
        check(coordinator.installationConfirmed(restored));
        packages.installed = identity(PKG, 2, "attacker");
        expectFailure(() -> new StagedUpdateStore(cache).restore(coordinator, new UpdateCoordinator.Cancellation()));
        check(!store.hasPending());
        store.cleanup(null);
        check(cache.list().length == 0);
        packages.installed = identity(PKG, 1, "trusted");
        expectFailure(() -> coordinator.stage(release, new ByteArrayInputStream(APK), cancelled, progress));
        check(cache.list().length == 0);
        packages.installed = null;
        expectFailure(() -> coordinator.download(release, transport, new UpdateCoordinator.Cancellation(), progress));
        cache.delete();
        System.out.println("Update verification: " + assertions + " assertions passed");
    }
}
