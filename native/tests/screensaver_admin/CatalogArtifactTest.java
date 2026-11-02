package com.jeremykenedy.firetv.ui.nativeapp.device.screensavers;

import com.jeremykenedy.firetv.ui.nativeapp.updates.UpdateCoordinator;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;

public final class CatalogArtifactTest {
    static final byte[] BYTES = {1, 2, 3, 4};
    static final CatalogArtifact.Identity GOOD = new CatalogArtifact.Identity("example.saver", 2, new String[]{"trusted"});
    static final class Packages implements CatalogArtifact.Packages {
        CatalogArtifact.Identity installed;
        CatalogArtifact.Identity archive = GOOD;
        int inspections;
        @Override public CatalogArtifact.Identity archive(File file) { inspections++; return archive; }
        @Override public CatalogArtifact.Identity installed(String name) { return installed; }
    }
    public static void main(String[] args) throws Exception {
        File cache = Files.createTempDirectory("catalog-test").toFile();
        ScreensaverInstallCatalog.Entry entry = new ScreensaverInstallCatalog.Entry("Test", "example.saver", "jeremykenedy/test", "v2",
                "https://github.com/jeremykenedy/test/releases/download/v2/test.apk",
                CatalogArtifact.hex(MessageDigest.getInstance("SHA-256").digest(BYTES)), "", BYTES.length);
        Packages packages = new Packages();
        File first = CatalogArtifact.download(cache, entry, new ByteArrayInputStream(BYTES), packages,
                new UpdateCoordinator.Cancellation());
        check(first.isFile(), "Exact reviewed first install is staged");
        Files.write(first.toPath(), new byte[]{1, 2, 3, 5});
        rejects(new Action() { public void run() throws Exception { CatalogArtifact.verify(first, entry, packages, new UpdateCoordinator.Cancellation()); }}, "Changed artifact rejected before installer");
        first.delete(); packages.inspections = 0;
        rejects(new Action() { public void run() throws Exception { CatalogArtifact.download(cache, entry, new ByteArrayInputStream(new byte[]{9, 2, 3, 4}), packages, new UpdateCoordinator.Cancellation()); }}, "Wrong hash rejected");
        check(packages.inspections == 0, "Untrusted bytes never reach archive inspection");
        packages.installed = new CatalogArtifact.Identity("example.saver", 1, new String[]{"different"});
        rejects(new Action() { public void run() throws Exception { CatalogArtifact.download(cache, entry, new ByteArrayInputStream(BYTES), packages, new UpdateCoordinator.Cancellation()); }}, "Signer mismatch rejected");
        packages.installed = GOOD;
        rejects(new Action() { public void run() throws Exception { CatalogArtifact.download(cache, entry, new ByteArrayInputStream(BYTES), packages, new UpdateCoordinator.Cancellation()); }}, "Same version rejected");
        packages.installed = new CatalogArtifact.Identity("example.saver", 1, new String[]{"trusted"});
        File update = CatalogArtifact.download(cache, entry, new ByteArrayInputStream(BYTES), packages, new UpdateCoordinator.Cancellation());
        check(update.isFile(), "Higher version with same signer accepted"); update.delete();
        packages.archive = new CatalogArtifact.Identity("wrong.package", 3, new String[]{"trusted"});
        rejects(new Action() { public void run() throws Exception { CatalogArtifact.download(cache, entry, new ByteArrayInputStream(BYTES), packages, new UpdateCoordinator.Cancellation()); }}, "Wrong package rejected");
        check(cache.list().length == 0, "Failed and discarded downloads leave no artifacts");
        cache.delete();
        check(ScreensaverInstallCatalog.ENTRIES.length > 0, "Maintained catalog metadata validates");
        System.out.println("Reviewed catalog artifact security behavior passed");
    }
    interface Action { void run() throws Exception; }
    static void rejects(Action action, String label) throws Exception {
        try { action.run(); } catch (Exception expected) { return; }
        throw new AssertionError(label);
    }
    static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
}
