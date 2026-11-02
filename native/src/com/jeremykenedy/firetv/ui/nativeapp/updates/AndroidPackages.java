package com.jeremykenedy.firetv.ui.nativeapp.updates;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.io.File;
import java.security.MessageDigest;

public final class AndroidPackages implements UpdateCoordinator.PackageInspector {
    private final PackageManager packages;
    public AndroidPackages(Context context) { packages = context.getPackageManager(); }
    private int flags() { return Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES; }
    @Override public UpdateCoordinator.Identity installed(String packageName) throws Exception {
        return identity(packages.getPackageInfo(packageName, flags()));
    }
    @Override public UpdateCoordinator.Identity archive(File file) throws Exception {
        return identity(packages.getPackageArchiveInfo(file.getAbsolutePath(), flags()));
    }
    private UpdateCoordinator.Identity identity(PackageInfo info) throws Exception {
        if (info == null) return null;
        Signature[] signatures = Build.VERSION.SDK_INT >= 28
                ? info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners() : info.signatures;
        String[] hashes = signatures == null ? new String[0] : new String[signatures.length];
        for (int i = 0; i < hashes.length; i++) hashes[i] = UpdateCoordinator.hex(MessageDigest.getInstance("SHA-256").digest(signatures[i].toByteArray()));
        return new UpdateCoordinator.Identity(info.packageName,
                Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode, hashes);
    }
}
