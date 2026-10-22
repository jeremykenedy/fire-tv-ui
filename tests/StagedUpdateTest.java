package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Environment;
import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import org.json.JSONObject;

public final class StagedUpdateTest {
    static final String PKG="com.jeremykenedy.firetv.ui";
    static final byte[] APK={4,5,6};
    static Context context;
    static File metadata;
    static File cached;
    static String accepted;
    static int checks;

    static PackageInfo pkg(int version, byte signer) {
        PackageInfo info=new PackageInfo();
        info.packageName=PKG;
        info.versionCode=version;
        info.signingInfo=new SigningInfo(new Signature[]{new Signature(signer)});
        return info;
    }

    static JSONObject release() throws Exception {
        StringBuilder sha=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(APK)) sha.append(String.format("%02x",b&255));
        return new JSONObject().put("name","Fire TV UI").put("package",PKG)
                .put("repo","jeremykenedy/fire-tv-ui").put("dream","").put("tag","v1.4.0")
                .put("url","https://github.com/jeremykenedy/fire-tv-ui/releases/download/v1.4.0/fire-tv-ui.apk")
                .put("bytes",3).put("sha256",sha.toString()).put("checked_at",System.currentTimeMillis());
    }

    static void write(JSONObject value) throws Exception {
        Files.writeString(metadata.toPath(),value.toString());
    }

    static void reject(JSONObject value) throws Exception {
        write(value);
        boolean failed=false;
        try {StagedUiUpdate.stage(context);} catch(Exception expected) {failed=true;}
        if(!failed)throw new AssertionError("Invalid handoff accepted");
        if(!accepted.equals(Files.readString(cached.toPath())))throw new AssertionError("Prior verified metadata was replaced");
        checks++;
    }

    public static void main(String[] args) throws Exception {
        File base=new File(args[0]);
        File cache=new File(base,"cache"); cache.mkdir(); context=new Context(cache);
        Environment.folder=new File(base,"Downloads"); Environment.folder.mkdir();
        metadata=new File(Environment.folder,StagedUiUpdate.METADATA_NAME);
        cached=new File(cache,"staged-ui-update.json");
        Files.write(new File(Environment.folder,StagedUiUpdate.APK_NAME).toPath(),APK);
        PackageManager.installed=pkg(37,(byte)1); PackageManager.archive=pkg(38,(byte)1);
        if(StagedUiUpdate.load(context)!=null)throw new AssertionError(); checks++;
        write(release().put("ignored_private_field","must-not-be-copied"));
        StagedUiUpdate.stage(context);
        accepted=Files.readString(cached.toPath());
        if(accepted.contains("ignored_private_field")||accepted.contains("must-not-be-copied"))throw new AssertionError("Unknown metadata retained");
        if(!"v1.4.0".equals(StagedUiUpdate.load(context).entry.tag))throw new AssertionError(); checks++;
        reject(release().put("package","other.app"));
        reject(release().put("repo","someone/other"));
        reject(release().put("tag","v1.4.0-beta"));
        reject(release().put("checked_at",System.currentTimeMillis()-7*3600000L));
        reject(release().put("checked_at",System.currentTimeMillis()+3600000L));
        reject(release().put("url","https://github.com/jeremykenedy/fire-tv-ui/releases/download/v1.3.0/fire-tv-ui.apk"));
        reject(release().put("bytes",4));
        reject(release().put("sha256","0".repeat(64)));
        PackageManager.archive=pkg(38,(byte)2); reject(release());
        PackageManager.archive=pkg(37,(byte)1); reject(release());
        PackageManager.archive=pkg(38,(byte)1); PackageManager.archive.packageName="other.app"; reject(release());
        PackageManager.archive=pkg(38,(byte)1); BackupStore.access=false; reject(release()); BackupStore.access=true;
        JSONObject stale=new JSONObject(accepted).put("checked_at",System.currentTimeMillis()-7*3600000L);
        Files.writeString(cached.toPath(),stale.toString());
        if(StagedUiUpdate.load(context)!=null)throw new AssertionError("Expired private metadata was offered"); checks++;
        Files.writeString(cached.toPath(),accepted);
        Files.delete(new File(Environment.folder,StagedUiUpdate.APK_NAME).toPath()); reject(release());
        System.out.println("Private UI staging checks passed: "+checks);
    }
}
