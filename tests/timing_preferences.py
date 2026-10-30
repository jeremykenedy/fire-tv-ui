PREFERENCES = r"""package android.content;
import java.util.*;
public class SharedPreferences {
 public int value=8; public boolean failCommit; public Set<String> overrides=new HashSet<>();
 public int getInt(String key,int fallback){return value;}
 public Set<String> getStringSet(String key,Set<String> fallback){return new HashSet<>(overrides);}
 public Editor edit(){return new Editor();}
 public class Editor {
  int next=value; Set<String> strings=new HashSet<>(overrides);
  public Editor putInt(String key,int v){next=v;return this;}
  public Editor putStringSet(String key,Set<String> v){strings=new HashSet<>(v);return this;}
  public Editor remove(String key){if(key.equals("featured_rotation_seconds"))next=8;else strings.clear();return this;}
  public boolean commit(){if(failCommit)return false;value=next;overrides=strings;return true;}
 }
}
"""
