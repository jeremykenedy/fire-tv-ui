package com.jeremykenedy.firetv.ui.nativeapp.backup;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class OriginalBackupTest {
    private static int checks;
    private static OriginalBackup decode(String text) {return OriginalBackup.decode(text.getBytes(StandardCharsets.UTF_8));}
    private static void check(boolean valid){checks++;if(!valid)throw new AssertionError("Original backup check "+checks);}
    private static void rejects(String text){try{decode(text);throw new AssertionError("Accepted invalid original backup");}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args){
        OriginalBackup valid=decode("{\"apps_per_row\":4,\"use_24h_clock\":true,\"featured_card_order\":[\"one\",\"two\"]}");
        check(valid.unsupported.isEmpty());check(Integer.valueOf(4).equals(valid.settings.values().get("apps_per_row")));
        check(valid.settings.values().get("featured_card_order") instanceof Set);
        check(valid.summary().contains("apps per row"));
        OriginalBackup blocked=decode("{\"apps_per_row\":4,\"folder_lock_pin\":\"secret\",\"fire_tv_ui_sleep\":60,\"unknown_key\":false}");
        check(blocked.unsupported.equals(new TreeSet<>(Arrays.asList("folder_lock_pin","fire_tv_ui_sleep","unknown_key"))));
        check(!blocked.summary().contains("secret"));check(blocked.summary().contains("Unsupported settings"));
        check(decode("{\"format\":\"portable\"}")==null);
        rejects("{}");rejects("{\"apps_per_row\":4.5}");rejects("{\"apps_per_row\":true}");
        rejects("{\"use_24h_clock\":1}");rejects("{\"featured_card_order\":[\"one\",\"one\"]}");
        rejects("{\"apps_per_row\":4,\"apps_per_row\":5}");rejects("{\"unexpected\\nname\":true}");
        System.out.println("Original backup checks passed: "+checks);
    }
}
