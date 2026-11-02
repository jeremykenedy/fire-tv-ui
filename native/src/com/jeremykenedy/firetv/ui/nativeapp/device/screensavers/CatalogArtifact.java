package com.jeremykenedy.firetv.ui.nativeapp.device.screensavers;

import com.jeremykenedy.firetv.ui.nativeapp.updates.UpdateCoordinator;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Exact reviewed bytes establish first-install identity; updates also require the installed signing identity. */
public final class CatalogArtifact {
    public interface Packages {
        Identity archive(File file) throws Exception;
        Identity installed(String packageName) throws Exception;
    }
    public static final class Identity {
        public final String packageName;
        public final long version;
        public final Set<String> signers;
        public final int signerCount;
        public Identity(String packageName, long version, String[] signers) {
            this.packageName = packageName;
            this.version = version;
            this.signerCount = signers == null ? 0 : signers.length;
            this.signers = new HashSet<>(signers == null ? java.util.Collections.<String>emptyList() : Arrays.asList(signers));
            if (this.signers.contains(null) || this.signers.contains("")) this.signers.clear();
        }
    }
    private CatalogArtifact() {}
    public static File download(File cache, ScreensaverInstallCatalog.Entry entry, InputStream source,
            Packages packages, UpdateCoordinator.Cancellation cancellation) throws Exception {
        File artifact = File.createTempFile("verified-catalog-", ".apk", cache);
        boolean verified = false;
        try {
            long total = 0;
            try (InputStream input = source; FileOutputStream output = new FileOutputStream(artifact)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    cancellation.check();
                    total += count;
                    if (total > entry.bytes) throw new IllegalStateException("The APK exceeds the reviewed size.");
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
            verify(artifact, entry, packages, cancellation);
            verified = true;
            return artifact;
        } finally { if (!verified) artifact.delete(); }
    }
    public static Identity verify(File artifact, ScreensaverInstallCatalog.Entry entry, Packages packages,
            UpdateCoordinator.Cancellation cancellation) throws Exception {
        Identity archive = verifiedArchive(artifact, entry, packages, cancellation);
        Identity installed = packages.installed(entry.packageName);
        if (installed != null) {
            if (!entry.packageName.equals(installed.packageName) || archive.version <= installed.version) {
                throw new IllegalStateException("This saver is already at the reviewed version or newer.");
            }
            if (installed.signers.isEmpty() || installed.signerCount != archive.signerCount || !installed.signers.equals(archive.signers)) {
                throw new IllegalStateException("The APK signing certificate differs from the installed saver.");
            }
        }
        return archive;
    }
    public static boolean installationConfirmed(File artifact, ScreensaverInstallCatalog.Entry entry, Packages packages,
            UpdateCoordinator.Cancellation cancellation) throws Exception {
        Identity archive = verifiedArchive(artifact, entry, packages, cancellation);
        Identity installed = packages.installed(entry.packageName);
        return installed != null && entry.packageName.equals(installed.packageName) && archive.version == installed.version
                && !installed.signers.isEmpty() && installed.signerCount == archive.signerCount && installed.signers.equals(archive.signers);
    }
    private static Identity verifiedArchive(File artifact, ScreensaverInstallCatalog.Entry entry, Packages packages,
            UpdateCoordinator.Cancellation cancellation) throws Exception {
        if (artifact.length() != entry.bytes) throw new IllegalStateException("The APK does not match its reviewed size.");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(artifact)) {
            byte[] buffer = new byte[65536]; int count;
            while ((count = input.read(buffer)) != -1) { cancellation.check(); digest.update(buffer, 0, count); }
        }
        if (!entry.sha256.equals(hex(digest.digest()))) throw new IllegalStateException("The APK does not match its reviewed checksum.");
        cancellation.check();
        Identity archive = packages.archive(artifact);
        if (archive == null || !entry.packageName.equals(archive.packageName) || archive.version <= 0 || archive.signers.isEmpty()) {
            throw new IllegalStateException("The verified APK has no valid package, version or signing identity.");
        }
        return archive;
    }
    public static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder();
        for (byte item : bytes) value.append(String.format(java.util.Locale.ROOT, "%02x", item & 255));
        return value.toString();
    }
}
