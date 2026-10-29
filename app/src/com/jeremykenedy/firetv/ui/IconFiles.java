package com.jeremykenedy.firetv.ui;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Missing-only installation of the signed APK's canonical shared image files. */
final class IconFiles {
    static final int MAX_BYTES = 1024 * 1024;

    interface Source { InputStream open(String name) throws IOException; }
    interface Cancelled { boolean cancelled(); }
    interface Indexer { void index(List<File> files, Cancelled cancelled) throws IOException; }

    static final class Entry {
        final String name, sha256;
        final int bytes;

        Entry(String name, int bytes, String sha256) throws IOException {
            if (!name.matches("[a-z0-9-]+\\.png") || bytes < 24 || bytes > MAX_BYTES
                    || !sha256.matches("[0-9a-f]{64}")) throw new IOException("Invalid bundled icon entry.");
            this.name = name;
            this.bytes = bytes;
            this.sha256 = sha256;
        }
    }

    static final class Result {
        int copied, preserved, indexed;
        boolean access;

        String message() {
            return access ? "Icon files: " + copied + " added, " + preserved + " existing kept, "
                    + indexed + " canonical images indexed. Custom images were not changed."
                    : "Icon files need shared storage access. Allow icon file access, then retry.";
        }
    }

    private IconFiles() {}

    static Result install(boolean access, File directory, List<Entry> entries, Source source,
            Indexer indexer, Cancelled cancelled) throws IOException {
        Result result = new Result();
        result.access = access;
        if (!access) return result;
        Set<String> names = new HashSet<>();
        for (Entry entry : entries) {
            if (!names.add(entry.name)) throw new IOException("Duplicate bundled icon.");
        }
        check(cancelled);
        Files.createDirectories(directory.toPath());
        if (Files.isSymbolicLink(directory.toPath())) throw new IOException("The icon folder is a link.");
        List<File> canonical = new ArrayList<>();
        for (Entry entry : entries) {
            check(cancelled);
            Path target = directory.toPath().resolve(entry.name);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                result.preserved++;
                if (matches(target, entry)) canonical.add(target.toFile());
                continue;
            }
            byte[] data;
            try (InputStream input = source.open(entry.name)) {
                data = read(input, entry.bytes);
            }
            if (!digest(data).equals(entry.sha256) || !png(data)) {
                throw new IOException("A bundled icon failed verification.");
            }
            check(cancelled);
            Object key = null;
            try (SeekableByteChannel output = Files.newByteChannel(target,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                key = Files.readAttributes(target, BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS).fileKey();
                ByteBuffer buffer = ByteBuffer.wrap(data);
                while (buffer.position() < data.length) {
                    check(cancelled);
                    buffer.limit(Math.min(data.length, buffer.position() + 8192));
                    int count = output.write(buffer);
                    if (count <= 0) throw new IOException("The icon file could not be written.");
                }
            } catch (FileAlreadyExistsException existing) {
                result.preserved++;
                if (matches(target, entry)) canonical.add(target.toFile());
                continue;
            }
            BasicFileAttributes installed = Files.readAttributes(target, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!installed.isRegularFile() || (key != null && !key.equals(installed.fileKey()))
                    || !matches(target, entry)) {
                throw new IOException("An icon changed while it was being installed.");
            }
            result.copied++;
            canonical.add(target.toFile());
        }
        check(cancelled);
        indexer.index(canonical, cancelled);
        result.indexed = canonical.size();
        return result;
    }

    // Shared paths are never unlinked on failure: a replacement could appear after an identity check.
    // An interrupted new file is preserved until its owner explicitly removes it.
    static boolean matches(Path target, Entry entry) throws IOException {
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.size(target) != entry.bytes) return false;
        try (InputStream stream = Files.newInputStream(target, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            return digest(read(stream, entry.bytes)).equals(entry.sha256);
        } catch (IOException unreadableOrChanged) {
            return false;
        }
    }

    private static byte[] read(InputStream input, int expected) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(expected);
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (out.size() + count > expected) throw new IOException("A bundled icon is too large.");
            out.write(buffer, 0, count);
        }
        if (out.size() != expected) throw new IOException("A bundled icon is incomplete.");
        return out.toByteArray();
    }

    private static boolean png(byte[] bytes) {
        byte[] signature = {(byte)137,80,78,71,13,10,26,10,0,0,0,13,73,72,68,82};
        for (int i = 0; i < signature.length; i++) if (bytes[i] != signature[i]) return false;
        return ByteBuffer.wrap(bytes, 16, 8).getInt() == 1920
                && ByteBuffer.wrap(bytes, 20, 4).getInt() == 1080;
    }

    static String digest(byte[] bytes) throws IOException {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder value = new StringBuilder();
            for (byte part : hash) value.append(String.format(java.util.Locale.ROOT, "%02x", part & 255));
            return value.toString();
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IOException("SHA-256 is unavailable.", unavailable);
        }
    }

    static void check(Cancelled cancelled) throws InterruptedIOException {
        if (cancelled.cancelled()) throw new InterruptedIOException("Icon installation was cancelled; retry to finish indexing.");
    }
}
