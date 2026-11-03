#!/usr/bin/env python3
"""Exercise the real receiver dispatch with bounded Android host stubs."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
stubs = {
    'android/content/BroadcastReceiver.java': 'package android.content; public abstract class BroadcastReceiver { public abstract void onReceive(Context c,Intent i); public PendingResult goAsync(){return new PendingResult();} public static class PendingResult {public void finish(){}} }',
    'android/content/Intent.java': 'package android.content; public class Intent {public static final String ACTION_BOOT_COMPLETED="boot",ACTION_MY_PACKAGE_REPLACED="replace",ACTION_MAIN="main";public static final int FLAG_ACTIVITY_NEW_TASK=1,FLAG_ACTIVITY_CLEAR_TOP=2,FLAG_ACTIVITY_SINGLE_TOP=4;private String action;public Intent(String a){action=a;}public String getAction(){return action;}public Intent setComponent(ComponentName c){return this;}public Intent addFlags(int f){return this;}}',
    'android/content/ComponentName.java': 'package android.content; public class ComponentName {public ComponentName(String p,String n){}}',
    'android/content/Context.java': 'package android.content; public class Context {public static final int MODE_PRIVATE=0;public int launches;public String getPackageName(){return "com.jeremykenedy.firetv.ui";}public SharedPreferences getSharedPreferences(String n,int m){return new SharedPreferences();}public void startActivity(Intent i){launches++;}}',
    'android/content/SharedPreferences.java': 'package android.content; public class SharedPreferences {public java.util.Map<String,Object> getAll(){return new java.util.HashMap<>();}}',
    'android/os/Looper.java': 'package android.os; public class Looper {public static Looper getMainLooper(){return new Looper();}}',
    'android/os/Handler.java': 'package android.os; public class Handler {public Handler(Looper l){}public void removeCallbacks(Runnable r){}public void postDelayed(Runnable r,int m){}}',
    'com/jeremykenedy/firetv/ui/nativeapp/SourceStartup.java': 'package com.jeremykenedy.firetv.ui.nativeapp; public class SourceStartup {public static int starts,protection;public static boolean ready=true;public static void start(android.content.Context c,Runnable r){starts++;r.run();}public static boolean ready(android.content.Context c){return ready;}public static void startProtection(android.content.Context c,Runnable r,java.util.concurrent.atomic.AtomicBoolean e){protection++;r.run();}}',
    'com/jeremykenedy/firetv/ui/nativeapp/device/HomeCommandHandler.java': 'package com.jeremykenedy.firetv.ui.nativeapp.device; public class HomeCommandHandler {public static int recoveries;public static boolean recover(android.content.Context c){recoveries++;return true;}}',
    'com/jeremykenedy/firetv/ui/nativeapp/device/HomeSetupPolicy.java': 'package com.jeremykenedy.firetv.ui.nativeapp.device; public class HomeSetupPolicy {public static boolean launchOnBoot(String p,java.util.Map<String,Object> v){return true;}}',
    'ReceiverTest.java': r'''import android.content.*;
import com.jeremykenedy.firetv.ui.DeviceBootReceiver;
import com.jeremykenedy.firetv.ui.nativeapp.SourceStartup;
import com.jeremykenedy.firetv.ui.nativeapp.device.HomeCommandHandler;
public final class ReceiverTest {
    public static void main(String[] args) {
        DeviceBootReceiver receiver=new DeviceBootReceiver(); Context context=new Context();
        receiver.onReceive(context,new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));
        check(SourceStartup.protection==1 && context.launches==0 && HomeCommandHandler.recoveries==0,"Replacement must restore protection without Home recovery or launch even when boot opt-in is true");
        receiver.onReceive(context,new Intent(Intent.ACTION_BOOT_COMPLETED));
        check(SourceStartup.protection==2 && context.launches==1 && HomeCommandHandler.recoveries==1,"Boot must preserve the existing opted-in Home path");
        int starts=SourceStartup.starts;
        receiver.onReceive(context,new Intent("other")); receiver.onReceive(context,null);
        check(SourceStartup.starts==starts,"Unrelated actions must have no startup effect");
        SourceStartup.ready=false;
        receiver.onReceive(context,new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));
        check(SourceStartup.protection==2 && context.launches==1,"Blocked startup must not restore protection or launch Home");
        System.out.println("Own replacement restores protection only; boot routing and blocked startup preserved.");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}''',
}
with tempfile.TemporaryDirectory(prefix='fire-tv-boot-receiver-') as folder:
    work = Path(folder)
    sources = []
    for name, text in stubs.items():
        path = work / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_text(text); sources.append(str(path))
    sources.append(str(root / 'production-src/com/jeremykenedy/firetv/ui/DeviceBootReceiver.java'))
    subprocess.run(['javac', '-Xlint:all', '-Werror', '-d', str(work / 'classes'), *sources], check=True)
    subprocess.run(['java', '-cp', str(work / 'classes'), 'ReceiverTest'], check=True)
