package com.jeremykenedy.firetv.ui.nativeapp.updates;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Verifies reviewed release metadata and the installed identity before offering an installer. */
public final class UpdateCoordinator {
    public static final long MAX_BYTES = 256L * 1024 * 1024;
    public interface Source { Release check(Cancellation cancellation) throws Exception; }
    public interface Transport { InputStream open(Release release, Cancellation cancellation) throws Exception; }
    public interface PackageInspector {
        Identity installed(String packageName) throws Exception;
        Identity archive(File file) throws Exception;
    }
    public interface Progress { void update(int percent); }

    public static final class Cancellation {
        private volatile boolean cancelled;
        public void cancel() { cancelled = true; }
        public void check() throws InterruptedException {
            if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedException("Update cancelled.");
        }
    }

    public static final class Identity {
        public final String packageName;
        public final long version;
        private final Set<String> signers;
        private final int signerCount;
        public Identity(String packageName, long version, String[] signers) {
            this.packageName = packageName;
            this.version = version;
            this.signerCount = signers == null ? 0 : signers.length;
            this.signers = signers == null ? new HashSet<String>() : new HashSet<String>(Arrays.asList(signers));
            if (this.signers.contains(null) || this.signers.contains("")) this.signers.clear();
        }
    }

    /** Only a reviewed source may supply this contract. Intents and downloaded APKs are not metadata sources. */
    public static final class Release {
        public final String repository, tag, packageName, url, sha256, versionName, notes;
        public final long bytes, version;
        public Release(String repository, String tag, String packageName, String url, String sha256,
                long bytes, long version, String versionName, String notes) {
            if (url == null || url.length() > 2048 || repository == null || repository.length() > 200 || !repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
                    || repository.startsWith("./") || repository.startsWith("../") || repository.endsWith("/.") || repository.endsWith("/..")
                    || tag == null || tag.length() > 100 || !tag.matches("[A-Za-z0-9_.-]+") || tag.equals(".") || tag.equals("..")
                    || packageName == null || packageName.length() > 255 || !packageName.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
                    || sha256 == null || !sha256.matches("[a-f0-9]{64}")
                    || bytes <= 0 || bytes > MAX_BYTES || version <= 0
                    || versionName == null || versionName.length() == 0 || versionName.length() > 100
                    || notes == null || notes.length() > 16384) throw new IllegalArgumentException("Invalid reviewed release metadata.");
            try {
                URL location = new URL(url);
                String prefix = "/" + repository + "/releases/download/" + tag + "/";
                if (!"https".equals(location.getProtocol()) || !"github.com".equals(location.getHost())
                        || location.getUserInfo() != null || location.getPort() != -1
                        || location.getQuery() != null || location.getRef() != null
                        || !location.getPath().startsWith(prefix)
                        || !location.getPath().substring(prefix.length()).matches("[A-Za-z0-9_.-]+\\.apk")) {
                    throw new IllegalArgumentException("The APK must belong to the reviewed GitHub release.");
                }
            } catch (IOException failed) { throw new IllegalArgumentException("Invalid release URL.", failed); }
            this.repository = repository; this.tag = tag; this.packageName = packageName; this.url = url;
            this.sha256 = sha256; this.bytes = bytes; this.version = version;
            this.versionName = versionName; this.notes = notes;
        }
        public String releaseUrl() { return "https://github.com/" + repository + "/releases/tag/" + tag; }
    }

    public static final class Verified {
        final File file;
        public final Release release;
        private Verified(File file, Release release) { this.file = file; this.release = release; }
        public void discard() { file.delete(); }
    }

    private final String ownPackage;
    private final File cache;
    private final PackageInspector packages;
    public UpdateCoordinator(String ownPackage, File cache, PackageInspector packages) {
        this.ownPackage = ownPackage; this.cache = cache; this.packages = packages;
    }

    public Release check(Source source, Cancellation cancellation) throws Exception {
        cancellation.check();
        Release release = source.check(cancellation);
        cancellation.check();
        if (release != null) validateInstalled(release);
        return release;
    }

    private Identity validateInstalled(Release release) throws Exception {
        if (!ownPackage.equals(release.packageName)) throw new IllegalStateException("No compatible source release for this app package.");
        Identity installed = packages.installed(ownPackage);
        if (installed == null || !ownPackage.equals(installed.packageName) || installed.signers.isEmpty()) {
            throw new IllegalStateException("The installed app identity could not be verified.");
        }
        if (release.version <= installed.version) throw new IllegalStateException("This app is already at the reviewed version or newer.");
        return installed;
    }

