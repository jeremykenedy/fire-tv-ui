package com.jeremykenedy.firetv.ui.nativeapp.stateguard;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.*;

/** Disposable empty-state smoke test only. No arbitrary editor or owner-data mutation. */
public final class GuardProbeInstrumentation extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle args){super.onCreate(args);arguments=args==null?new Bundle():args;start();}
    @Override public void onStart(){
        Bundle result=new Bundle();result.putInt("pid",android.os.Process.myPid());int code=Activity.RESULT_CANCELED;
        try{
            Context context=getTargetContext();
            if(!"true".equals(arguments.getString("quiescent")))throw new IllegalStateException("QUIESCENCE_ACK_REQUIRED");
            if(!"state-guard-smoke".equals(arguments.getString("case_id")))throw new IllegalStateException("DISPOSABLE_SMOKE_CASE_REQUIRED");
            String rawRoots=arguments.getString("roots");if(rawRoots==null)throw new IllegalStateException("EXPLICIT_ROOTS_REQUIRED");
            Set<String> roots=new LinkedHashSet<>();for(String root:rawRoots.split(",",-1))if(!roots.add(root))throw new IllegalStateException("DUPLICATE_ROOT_REFUSED");
            if(!roots.equals(new HashSet<>(Arrays.asList("shared_prefs","files","no_backup"))))throw new IllegalStateException("ALL_THREE_REVIEWED_ROOTS_REQUIRED");
            StateGuardInstrumentation.requireStopped(context);
            KeyStore keys=KeyStore.getInstance("AndroidKeyStore");keys.load(null);int aliases=0;
            for(Enumeration<String> names=keys.aliases();names.hasMoreElements();){names.nextElement();aliases++;}
            result.putInt("keystore_alias_count",aliases);if(aliases!=0)throw new IllegalStateException("NONEXPORTABLE_KEYSTORE_STATE_REFUSED");
            String hash=arguments.getString("manifest_sha256");
            GuardStore store=new GuardStore(context.getDataDir());GuardStore.Result baseline=store.verify("state-guard-smoke",roots,hash);
            if(!baseline.matches||baseline.files!=0||baseline.bytes!=0)throw new IllegalStateException("VERIFIED_EMPTY_BASELINE_REQUIRED");
            Path data=context.getDataDir().toPath().toRealPath();
            Path preferences=data.resolve("shared_prefs/state_guard_probe.xml");
            Path directory=data.resolve("files/state_guard_probe");
            Path privateFile=data.resolve("no_backup/state_guard_probe.bin");
            for(Path path:Arrays.asList(preferences,directory,privateFile))if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw new IllegalStateException("FIXTURE_PATH_MUST_BE_ABSENT");
            byte[] xml="<?xml version=\"1.0\" encoding=\"utf-8\"?><map><int name=\"probe\" value=\"7\" /></map>".getBytes(StandardCharsets.UTF_8);
            byte[] binary={0x46,0x54,0x47,0x44,0,1,2,3};byte[] local={9,8,7,6};
            Files.createDirectories(preferences.getParent());Files.createDirectories(directory);Files.createDirectory(directory.resolve("empty"));
            write(preferences,xml);write(directory.resolve("data.bin"),binary);write(privateFile,local);
            sync(preferences.getParent());sync(directory.resolve("empty"));sync(directory);sync(directory.getParent());sync(privateFile.getParent());
            if(!Arrays.equals(Files.readAllBytes(preferences),xml)||!Arrays.equals(Files.readAllBytes(directory.resolve("data.bin")),binary)||!Arrays.equals(Files.readAllBytes(privateFile),local)||!Files.isDirectory(directory.resolve("empty"),LinkOption.NOFOLLOW_LINKS))throw new IllegalStateException("FIXTURE_READBACK_FAILED");
            if(store.verify("state-guard-smoke",roots,hash).matches)throw new IllegalStateException("FIXTURE_MUTATION_NOT_DETECTED");
            StateGuardInstrumentation.requireStopped(context);
            MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update(xml);digest.update(binary);digest.update(local);
            StringBuilder fixtureHash=new StringBuilder();for(byte value:digest.digest())fixtureHash.append(String.format(Locale.ROOT,"%02x",value&255));
            result.putString("case_id","state-guard-smoke");result.putString("manifest_sha256",hash);result.putString("probe_sha256",fixtureHash.toString());result.putInt("probe_file_count",3);
            result.putString("status","PROBE_MUTATED_GUARD_RETAINED");result.putString("recovery","Force-stop the disposable target, then restore/verify/resolve this exact guard in a new helper process.");code=Activity.RESULT_OK;
        }catch(Throwable failure){
            String status="REFUSED_"+failure.getClass().getSimpleName();
            if(failure instanceof GuardStore.GuardFailure)status=failure.getMessage();
            if(failure instanceof IllegalStateException&&failure.getMessage()!=null&&failure.getMessage().matches("[A-Z_]+"))status=failure.getMessage();
            result.putString("status",status);result.putString("recovery","Any existing guard remains private. Do not continue a mutating suite.");
        }
        finish(code,result);
    }
    private static void write(Path path,byte[] bytes)throws java.io.IOException{
        try(FileChannel output=FileChannel.open(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())output.write(buffer);output.force(true);}
    }
    private static void sync(Path path)throws java.io.IOException{try(FileChannel channel=FileChannel.open(path,StandardOpenOption.READ)){channel.force(true);}}
}
