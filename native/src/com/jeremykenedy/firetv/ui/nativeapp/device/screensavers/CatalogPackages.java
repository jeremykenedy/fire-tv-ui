package com.jeremykenedy.firetv.ui.nativeapp.device.screensavers;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.io.File;
import java.security.MessageDigest;

final class CatalogPackages implements CatalogArtifact.Packages {
    private final PackageManager packages;
    CatalogPackages(Context context) { packages = context.getPackageManager(); }
    private int flags() { return Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES; }
    @Override public CatalogArtifact.Identity archive(File file) throws Exception {
        return identity(packages.getPackageArchiveInfo(file.getPath(), flags()));
    }
    @Override public CatalogArtifact.Identity installed(String packageName) throws Exception {
        try { return identity(packages.getPackageInfo(packageName, flags())); }
        catch (PackageManager.NameNotFoundException absent) { return null; }
    }
    private CatalogArtifact.Identity identity(PackageInfo info) throws Exception {
        if (info == null) return null;
        Signature[] signatures = Build.VERSION.SDK_INT >= 28 ? info.signingInfo == null ? null
                : info.signingInfo.getApkContentsSigners() : info.signatures;
        String[] hashes = new String[signatures == null ? 0 : signatures.length];
        for (int i = 0; i < hashes.length; i++) hashes[i] = CatalogArtifact.hex(MessageDigest.getInstance("SHA-256").digest(signatures[i].toByteArray()));
        return new CatalogArtifact.Identity(info.packageName, Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode, hashes);
    }
}
