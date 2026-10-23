package com.jeremykenedy.firetv.ui;

import android.content.Context;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.Arrays;

public final class NativeTileArtworkTest {
    public static void main(String[] args) throws Exception {
        Context context = new Context();
        context.directory = new File(args[0], "private");
        context.directory.mkdirs();
        for (String pkg : new String[] {"com.amazon.tv.livetv", "com.amazon.tv.launcher"}) {
            String custom = "content://user-art/preserved-image";
            if (!custom.equals(NativeTileArtwork.uri(context, pkg, custom))) throw new AssertionError("Custom artwork replaced");
            if (context.assets.opens != 0) throw new AssertionError("Custom artwork opened fallback asset");
        }
        if (NativeTileArtwork.uri(context, "other.publisher", null) != null) throw new AssertionError("Unrelated app overridden");
        for (String pkg : new String[] {"com.amazon.tv.livetv", "com.amazon.tv.launcher"}) {
            String uri = NativeTileArtwork.uri(context, pkg, null);
            File output = new File(URI.create(uri));
            File original = new File(System.getProperty("asset.root"), "native-tiles/" + output.getName());
            if (!output.isFile() || !Arrays.equals(Files.readAllBytes(output.toPath()), Files.readAllBytes(original.toPath()))) throw new AssertionError("Fallback image differs from bundled image");
            int opens = context.assets.opens;
            if (!uri.equals(NativeTileArtwork.uri(context, pkg, "")) || context.assets.opens != opens) throw new AssertionError("Cached fallback not reused");
            output.delete();
        }
        context.assets.fail = true;
        if (NativeTileArtwork.uri(context, "com.amazon.tv.livetv", null) != null) throw new AssertionError("Missing asset must retain native fallback");
        File directory = new File(context.directory, "native-tiles");
        if (directory.list().length != 0) throw new AssertionError("Partial image left behind");
        System.out.println("Native artwork custom-icon, fallback, caching and failure checks passed");
    }
}