    public Verified download(Release release, Transport transport, Cancellation cancellation, Progress progress) throws Exception {
        cancellation.check();
        Identity installed = validateInstalled(release);
        File file = File.createTempFile("verified-update-", ".apk", cache);
        boolean verified = false;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0;
            int previous = -1;
            try (InputStream input = transport.open(release, cancellation); FileOutputStream output = new FileOutputStream(file)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    cancellation.check();
                    total += count;
                    if (total > release.bytes) throw new IOException("The APK exceeds its reviewed size.");
                    output.write(buffer, 0, count);
                    digest.update(buffer, 0, count);
                    int percent = (int) (100 * total / release.bytes);
                    if (percent != previous) { progress.update(percent); previous = percent; }
                }
                cancellation.check();
                output.getFD().sync();
            }
            if (total != release.bytes || !release.sha256.equals(hex(digest.digest()))) {
                throw new IOException("The APK does not match its reviewed size and checksum.");
            }
            verifyIdentity(release, installed, packages.archive(file));
            cancellation.check();
            verified = true;
            return new Verified(file, release);
        } finally { if (!verified) file.delete(); }
    }

    /** Explicitly staged APK bytes use exactly the same verifier as network downloads. */
    public Verified stage(Release release, final InputStream artifact, Cancellation cancellation, Progress progress) throws Exception {
        try (InputStream input = artifact) {
            return download(release, new Transport() {
                @Override public InputStream open(Release reviewed, Cancellation request) { return input; }
            }, cancellation, progress);
        }
    }

    /** Private-cache recovery re-verifies bytes and identity; journal contents cannot authorize installation. */
    public Verified recover(File file, Release release, Cancellation cancellation) throws Exception {
        if (!ownPackage.equals(release.packageName)) throw new IllegalStateException("Incompatible staged package.");
        if (!cache.getCanonicalFile().equals(file.getCanonicalFile().getParentFile())
                || !file.getName().matches("verified-update-[A-Za-z0-9_-]+\\.apk")) throw new IOException("Invalid staged artifact path.");
        Verified verified = new Verified(file, release);
        Identity installed = packages.installed(ownPackage);
        Identity archive = packages.archive(file);
        if (installed == null || !ownPackage.equals(installed.packageName) || installed.version > release.version) {
            throw new IllegalStateException("The installed app no longer matches this staged upgrade.");
        }
        verifyIdentity(release, installed, archive, true);
        verifyBytes(verified, cancellation);
        return verified;
    }

    /** Recheck bytes and current installed identity immediately before the explicit install action. */
    public File prepareInstall(Verified verified, Cancellation cancellation) throws Exception {
        cancellation.check();
        verifyBytes(verified, cancellation);
        verifyIdentity(verified.release, validateInstalled(verified.release), packages.archive(verified.file));
        cancellation.check();
        return verified.file;
    }

    private void verifyBytes(Verified verified, Cancellation cancellation) throws Exception {
        if (verified.file.length() != verified.release.bytes) throw new IOException("The verified APK is no longer available.");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new java.io.FileInputStream(verified.file)) {
            byte[] buffer = new byte[65536]; int count;
            while ((count = input.read(buffer)) != -1) { cancellation.check(); digest.update(buffer, 0, count); }
        }
        if (!verified.release.sha256.equals(hex(digest.digest()))) throw new IOException("The verified APK changed. Download again.");
    }

    private void verifyIdentity(Release release, Identity installed, Identity archive) {
        verifyIdentity(release, installed, archive, false);
    }
    private void verifyIdentity(Release release, Identity installed, Identity archive, boolean allowInstalled) {
        if (archive == null || !release.packageName.equals(archive.packageName)) throw new IllegalStateException("The APK contains a different package.");
        if (archive.version != release.version || (!allowInstalled && archive.version <= installed.version)) throw new IllegalStateException("The APK version does not match the reviewed upgrade.");
        if (archive.signers.isEmpty() || installed.signerCount != archive.signerCount || !installed.signers.equals(archive.signers)) throw new IllegalStateException("The APK uses a different signing certificate.");
    }

    public boolean installationConfirmed(Verified verified) {
        try {
            Identity installed = packages.installed(ownPackage);
            Identity archive = packages.archive(verified.file);
            return installed != null && archive != null && ownPackage.equals(installed.packageName)
                    && installed.version == verified.release.version && installed.version == archive.version
                    && !installed.signers.isEmpty() && installed.signerCount == archive.signerCount
                    && installed.signers.equals(archive.signers);
        } catch (Exception unavailable) { return false; }
    }

    static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
}
