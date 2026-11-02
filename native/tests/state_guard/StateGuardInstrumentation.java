package com.jeremykenedy.firetv.ui.nativeapp.stateguard;

import android.app.*;
import android.content.Context;
import android.os.*;
import java.security.KeyStore;
import java.util.*;

/** Run only after the operator stops the target and pauses every other writer. */
public final class StateGuardInstrumentation extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle args){super.onCreate(args);arguments=args==null?new Bundle():args;start();}
    @Override public void onStart(){
        Bundle result=new Bundle();result.putInt("pid",android.os.Process.myPid());int code=Activity.RESULT_CANCELED;
        try{
            Context context=getTargetContext();
            if(!"true".equals(arguments.getString("quiescent")))throw new IllegalStateException("QUIESCENCE_ACK_REQUIRED");
            requireStopped(context);
            KeyStore keys=KeyStore.getInstance("AndroidKeyStore");keys.load(null);
            int aliases=0;for(Enumeration<String> names=keys.aliases();names.hasMoreElements();){names.nextElement();aliases++;}
            result.putInt("keystore_alias_count",aliases);
            if(aliases!=0)throw new IllegalStateException("NONEXPORTABLE_KEYSTORE_STATE_REFUSED");
            String operation=arguments.getString("operation"),id=arguments.getString("case_id"),rootArgument=arguments.getString("roots");
            if(rootArgument==null)throw new IllegalStateException("EXPLICIT_ROOTS_REQUIRED");
            Set<String> roots=new LinkedHashSet<>();for(String root:rootArgument.split(",",-1))if(!roots.add(root))throw new IllegalStateException("DUPLICATE_ROOT_REFUSED");
            if(!roots.equals(new HashSet<>(Arrays.asList("shared_prefs","files","no_backup"))))throw new IllegalStateException("ALL_THREE_REVIEWED_ROOTS_REQUIRED");
            GuardStore store=new GuardStore(context.getDataDir());GuardStore.Result guard;
            String hash=arguments.getString("manifest_sha256");
            if("capture".equals(operation))guard=store.capture(id,roots);
            else if("verify".equals(operation))guard=store.verify(id,roots,hash);
            else if("restore".equals(operation))guard=store.restore(id,roots,hash);
            else if("resolve".equals(operation))guard=store.resolve(id,roots,hash);
            else throw new IllegalStateException("UNKNOWN_OPERATION_REFUSED");
            requireStopped(context);
            result.putString("case_id",guard.caseId);result.putString("manifest_sha256",guard.hash);
            result.putInt("file_count",guard.files);result.putLong("byte_count",guard.bytes);result.putBoolean("matches",guard.matches);
            result.putString("status",guard.matches?"VERIFIED":"DIFFERS_GUARD_RETAINED");
            result.putString("keystore","verified empty; no key material was accessed or exported");
            result.putString("scope","explicit app roots only; global settings and runtime URI permissions are separate operator guards");
            code=Activity.RESULT_OK;
        }catch(Throwable failure){
            String status="REFUSED_"+failure.getClass().getSimpleName();
            if(failure instanceof GuardStore.GuardFailure)status=failure.getMessage();
            if(failure instanceof IllegalStateException&&failure.getMessage()!=null&&failure.getMessage().matches("[A-Z_]+"))status=failure.getMessage();
            result.putString("status",status);result.putString("recovery","Any existing private guard was retained. Do not run a mutating suite until reviewed.");
        }
        finish(code,result);
    }
    static void requireStopped(Context context){
        ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        if(manager==null)throw new IllegalStateException("ACTIVITY_MANAGER_UNAVAILABLE");
        String name=context.getPackageName();
        for(ActivityManager.RunningTaskInfo task:manager.getRunningTasks(64))if(task.numActivities>0&&((task.topActivity!=null&&name.equals(task.topActivity.getPackageName()))||(task.baseActivity!=null&&name.equals(task.baseActivity.getPackageName()))))throw new IllegalStateException("TARGET_ACTIVITIES_MUST_BE_STOPPED");
        List<ActivityManager.RunningAppProcessInfo> processes=manager.getRunningAppProcesses();
        if(processes==null)throw new IllegalStateException("PROCESS_STATE_UNAVAILABLE");
        for(ActivityManager.RunningAppProcessInfo process:processes)if(process.uid==android.os.Process.myUid()&&process.pid!=android.os.Process.myPid())throw new IllegalStateException("OTHER_TARGET_PROCESS_MUST_BE_STOPPED");
        for(ActivityManager.RunningServiceInfo service:manager.getRunningServices(64))if(service.uid==android.os.Process.myUid())throw new IllegalStateException("TARGET_SERVICES_MUST_BE_STOPPED");
    }
}
