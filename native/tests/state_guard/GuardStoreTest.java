package com.jeremykenedy.firetv.ui.nativeapp.stateguard;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class GuardStoreTest {
    private static int checks;
    private interface Action {void run() throws Exception;}
    public static void main(String[] args)throws Exception {
        Path app=Files.createTempDirectory("state-guard-test-");
        try{
            Set<String> roots=new LinkedHashSet<>(Arrays.asList("files","shared_prefs","no_backup"));
            Files.createDirectories(app.resolve("shared_prefs"));Files.createDirectories(app.resolve("files/empty"));
            Files.write(app.resolve("shared_prefs/preferences.xml"),new byte[]{0,1,2,3});Files.write(app.resolve("files/original"),new byte[]{8,9});
            GuardStore guard=new GuardStore(app.toFile());GuardStore.Result captured=guard.capture("case_one",roots);
            check(captured.matches&&captured.files==2&&captured.bytes==6,"Capture reports only hash/count metadata");
            check(guard.verify("case_one",roots,captured.hash).matches,"Original exact files and absence verify");
            reject(new Action(){@Override public void run()throws Exception{guard.capture("case_two",roots);}},"Unresolved guard blocks replacement");
            Files.write(app.resolve("shared_prefs/preferences.xml"),new byte[]{7});Files.delete(app.resolve("files/original"));
            Files.createDirectories(app.resolve("files/new/child"));Files.write(app.resolve("files/new/child/created"),new byte[]{6});
            check(!guard.verify("case_one",roots,captured.hash).matches,"Mutation is detected");
            GuardStore recreated=new GuardStore(app.toFile());check(recreated.restore("case_one",roots,captured.hash).matches,"New helper restores durable before-image");
            check(Arrays.equals(Files.readAllBytes(app.resolve("shared_prefs/preferences.xml")),new byte[]{0,1,2,3}),"Exact original bytes restored");
            check(Files.isDirectory(app.resolve("files/empty"))&&!Files.exists(app.resolve("files/new")),"Empty directory and new-file absence restored");
            reject(new Action(){@Override public void run()throws Exception{guard.restore("wrong_case",roots,captured.hash);}},"Wrong case cannot restore");
            reject(new Action(){@Override public void run()throws Exception{guard.restore("case_one",Collections.singleton("files"),captured.hash);}},"Wrong scope cannot restore");
            reject(new Action(){@Override public void run()throws Exception{guard.restore("case_one",roots,String.join("",Collections.nCopies(64,"0")));}},"Wrong hash cannot restore");
            // An interrupted restore keeps an immutable before-image and explicit stage marker.
            Path active=app.resolve("no_backup/native-test-state-guard-v1/active");Files.write(active.resolve("restore.pending"),captured.hash.getBytes("US-ASCII"));Files.write(app.resolve("files/original"),new byte[]{1});
            reject(new Action(){@Override public void run()throws Exception{guard.resolve("case_one",roots,captured.hash);}},"Pending restoration cannot be resolved");
            guard.restore("case_one",roots,captured.hash);check(!Files.exists(active.resolve("restore.pending")),"Repeated interrupted restore clears journal after exact verification");
            // File/directory substitutions are confined and recoverable.
            Files.delete(app.resolve("files/original"));Files.createDirectories(app.resolve("files/original/nested"));Files.write(app.resolve("files/original/nested/new"),new byte[]{1});
            guard.restore("case_one",roots,captured.hash);check(Arrays.equals(Files.readAllBytes(app.resolve("files/original")),new byte[]{8,9}),"New directory replacing original file is recovered");
            Files.delete(app.resolve("files/empty"));Files.write(app.resolve("files/empty"),new byte[]{1});guard.restore("case_one",roots,captured.hash);check(Files.isDirectory(app.resolve("files/empty")),"New file replacing original empty directory is recovered");
            Path blob=active.resolve("blobs/f00000000");Files.write(blob,new byte[]{4});Files.write(app.resolve("files/original"),new byte[]{5});
            reject(new Action(){@Override public void run()throws Exception{guard.restore("case_one",roots,captured.hash);}},"Corrupt saved bytes stop before mutation");
            check(Arrays.equals(Files.readAllBytes(app.resolve("files/original")),new byte[]{5}),"Failed validation does not touch live files");
        }finally{remove(app);}
        Path symlinkApp=Files.createTempDirectory("state-guard-symlink-");Path outside=Files.createTempDirectory("state-guard-outside-");
        try{
            Files.createDirectories(symlinkApp.resolve("files"));Files.write(outside.resolve("private"),new byte[]{9});Files.createSymbolicLink(symlinkApp.resolve("files/escape"),outside);
            reject(new Action(){@Override public void run()throws Exception{new GuardStore(symlinkApp.toFile()).capture("symlink",Collections.singleton("files"));}},"Symlink escape is refused");
            check(Files.readAllBytes(outside.resolve("private"))[0]==9,"Outside data untouched");
        }finally{remove(symlinkApp);remove(outside);}
        Path absent=Files.createTempDirectory("state-guard-absence-");
        try{
            Set<String> roots=new HashSet<>(Arrays.asList("files","no_backup"));GuardStore guard=new GuardStore(absent.toFile());GuardStore.Result captured=guard.capture("absent",roots);
            Files.createDirectories(absent.resolve("files"));Files.write(absent.resolve("files/new"),new byte[]{1});guard.restore("absent",roots,captured.hash);guard.resolve("absent",roots,captured.hash);
            check(!Files.exists(absent.resolve("files"))&&!Files.exists(absent.resolve("no_backup")),"Original root absence restored after explicit resolution");
        }finally{remove(absent);}
        Path malformed=Files.createTempDirectory("state-guard-malformed-");
        try{
            Files.createDirectory(malformed.resolve("files"));Files.write(malformed.resolve("files/kept"),new byte[]{9});
            Set<String> roots=Collections.singleton("files");GuardStore guard=new GuardStore(malformed.toFile());guard.capture("malformed",roots);
            Path manifest=malformed.resolve("no_backup/native-test-state-guard-v1/active/manifest.bin");
            byte[] traversal=malformedManifest("../outside",false);Files.write(manifest,traversal);
            reject(new Action(){@Override public void run()throws Exception{guard.restore("malformed",roots,hash(traversal));}},"Manifest traversal is rejected before restoration");
            byte[] duplicate=malformedManifest("duplicate",true);Files.write(manifest,duplicate);
            reject(new Action(){@Override public void run()throws Exception{guard.restore("malformed",roots,hash(duplicate));}},"Duplicate manifest paths are rejected");
            check(Files.readAllBytes(malformed.resolve("files/kept"))[0]==9,"Malformed guard never mutates live data");
        }finally{remove(malformed);}
        Path oversized=Files.createTempDirectory("state-guard-bounds-");
        try{
            Files.createDirectory(oversized.resolve("files"));try(RandomAccessFile file=new RandomAccessFile(oversized.resolve("files/large").toFile(),"rw")){file.setLength(32L*1024*1024+1);}
            reject(new Action(){@Override public void run()throws Exception{new GuardStore(oversized.toFile()).capture("oversized",Collections.singleton("files"));}},"Oversized state fails before guard or app mutation");
            check(Files.size(oversized.resolve("files/large"))==32L*1024*1024+1,"Rejected source file remains intact");
        }finally{remove(oversized);}
        System.out.println("Private state guard checks passed: "+checks);
    }
    private static byte[] malformedManifest(String path,boolean duplicate)throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream output=new DataOutputStream(bytes);
        output.writeInt(0x46544744);output.writeInt(1);output.writeUTF("malformed");output.writeInt(1);output.writeUTF("files");output.writeByte(1);output.writeInt(duplicate?2:1);
        for(int i=0;i<(duplicate?2:1);i++){output.writeUTF("files");output.writeUTF(path);output.writeByte(0);output.writeLong(0);output.writeUTF(hash(new byte[0]));}
        output.flush();return bytes.toByteArray();
    }
    private static String hash(byte[] bytes)throws Exception {StringBuilder result=new StringBuilder();for(byte value:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))result.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return result.toString();}
    private static void reject(Action action,String message)throws Exception{try{action.run();throw new AssertionError(message);}catch(IOException expected){checks++;}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static void remove(Path path)throws IOException{if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))try(DirectoryStream<Path> children=Files.newDirectoryStream(path)){for(Path child:children)remove(child);}Files.deleteIfExists(path);}
}
