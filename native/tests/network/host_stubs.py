"""Minimal Android doubles for the network host behavior checks."""

SOURCES = {
    'android/content/Context.java': '''package android.content;
public class Context {
 public static final String CONNECTIVITY_SERVICE="connectivity";
 public Object service; public final android.content.pm.PackageManager pm=new android.content.pm.PackageManager();
 public Intent started; public boolean failStart;
 public final java.util.List<Intent> attempts=new java.util.ArrayList<>();
 public final java.util.Set<String> failedPackages=new java.util.HashSet<>();
 public Object getSystemService(String name){return service;}
 public android.content.pm.PackageManager getPackageManager(){return pm;}
 public String getPackageName(){return "fixture.launcher";}
 public Context getApplicationContext(){return this;}
 public int checkSelfPermission(String permission){return -1;}
 public void startActivity(Intent intent){attempts.add(intent);
  if(failStart||failedPackages.contains(intent.getComponent().getPackageName()))throw new SecurityException();started=intent;}
}''',
    'android/content/ComponentName.java': '''package android.content;
public class ComponentName { private final String pkg,name;
 public ComponentName(String pkg,String name){this.pkg=pkg;this.name=name;}
 public String getPackageName(){return pkg;} public String getClassName(){return name;}
 @Override public boolean equals(Object other){if(!(other instanceof ComponentName))return false;
  ComponentName value=(ComponentName)other;return pkg.equals(value.pkg)&&name.equals(value.name);}
 @Override public int hashCode(){return 31*pkg.hashCode()+name.hashCode();}
}''',
    'android/content/Intent.java': '''package android.content;
public class Intent {
 public static final String ACTION_MAIN="main",CATEGORY_HOME="home",ACTION_VIEW="view";
 public static final int FLAG_ACTIVITY_NEW_TASK=1; public final String action; private ComponentName component;
 public Intent(String action){this.action=action;} public Intent(String action,android.net.Uri uri){this(action);}
 public Intent(Intent source){action=source.action;component=source.component;}
 public Intent addCategory(String value){return this;} public Intent setPackage(String value){return this;}
 public Intent setComponent(ComponentName value){component=value;return this;} public ComponentName getComponent(){return component;}
 public Intent addFlags(int flags){return this;}
}''',
    'android/content/pm/ApplicationInfo.java': '''package android.content.pm;
public class ApplicationInfo { public static final int FLAG_SYSTEM=1,FLAG_UPDATED_SYSTEM_APP=2; public boolean enabled=true; public int flags; }
''',
    'android/content/pm/ActivityInfo.java': '''package android.content.pm;
public class ActivityInfo { public String packageName,name,permission; public boolean enabled=true,exported; public ApplicationInfo applicationInfo; }
''',
    'android/content/pm/ResolveInfo.java': '''package android.content.pm;
public class ResolveInfo { public ActivityInfo activityInfo; }
''',
    'android/content/pm/PackageManager.java': '''package android.content.pm;
public class PackageManager {
 public static final int PERMISSION_GRANTED=0,COMPONENT_ENABLED_STATE_DEFAULT=0,COMPONENT_ENABLED_STATE_ENABLED=1;
 private final java.util.Map<String,java.util.List<ResolveInfo>> routes=new java.util.HashMap<>();
 public void add(String action,ResolveInfo info){routes.computeIfAbsent(action,k->new java.util.ArrayList<>()).add(info);}
 public java.util.List<ResolveInfo> queryIntentActivities(android.content.Intent intent,int flags){return routes.getOrDefault(intent.action,java.util.Collections.emptyList());}
 public ResolveInfo resolveActivity(android.content.Intent intent,int flags){return null;}
 public int getComponentEnabledSetting(android.content.ComponentName component){return COMPONENT_ENABLED_STATE_DEFAULT;}
 public android.content.Intent getLeanbackLaunchIntentForPackage(String pkg){return null;}
 public android.content.Intent getLaunchIntentForPackage(String pkg){return null;}
}''',
    'android/provider/Settings.java': '''package android.provider;
public class Settings { public static final String ACTION_SETTINGS="settings", ACTION_WIFI_IP_SETTINGS="android.settings.WIFI_IP_SETTINGS",
 ACTION_WIFI_SETTINGS="android.settings.WIFI_SETTINGS", ACTION_WIRELESS_SETTINGS="android.settings.WIRELESS_SETTINGS"; }
''',
    'android/net/Uri.java': '''package android.net; public class Uri { public static Uri parse(String s){return new Uri();} }
''',
    'android/net/Network.java': '''package android.net; public class Network {}''',
    'android/net/NetworkCapabilities.java': '''package android.net;
public class NetworkCapabilities {
 public static final int TRANSPORT_VPN=4,TRANSPORT_ETHERNET=3,TRANSPORT_WIFI=1,TRANSPORT_CELLULAR=0,TRANSPORT_BLUETOOTH=2,NET_CAPABILITY_VALIDATED=16;
 private final int transport;private final boolean validated;
 public NetworkCapabilities(int transport,boolean validated){this.transport=transport;this.validated=validated;}
 public boolean hasTransport(int value){return transport==value;} public boolean hasCapability(int value){return validated&&value==NET_CAPABILITY_VALIDATED;}
}''',
    'android/net/LinkAddress.java': '''package android.net;
public class LinkAddress {private final java.net.InetAddress address;private final int prefix;
 public LinkAddress(String address,int prefix)throws Exception{this.address=java.net.InetAddress.getByName(address);this.prefix=prefix;}
 public java.net.InetAddress getAddress(){return address;}public int getPrefixLength(){return prefix;}}
''',
    'android/net/RouteInfo.java': '''package android.net;
public class RouteInfo {private final java.net.InetAddress address;private final boolean defaultRoute;
 public RouteInfo(String address,boolean defaultRoute)throws Exception{this.address=java.net.InetAddress.getByName(address);this.defaultRoute=defaultRoute;}
 public boolean isDefaultRoute(){return defaultRoute;}public java.net.InetAddress getGateway(){return address;}}
''',
    'android/net/LinkProperties.java': '''package android.net;
public class LinkProperties {public final java.util.List<LinkAddress> addresses=new java.util.ArrayList<>();
 public final java.util.List<RouteInfo> routes=new java.util.ArrayList<>();public final java.util.List<java.net.InetAddress> dns=new java.util.ArrayList<>();
 public java.util.List<LinkAddress> getLinkAddresses(){return addresses;}public java.util.List<RouteInfo> getRoutes(){return routes;}
 public java.util.List<java.net.InetAddress> getDnsServers(){return dns;}}
''',
    'android/net/ConnectivityManager.java': '''package android.net;
public class ConnectivityManager {public Network active; public boolean denied;
 public final java.util.Map<Network,NetworkCapabilities> networks=new java.util.LinkedHashMap<>();
 public final java.util.Map<Network,LinkProperties> links=new java.util.HashMap<>();
 public Network getActiveNetwork(){if(denied)throw new SecurityException();return active;}
 public Network[] getAllNetworks(){return networks.keySet().toArray(new Network[0]);}
 public NetworkCapabilities getNetworkCapabilities(Network n){return networks.get(n);}public LinkProperties getLinkProperties(Network n){return links.get(n);}}
''',
    'com/jeremykenedy/firetv/ui/nativeapp/device/HomeHandoffStore.java': '''package com.jeremykenedy.firetv.ui.nativeapp.device;
public class HomeHandoffStore {public HomeHandoffStore(android.content.Context context){}}
''',
    'com/jeremykenedy/firetv/ui/nativeapp/device/HomeRoutingPolicy.java': '''package com.jeremykenedy.firetv.ui.nativeapp.device;
public class HomeRoutingPolicy {public boolean allowHandoff=true; public int handoffs,cancellations;
 public HomeRoutingPolicy(HomeHandoffStore store){}public boolean beginHandoff(){handoffs++;return allowHandoff;}
 public void cancelHandoff(){cancellations++;} public void launcherVisibility(boolean visible){}}
''',
}
