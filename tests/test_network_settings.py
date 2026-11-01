from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class NetworkSettingsChecks(unittest.TestCase):
    def test_local_addresses_vpn_separation_and_available_settings_routes(self):
        stubs = {
            'android/content/Context.java': '''package android.content;
public class Context {
 public static final String CONNECTIVITY_SERVICE="connectivity";
 public Object service; public android.content.pm.PackageManager pm=new android.content.pm.PackageManager();
 public String started; public java.util.Set<String> blocked=new java.util.HashSet<>();
 public Object getSystemService(String name){return service;}
 public android.content.pm.PackageManager getPackageManager(){return pm;}
 public int checkSelfPermission(String permission){return -1;}
 public void startActivity(Intent intent){if(blocked.contains(intent.action))throw new SecurityException();started=intent.action;}
}''',
            'android/content/Intent.java': '''package android.content;
public class Intent { public final String action; public Intent(String a){action=a;} }''',
            'android/content/ActivityNotFoundException.java': '''package android.content;
public class ActivityNotFoundException extends RuntimeException {}''',
            'android/content/pm/ApplicationInfo.java': '''package android.content.pm;
public class ApplicationInfo { public boolean enabled=true; }''',
            'android/content/pm/ActivityInfo.java': '''package android.content.pm;
public class ActivityInfo { public boolean enabled=true,exported=true; public String permission;
public ApplicationInfo applicationInfo=new ApplicationInfo(); }''',
            'android/content/pm/ResolveInfo.java': '''package android.content.pm;
public class ResolveInfo { public ActivityInfo activityInfo=new ActivityInfo(); }''',
            'android/content/pm/PackageManager.java': '''package android.content.pm;
public class PackageManager { public static final int MATCH_DEFAULT_ONLY=1,PERMISSION_GRANTED=0;
 public java.util.Map<String,ResolveInfo> routes=new java.util.HashMap<>();
 public ResolveInfo resolveActivity(android.content.Intent i,int flags){return routes.get(i.action);} }''',
            'android/net/Network.java': '''package android.net;
public class Network { public final int id; public Network(int id){this.id=id;} }''',
            'android/net/NetworkCapabilities.java': '''package android.net;
public class NetworkCapabilities { public static final int TRANSPORT_VPN=4,TRANSPORT_ETHERNET=3,TRANSPORT_WIFI=1;
 public final int transport; public NetworkCapabilities(int t){transport=t;} public boolean hasTransport(int t){return t==transport;} }''',
            'android/net/NetworkInfo.java': '''package android.net;
public class NetworkInfo { public boolean connected=true; public boolean isConnected(){return connected;} }''',
            'android/net/LinkAddress.java': '''package android.net;
public class LinkAddress { public final java.net.InetAddress address; public final int prefix;
public LinkAddress(String ip,int p)throws Exception{address=java.net.InetAddress.getByName(ip);prefix=p;}
public java.net.InetAddress getAddress(){return address;}public int getPrefixLength(){return prefix;} }''',
            'android/net/RouteInfo.java': '''package android.net;
public class RouteInfo { public final java.net.InetAddress gateway; public boolean defaultRoute;
 public RouteInfo(String ip,boolean d)throws Exception{gateway=java.net.InetAddress.getByName(ip);defaultRoute=d;}
 public boolean isDefaultRoute(){return defaultRoute;}public java.net.InetAddress getGateway(){return gateway;} }''',
            'android/net/LinkProperties.java': '''package android.net;
public class LinkProperties {
public java.util.List<LinkAddress> addresses=new java.util.ArrayList<>();
public java.util.List<RouteInfo> routes=new java.util.ArrayList<>();
public java.util.List<java.net.InetAddress> dns=new java.util.ArrayList<>();
public java.util.List<LinkAddress> getLinkAddresses(){return addresses;}
public java.util.List<RouteInfo> getRoutes(){return routes;}
public java.util.List<java.net.InetAddress> getDnsServers(){return dns;} }''',
            'android/net/ConnectivityManager.java': '''package android.net;
public class ConnectivityManager {
public Network active;public boolean denied;
public java.util.Map<Network,NetworkCapabilities> caps=new java.util.LinkedHashMap<>();
public java.util.Map<Network,LinkProperties> links=new java.util.HashMap<>();
public java.util.Map<Network,NetworkInfo> infos=new java.util.HashMap<>();
public Network getActiveNetwork(){if(denied)throw new SecurityException();return active;}
public Network[] getAllNetworks(){return caps.keySet().toArray(new Network[0]);}
public NetworkCapabilities getNetworkCapabilities(Network n){return caps.get(n);}
public NetworkInfo getNetworkInfo(Network n){return infos.get(n);}
public LinkProperties getLinkProperties(Network n){return links.get(n);} }''',
            'com/jeremykenedy/firetv/ui/NetworkSettingsTest.java': '''package com.jeremykenedy.firetv.ui;
import android.net.*;import android.content.*;import android.content.pm.*;
public class NetworkSettingsTest {
static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
public static void main(String[] args)throws Exception{
 Context c=new Context();check(NetworkStatus.read(c).details.contains("unavailable"),"missing manager");
 ConnectivityManager m=new ConnectivityManager();c.service=m;
 check(NetworkStatus.read(c).details.contains("No connected"),"offline state");
 Network wifi=new Network(1),vpn=new Network(2),eth=new Network(3);
 m.caps.put(wifi,new NetworkCapabilities(1));m.infos.put(wifi,new NetworkInfo());
 LinkProperties p=new LinkProperties();p.addresses.add(new LinkAddress("192.0.2.10",24));p.addresses.add(new LinkAddress("2001:db8::10",64));p.addresses.add(new LinkAddress("127.0.0.1",8));
 p.routes.add(new RouteInfo("192.0.2.1",true));p.routes.add(new RouteInfo("0.0.0.0",true));p.routes.add(new RouteInfo("192.0.2.2",false));p.dns.add(java.net.InetAddress.getByName("192.0.2.53"));m.links.put(wifi,p);m.active=wifi;
 String d=NetworkStatus.read(c).details;check(d.contains("192.0.2.10/24")&&d.contains("/64")&&d.contains("current connection"),"real IPv4/IPv6 prefixes");
 check(d.contains("Gateway: 192.0.2.1")&&d.contains("DNS: 192.0.2.53")&&!d.contains("127.0.0.1")&&!d.contains("192.0.2.2"),"only actual default gateway and local interface addresses");
 m.caps.put(vpn,new NetworkCapabilities(4));m.infos.put(vpn,new NetworkInfo());LinkProperties tunnel=new LinkProperties();tunnel.addresses.add(new LinkAddress("10.9.0.1",32));m.links.put(vpn,tunnel);m.active=vpn;
 d=NetworkStatus.read(c).details;check(d.contains("VPN is active")&&!d.contains("10.9.0.1")&&!d.contains("current connection"),"VPN excluded without mislabeling physical default");
 m.caps.put(eth,new NetworkCapabilities(3));m.infos.put(eth,new NetworkInfo());m.links.put(eth,new LinkProperties());check(NetworkStatus.read(c).ethernet&&NetworkStatus.read(c).details.contains("Not reported"),"multiple links and missing fields");
 m.infos.get(eth).connected=false;check(!NetworkStatus.read(c).ethernet,"disconnected Ethernet excluded");
 m.denied=true;check(NetworkStatus.read(c).details.contains("did not allow"),"permission failure explained");
 check(!NetworkSettings.openAvailable(c,new String[]{"missing"})&&c.started==null,"no handler never claims opened");
 ResolveInfo privateActivity=new ResolveInfo();privateActivity.activityInfo.exported=false;c.pm.routes.put("private",privateActivity);
 ResolveInfo permission=new ResolveInfo();permission.activityInfo.permission="restricted";c.pm.routes.put("restricted",permission);
 c.pm.routes.put("blocked",new ResolveInfo());c.blocked.add("blocked");c.pm.routes.put("valid",new ResolveInfo());
 check(NetworkSettings.openAvailable(c,new String[]{"private","restricted","blocked","valid"})&&"valid".equals(c.started),"unexported restricted and failed routes fall back");
 System.out.println("Network status, VPN isolation, missing fields, permission errors and route fallback passed");
}}
''',
        }
        with tempfile.TemporaryDirectory() as directory:
            files = []
            for name, content in stubs.items():
                path = Path(directory) / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
                files.append(str(path))
            sources = ROOT / 'app/src/com/jeremykenedy/firetv/ui'
            subprocess.run(['javac', '-d', directory, *files, str(sources / 'NetworkStatus.java'),
                            str(sources / 'NetworkSettings.java')], check=True)
            subprocess.run(['java', '-cp', directory, 'com.jeremykenedy.firetv.ui.NetworkSettingsTest'], check=True)
