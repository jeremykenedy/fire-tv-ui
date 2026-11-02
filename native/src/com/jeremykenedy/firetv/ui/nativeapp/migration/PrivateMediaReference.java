package com.jeremykenedy.firetv.ui.nativeapp.migration;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/** Explicit metadata-only verification. It never opens, copies or deletes the selected media. */
public final class PrivateMediaReference {
    public final String uri;
    private final List<File> roots;
    private PrivateMediaReference(String uri, List<File> roots) { this.uri = uri; this.roots = roots; }

    public static PrivateMediaReference verify(String uri, File files, File noBackup, File cache) throws IOException {
        List<File> roots = new ArrayList<>();
        for (File root : new File[]{files, noBackup, cache}) {
            if (root == null || !root.isDirectory()) throw unavailable();
            roots.add(root.getCanonicalFile());
        }
        PrivateMediaReference reference = new PrivateMediaReference(uri, Collections.unmodifiableList(roots));
        reference.verifyAvailable();
        return reference;
    }

    public void verifyAvailable() throws IOException {
        try {
            if (uri == null || uri.length() > 16384) throw unavailable();
            URI value = new URI(uri);
            if (!"file".equals(value.getScheme()) || value.getRawAuthority() != null
                    || value.getRawQuery() != null || value.getRawFragment() != null
                    || value.getPath() == null || !value.getPath().startsWith("/")) throw unavailable();
            File selected = new File(value).getCanonicalFile();
            boolean privateFile = false;
            for (File root : roots) {
                if (selected.getPath().startsWith(root.getCanonicalPath() + File.separator)) privateFile = true;
            }
            if (!privateFile || !selected.isFile() || !selected.canRead()) throw unavailable();
        } catch (URISyntaxException | IllegalArgumentException invalid) {
            throw unavailable();
        }
    }
    private static IOException unavailable() {
        return new IOException("The selected private media reference is unavailable or outside app-owned storage.");
    }
}
