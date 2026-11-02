#!/usr/bin/env python3
"""Run layout-only policy/storage regressions and isolated native compilation."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
SOURCE=ROOT/'native/src/com/jeremykenedy/firetv/ui/nativeapp'
sys.path.insert(0,str(ROOT/'tests'))
from java_runtime import json_runtime
with tempfile.TemporaryDirectory(prefix='fire-tv-layoutschedule-') as temporary:
    target=Path(temporary)
    stubs={
      'android/content/Context.java':'''package android.content; public final class Context {private final java.io.File files;public Context(java.io.File file){files=file;files.mkdirs();}public java.io.File getFilesDir(){return files;}}''',
      'android/util/AtomicFile.java':'''package android.util; public final class AtomicFile {public static boolean failNext;private final java.io.File file,temp; public AtomicFile(java.io.File file){this.file=file;temp=new java.io.File(file+".new");} public java.io.FileInputStream openRead()throws java.io.IOException{return new java.io.FileInputStream(file);}public java.io.FileOutputStream startWrite()throws java.io.IOException{if(failNext){failNext=false;throw new java.io.IOException("Fixture write failure");}return new java.io.FileOutputStream(temp);}public void finishWrite(java.io.FileOutputStream output)throws java.io.IOException{output.close();java.nio.file.Files.move(temp.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}public void failWrite(java.io.FileOutputStream output)throws java.io.IOException{output.close();temp.delete();}}'''
    }
    files=[]
    for name,value in stubs.items():
        path=target/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(value);files.append(path)
    pure=[SOURCE/'layout/LayoutOptions.java',SOURCE/'layout/LayoutItem.java',*[p for p in (SOURCE/'layoutschedule').glob('*.java') if p.name not in ('LayoutScheduleActivity.java', 'LayoutScheduleController.java')]]
    jar=json_runtime()
    subprocess.run(['javac','-Xlint:all','-Werror','-cp',str(jar),'-d',str(target),*map(str,files+pure),str(HERE/'LayoutScheduleTest.java')],check=True)
    subprocess.run(['java','-cp',str(target)+os.pathsep+str(jar),'com.jeremykenedy.firetv.ui.nativeapp.layoutschedule.LayoutScheduleTest',str(target)],check=True)
    startup=target/'SourceStartup.java'
    startup.write_text('package com.jeremykenedy.firetv.ui.nativeapp; public final class SourceStartup { public static boolean redirectIfBlocked(android.app.Activity activity) { return false; } }')
    sdk=Path.home()/'Library/Android/sdk/platforms/android-36/android.jar'
    subprocess.run(['javac','-source','8','-target','8','-Xlint:-options','-bootclasspath',str(sdk),'-d',str(target/'android'),str(startup),*map(str,pure),str(SOURCE/'layout/LayoutPreferences.java'),str(SOURCE/'layoutschedule/LayoutScheduleActivity.java'),str(SOURCE/'layoutschedule/LayoutScheduleController.java'),str(HERE/'LayoutScheduleInstrumentation.java'),str(HERE/'DashboardHomeInstrumentation.java')],check=True)
print('Layout schedule host policy/storage checks and isolated API36 UI compilation passed.')
