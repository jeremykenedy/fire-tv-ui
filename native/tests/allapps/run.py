#!/usr/bin/env python3
"""Exercise the actual inventory source against deterministic package-manager fixtures."""
from pathlib import Path
import subprocess
import tempfile

here = Path(__file__).resolve().parent
source = here.parents[1] / 'src/com/jeremykenedy/firetv/ui/nativeapp'
stubs = {
'android/graphics/drawable/Drawable.java': 'package android.graphics.drawable; public class Drawable {}',
'android/content/ComponentName.java': '''package android.content; public class ComponentName {public final String pkg,name;public ComponentName(String p,String n){pkg=p;name=n;}public String flattenToString(){return pkg+"/"+name;}}''',
'android/content/Intent.java': '''package android.content; public class Intent {public static final String ACTION_MAIN="main",CATEGORY_LEANBACK_LAUNCHER="tv",CATEGORY_LAUNCHER="ordinary";public String category;public ComponentName component;public Intent(String action){}public Intent addCategory(String c){category=c;return this;}public Intent setComponent(ComponentName c){component=c;return this;}}''',
'android/content/Context.java': '''package android.content; import android.content.pm.PackageManager; public class Context {public final PackageManager pm=new PackageManager();public PackageManager getPackageManager(){return pm;}public String getPackageName(){return "preview.app";}public int checkSelfPermission(String p){return -1;}}''',
'android/content/pm/ApplicationInfo.java': '''package android.content.pm;public class ApplicationInfo {public boolean enabled=true;}''',
'android/content/pm/ActivityInfo.java': '''package android.content.pm; import android.graphics.drawable.Drawable; public class ActivityInfo {public String packageName,name,permission;public boolean enabled=true,exported=true;public ApplicationInfo applicationInfo=new ApplicationInfo();public Drawable loadBanner(PackageManager p){return null;}}''',
'android/content/pm/ResolveInfo.java': '''package android.content.pm;import android.graphics.drawable.Drawable;public class ResolveInfo {public ActivityInfo activityInfo=new ActivityInfo();public String label;public CharSequence loadLabel(PackageManager p){return label;}public Drawable loadIcon(PackageManager p){return new Drawable();}}''',
'android/content/pm/PackageManager.java': '''package android.content.pm; import android.content.*;import java.util.*;public class PackageManager {public static final int PERMISSION_GRANTED=0,COMPONENT_ENABLED_STATE_DEFAULT=0,COMPONENT_ENABLED_STATE_ENABLED=1;public String removed,disabledComponent;private final Map<String,List<ResolveInfo>> groups=new HashMap<>();public ResolveInfo add(String category,String pkg,String name,String label){ResolveInfo value=new ResolveInfo();value.activityInfo.packageName=pkg;value.activityInfo.name=name;value.label=label;groups.computeIfAbsent(category,k->new ArrayList<>()).add(value);return value;}public List<ResolveInfo> queryIntentActivities(Intent i,int flags){return groups.getOrDefault(i.category,Collections.emptyList());}public ActivityInfo getActivityInfo(ComponentName c,int f)throws NameNotFoundException {if(c.flattenToString().equals(removed))throw new NameNotFoundException();for(List<ResolveInfo> group:groups.values())for(ResolveInfo i:group)if((i.activityInfo.packageName+"/"+i.activityInfo.name).equals(c.flattenToString()))return i.activityInfo;throw new NameNotFoundException();}public int getComponentEnabledSetting(ComponentName c){return c.flattenToString().equals(disabledComponent)?2:0;}public static class NameNotFoundException extends Exception {}}'''
}
with tempfile.TemporaryDirectory(prefix='fire-tv-allapps-') as folder:
    work = Path(folder)
    for name, content in stubs.items():
        file = work / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(content)
    subprocess.run(['javac', '-d', work, *[work / name for name in stubs], source / 'appgrid/AppInventory.java', here / 'AppInventoryTest.java'], check=True)
    subprocess.run(['java', '-cp', work, 'com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppInventoryTest'], check=True)
