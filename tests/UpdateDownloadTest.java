package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.MessageDigest;
import org.json.JSONObject;

public final class UpdateDownloadTest {
    static final byte[] BYTES = {1,2,3};
    static int checks;
    static Context context;

    static PackageInfo pkg(int version, byte signer) {
        PackageInfo info = new PackageInfo();
        info.packageName = "example.saver";
        info.versionCode = version;
        info.signatures = new Signature[]{new Signature(signer)};
        info.signingInfo = new SigningInfo(info.signatures);
        return info;
    }

    static ScreensaverCatalog.Entry entry(String checksum, long bytes) throws Exception {
        return new ScreensaverCatalog.Entry(new JSONObject().put("name","Example")
                .put("package","example.saver").put("repo","jeremykenedy/example")
                .put("dream","example.saver/.Dream").put("tag","v2.0.0")
                .put("url","https://github.com/jeremykenedy/example/releases/download/v2/example.apk")
                .put("bytes",bytes).put("sha256",checksum));
    }

    static void reject(ScreensaverCatalog.Entry entry) throws Exception {
        try {
            ScreensaverDownload.download(context, entry, percent -> { });
            throw new AssertionError("Incompatible APK was accepted");
        } catch (IllegalStateException expected) {
            if (context.getCacheDir().list().length != 0) throw new AssertionError("Rejected APK was retained");
            checks++;
        }
    }

    static void rejectInspection(ScreensaverCatalog.Entry entry) throws Exception {
        try {
            ScreensaverDownload.inspectRelease(context, entry);
            throw new AssertionError("An incompatible APK passed inspection");
        } catch (IllegalStateException expected) {
            if (context.getCacheDir().list().length != 0) throw new AssertionError("Inspected APK was retained");
            checks++;
        }
    }

    public static void main(String[] args) throws Exception {
        URL.setURLStreamHandlerFactory(protocol -> new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) {
                return new HttpURLConnection(url) {
                    @Override public void connect() { }
                    @Override public boolean usingProxy() { return false; }
                    @Override public void disconnect() { }
                    @Override public int getResponseCode() { return 200; }
                    @Override public InputStream getInputStream() { return new ByteArrayInputStream(BYTES); }
                };
            }
        });
        context = new Context(new File(args[0]));
        StringBuilder digest = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(BYTES)) digest.append(String.format("%02x",b&255));
        ScreensaverCatalog.Entry entry = entry(digest.toString(),3);
        PackageManager.installed=pkg(1,(byte)1); PackageManager.archive=pkg(2,(byte)1);
        File accepted=ScreensaverDownload.download(context,entry,percent -> { });
        if (!accepted.isFile() || accepted.length()!=3) throw new AssertionError(); accepted.delete(); checks++;
        PackageManager.archive=pkg(2,(byte)2); reject(entry);
        PackageManager.archive=pkg(1,(byte)1); reject(entry);
        PackageManager.archive=pkg(0,(byte)1); reject(entry);
        PackageManager.archive=pkg(2,(byte)1); PackageManager.archive.packageName="other.app"; reject(entry);
        PackageManager.archive=null; reject(entry);
        PackageManager.archive=pkg(2,(byte)1); PackageManager.archive.signingInfo=null; reject(entry);
        PackageManager.archive=pkg(2,(byte)1); reject(entry("0".repeat(64),3));
        reject(entry(digest.toString(),2)); reject(entry(digest.toString(),4));
        Build.VERSION.SDK_INT=27;
        accepted=ScreensaverDownload.download(context,entry,percent -> { }); accepted.delete(); checks++;
        PackageManager.archive.signatures=new Signature[0]; reject(entry);
        PackageManager.archive=pkg(2,(byte)1); PackageManager.installed=null;
        accepted=ScreensaverDownload.download(context,entry,percent -> { }); accepted.delete(); checks++;
        Build.VERSION.SDK_INT=34;
        PackageManager.installed=pkg(125,(byte)1); PackageManager.archive=pkg(125,(byte)1);
        PackageManager.archive.versionName="1.2.5";
        PackageInfo inspected=ScreensaverDownload.inspectRelease(context,entry);
        if (inspected.versionCode!=125 || !"1.2.5".equals(inspected.versionName)
                || context.getCacheDir().list().length!=0) throw new AssertionError("Current helper inspection failed");
        checks++;
        reject(entry);
        PackageManager.archive=pkg(124,(byte)1);
        if (ScreensaverDownload.inspectRelease(context,entry).versionCode!=124) throw new AssertionError();
        checks++;
        reject(entry);
        PackageManager.archive=pkg(126,(byte)2); rejectInspection(entry);
        PackageManager.archive=pkg(126,(byte)1); PackageManager.archive.packageName="other.app";
        rejectInspection(entry);
        PackageManager.archive=pkg(126,(byte)1); rejectInspection(entry("0".repeat(64),3));
        PackageManager.installed=null; rejectInspection(entry);
        PackageManager.installed=pkg(125,(byte)1);
        Thread.currentThread().interrupt();
        try { ScreensaverDownload.download(context,entry,percent -> { }); throw new AssertionError(); }
        catch (InterruptedException expected) { Thread.interrupted(); checks++; }
        if (context.getCacheDir().list().length!=0) throw new AssertionError("Cancelled APK retained");
        System.out.println("APK update integrity/signature/version checks passed: "+checks);
    }
}
