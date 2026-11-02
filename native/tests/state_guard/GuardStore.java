package com.jeremykenedy.firetv.ui.nativeapp.stateguard;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Test-only private before-image. Paths in the manifest are relative to fixed app roots. */
public final class GuardStore {
    private static final int MAGIC=0x46544744, MAX_FILES=8192, MAX_MANIFEST=4*1024*1024;
    private static final long MAX_FILE=32L*1024*1024, MAX_TOTAL=256L*1024*1024;
    private static final String DIRECTORY="native-test-state-guard-v1";
    private final Path data,home,active;
    private final Map<String,Path> allowed=new LinkedHashMap<>();
    public GuardStore(File dataDirectory) throws IOException {
        Path requested=dataDirectory.toPath().toAbsolutePath().normalize();
        if(!Files.isDirectory(requested,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(requested))throw fail("Invalid app data root.");
        data=requested.toRealPath();
        allowed.put("shared_prefs",data.resolve("shared_prefs"));allowed.put("files",data.resolve("files"));allowed.put("no_backup",data.resolve("no_backup"));
        home=allowed.get("no_backup").resolve(DIRECTORY);active=home.resolve("active");
        for(Path root:allowed.values())safe(root);
    }
    public static final class Result {
        public final String caseId,hash; public final int files; public final long bytes; public final boolean matches;
        Result(String id,String hash,int files,long bytes,boolean matches){caseId=id;this.hash=hash;this.files=files;this.bytes=bytes;this.matches=matches;}
    }
    private static final class Entry {
        final String root,path;final boolean directory;final long size;final String hash;
        Entry(String root,String path,boolean directory,long size,String hash){this.root=root;this.path=path;this.directory=directory;this.size=size;this.hash=hash;}
        String key(){return root+"/"+path;}
        @Override public boolean equals(Object other){if(!(other instanceof Entry))return false;Entry e=(Entry)other;return root.equals(e.root)&&path.equals(e.path)&&directory==e.directory&&size==e.size&&hash.equals(e.hash);}
        @Override public int hashCode(){return key().hashCode();}
    }
    private static final class Manifest {
        final String id;final LinkedHashMap<String,Boolean> roots;final List<Entry> entries;
        Manifest(String id,LinkedHashMap<String,Boolean> roots,List<Entry> entries){this.id=id;this.roots=roots;this.entries=entries;}
    }
    public Result capture(String id,Set<String> roots) throws IOException {
        validateId(id);validateRoots(roots);
        Manifest before=scan(id,roots,null);
        try(Lock lock=lock()) {
            lock.requireHeld();
            if(Files.exists(active,LinkOption.NOFOLLOW_LINKS))throw fail("An unresolved guard already exists.");
            Files.createDirectory(active);Files.createDirectory(active.resolve("blobs"));
            int index=0;
            for(Entry entry:before.entries)if(!entry.directory){Path copy=blob(index++);copy(target(entry),copy);if(!digest(copy).equals(entry.hash))throw fail("Source changed during capture.");}
            Manifest after=scan(id,roots,before.roots);
            if(!same(before,after))throw fail("Source changed during capture. The guard is unresolved.");
            byte[] manifest=encode(before);writeNew(active.resolve("manifest.bin"),manifest);
            sync(active.resolve("blobs"));sync(active);sync(home);
            Manifest checked=read(id,roots,hex(manifest));validateCopies(checked);
            if(!same(before,scan(id,roots,before.roots)))throw fail("Source changed before capture verification.");
            return result(before,hex(manifest),true);
        }
    }
    public Result verify(String id,Set<String> roots,String expectedHash) throws IOException {
        try(Lock lock=lock()){lock.requireHeld();Manifest before=read(id,roots,expectedHash);validateCopies(before);return result(before,expectedHash,same(before,scan(id,roots,before.roots)));}
    }
    public Result restore(String id,Set<String> roots,String expectedHash) throws IOException {
        try(Lock lock=lock()) {
            lock.requireHeld();
            Manifest before=read(id,roots,expectedHash);validateCopies(before);
            Manifest current=scan(id,roots,before.roots);
            // All current paths and every saved byte are validated before the first mutation.
            Map<String,Entry> expected=new HashMap<>();for(Entry entry:before.entries)expected.put(entry.key(),entry);
            Path pending=active.resolve("restore.pending");
            if(Files.exists(pending,LinkOption.NOFOLLOW_LINKS))validatePending(expectedHash);
            else {writeNew(pending,expectedHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII));sync(active);}
            List<Entry> reverse=new ArrayList<>(current.entries);Collections.reverse(reverse);
            for(Entry entry:reverse){Entry original=expected.get(entry.key());if(original==null||original.directory!=entry.directory)Files.delete(target(entry));}
            int index=0;
            for(Map.Entry<String,Boolean> root:before.roots.entrySet())if(root.getValue())directory(allowed.get(root.getKey()));
            for(Entry entry:before.entries) {
                if(entry.directory)directory(target(entry));
                else{Path destination=target(entry);directory(destination.getParent());Path temporary=destination.resolveSibling(".state-guard-restore-"+UUID.randomUUID());copy(blob(index++),temporary);if(!digest(temporary).equals(entry.hash))throw fail("Restore copy could not be verified.");Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);sync(destination.getParent());}
            }
            for(Map.Entry<String,Boolean> root:before.roots.entrySet())if(!root.getValue()&&!root.getKey().equals("no_backup"))Files.deleteIfExists(allowed.get(root.getKey()));
            if(!same(before,scan(id,roots,before.roots)))throw fail("Restoration could not be verified. Guard retained.");
            Files.delete(pending);sync(active);
            return result(before,expectedHash,true);
        }
    }
    public Result resolve(String id,Set<String> roots,String expectedHash) throws IOException {
        Result result;boolean removeNoBackup;
        try(Lock lock=lock()) {
            lock.requireHeld();
            Manifest before=read(id,roots,expectedHash);validateCopies(before);
            if(Files.exists(active.resolve("restore.pending"),LinkOption.NOFOLLOW_LINKS))throw fail("An interrupted restore must finish before resolving.");
            if(!same(before,scan(id,roots,before.roots)))throw fail("Current state differs from guard; restore before resolving.");
            result=result(before,expectedHash,true);removeNoBackup=Boolean.FALSE.equals(before.roots.get("no_backup"));
            deleteGuard(active);sync(home);
        }
        // The operator must keep the target quiescent until this final cleanup completes.
        Files.deleteIfExists(home.resolve("lock"));Files.delete(home);
        if(removeNoBackup)Files.deleteIfExists(allowed.get("no_backup"));
        return result;
    }
    private Manifest scan(String id,Set<String> roots,Map<String,Boolean> original) throws IOException {
        LinkedHashMap<String,Boolean> presence=new LinkedHashMap<>();List<Entry> entries=new ArrayList<>();long[] total={0};
        for(String name:new TreeSet<>(roots)) {
            Path root=allowed.get(name);safe(root);boolean exists=Files.exists(root,LinkOption.NOFOLLOW_LINKS);
            if(exists&&!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))throw fail("A scoped root is not a directory.");
            int start=entries.size();if(exists)walk(name,root,root,entries,total,0);
            if(name.equals("no_backup")&&original!=null&&Boolean.FALSE.equals(original.get(name))&&entries.size()==start)exists=false;
            presence.put(name,exists);
        }
        return new Manifest(id,presence,entries);
    }
    private void walk(String rootName,Path root,Path parent,List<Entry> entries,long[] total,int depth) throws IOException {
        if(depth>32)throw fail("State exceeds the directory depth bound.");
        List<Path> children=new ArrayList<>();try(DirectoryStream<Path> stream=Files.newDirectoryStream(parent)){for(Path child:stream)children.add(child);}
        Collections.sort(children,new Comparator<Path>(){@Override public int compare(Path first,Path second){return first.getFileName().toString().compareTo(second.getFileName().toString());}});
        for(Path child:children) {
            if(child.equals(home))continue;safe(child);
            String relative=root.relativize(child).toString().replace(File.separatorChar,'/');validatePath(relative);
            if(entries.size()>=MAX_FILES)throw fail("State exceeds the file-count bound.");
            if(Files.isDirectory(child,LinkOption.NOFOLLOW_LINKS)){entries.add(new Entry(rootName,relative,true,0,""));walk(rootName,root,child,entries,total,depth+1);}
            else if(Files.isRegularFile(child,LinkOption.NOFOLLOW_LINKS)){long size=Files.size(child);if(size>MAX_FILE||(total[0]+=size)>MAX_TOTAL)throw fail("State exceeds the byte bound.");entries.add(new Entry(rootName,relative,false,size,digest(child)));}
            else throw fail("Special files and symlinks are not supported.");
        }
    }
    private Manifest read(String id,Set<String> roots,String expectedHash) throws IOException {
        validateId(id);validateRoots(roots);if(expectedHash==null||!expectedHash.matches("[a-f0-9]{64}"))throw fail("Explicit manifest hash required.");
        safe(active);Path path=active.resolve("manifest.bin");safe(path);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>MAX_MANIFEST)throw fail("Guard manifest unavailable.");
        byte[] bytes=Files.readAllBytes(path);if(!hex(bytes).equals(expectedHash))throw fail("Guard manifest hash differs.");
        DataInputStream input=new DataInputStream(new ByteArrayInputStream(bytes));
        if(input.readInt()!=MAGIC||input.readInt()!=1||!id.equals(input.readUTF()))throw fail("Guard identity differs.");
        int count=input.readInt();if(count<1||count>allowed.size())throw fail("Invalid guard roots.");
        LinkedHashMap<String,Boolean> savedRoots=new LinkedHashMap<>();
        for(int i=0;i<count;i++){String name=input.readUTF();int flag=input.readUnsignedByte();if(!allowed.containsKey(name)||savedRoots.containsKey(name)||flag>1)throw fail("Invalid guard roots.");savedRoots.put(name,flag==1);}
        if(!savedRoots.keySet().equals(roots))throw fail("Guard scope differs from explicit roots.");
        count=input.readInt();if(count<0||count>MAX_FILES)throw fail("Invalid guard entries.");
        List<Entry> entries=new ArrayList<>();Set<String> keys=new HashSet<>();long total=0;
        for(int i=0;i<count;i++){String name=input.readUTF(),relative=input.readUTF();int type=input.readUnsignedByte();long size=input.readLong();String hash=input.readUTF();validatePath(relative);
            if(!Boolean.TRUE.equals(savedRoots.get(name))||type>1||size<0||size>MAX_FILE||(total+=size)>MAX_TOTAL||type==1&&(size!=0||!hash.isEmpty())||type==0&&!hash.matches("[a-f0-9]{64}"))throw fail("Invalid guard entry.");
            Entry entry=new Entry(name,relative,type==1,size,hash);if(!keys.add(entry.key()))throw fail("Duplicate guard entry.");target(entry);entries.add(entry);
        }
        if(input.available()!=0)throw fail("Invalid trailing guard data.");
        return new Manifest(id,savedRoots,entries);
    }
    private void validateCopies(Manifest manifest) throws IOException {
        safe(active.resolve("blobs"));int index=0;Set<String> expected=new HashSet<>();
        for(Entry entry:manifest.entries)if(!entry.directory){Path path=blob(index++);safe(path);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)!=entry.size||!digest(path).equals(entry.hash))throw fail("Guard bytes could not be verified.");expected.add(path.getFileName().toString());}
        try(DirectoryStream<Path> stream=Files.newDirectoryStream(active.resolve("blobs"))){for(Path path:stream)if(!expected.remove(path.getFileName().toString()))throw fail("Unexpected guard blob.");}if(!expected.isEmpty())throw fail("Missing guard blob.");
        try(DirectoryStream<Path> stream=Files.newDirectoryStream(active)){for(Path path:stream)if(!path.getFileName().toString().equals("blobs")&&!path.getFileName().toString().equals("manifest.bin")&&!path.getFileName().toString().equals("restore.pending"))throw fail("Unexpected guard state.");}
        if(Files.exists(active.resolve("restore.pending"),LinkOption.NOFOLLOW_LINKS))validatePending(hex(encode(manifest)));
    }
    private void validatePending(String hash) throws IOException {
        Path pending=active.resolve("restore.pending");safe(pending);
        if(!Files.isRegularFile(pending,LinkOption.NOFOLLOW_LINKS)||Files.size(pending)!=64||!Arrays.equals(Files.readAllBytes(pending),hash.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))throw fail("Invalid restore journal.");
    }
    private Path target(Entry entry) throws IOException {Path root=allowed.get(entry.root);if(root==null)throw fail("Invalid guard root.");Path target=root.resolve(entry.path).normalize();if(!target.startsWith(root)||target.equals(root)||target.startsWith(home))throw fail("Guard path escaped its root.");safe(target);return target;}
    private void safe(Path path) throws IOException {Path cursor=path.toAbsolutePath().normalize();if(!cursor.startsWith(data))throw fail("Path escaped app data.");while(cursor!=null&&cursor.startsWith(data)){if(Files.isSymbolicLink(cursor))throw fail("Symlink state is not supported.");cursor=cursor.getParent();}}
    private void directory(Path path) throws IOException {safe(path);if(Files.exists(path,LinkOption.NOFOLLOW_LINKS)&&!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))throw fail("Directory conflicts with a file.");Files.createDirectories(path);}
    private Path blob(int index){return active.resolve("blobs").resolve(String.format(Locale.ROOT,"f%08d",index));}
    private static boolean same(Manifest first,Manifest second){return first.roots.equals(second.roots)&&first.entries.equals(second.entries);}
    private static byte[] encode(Manifest manifest) throws IOException {ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream output=new DataOutputStream(bytes);output.writeInt(MAGIC);output.writeInt(1);output.writeUTF(manifest.id);output.writeInt(manifest.roots.size());for(Map.Entry<String,Boolean> root:manifest.roots.entrySet()){output.writeUTF(root.getKey());output.writeByte(root.getValue()?1:0);}output.writeInt(manifest.entries.size());for(Entry entry:manifest.entries){output.writeUTF(entry.root);output.writeUTF(entry.path);output.writeByte(entry.directory?1:0);output.writeLong(entry.size);output.writeUTF(entry.hash);}output.flush();if(bytes.size()>MAX_MANIFEST)throw fail("Manifest exceeds bounds.");return bytes.toByteArray();}
    private static String digest(Path path) throws IOException {try(InputStream input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){MessageDigest hash=sha();byte[] buffer=new byte[8192];long bytes=0;int count;while((count=input.read(buffer))!=-1){if((bytes+=count)>MAX_FILE)throw fail("State file exceeds bounds.");hash.update(buffer,0,count);}return hexDigest(hash.digest());}}
    private static String hex(byte[] bytes){return hexDigest(sha().digest(bytes));}
    private static String hexDigest(byte[] bytes){StringBuilder result=new StringBuilder();for(byte value:bytes)result.append(String.format(Locale.ROOT,"%02x",value&255));return result.toString();}
    private static MessageDigest sha(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    private static void copy(Path source,Path destination) throws IOException {try(InputStream input=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS);FileOutputStream output=new FileOutputStream(destination.toFile())){byte[] buffer=new byte[8192];long bytes=0;int count;while((count=input.read(buffer))!=-1){if((bytes+=count)>MAX_FILE)throw fail("State file exceeds bounds.");output.write(buffer,0,count);}output.getFD().sync();}}
    private static void writeNew(Path path,byte[] bytes) throws IOException {if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw fail("Guard manifest already exists.");try(FileOutputStream output=new FileOutputStream(path.toFile())){output.write(bytes);output.getFD().sync();}}
    private static void sync(Path path) throws IOException {try(FileChannel channel=FileChannel.open(path,StandardOpenOption.READ)){channel.force(true);}}
    private void deleteGuard(Path path) throws IOException {safe(path);if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)){try(DirectoryStream<Path> children=Files.newDirectoryStream(path)){for(Path child:children)deleteGuard(child);}}Files.delete(path);}
    private void validateRoots(Set<String> roots) throws IOException {if(roots==null||roots.isEmpty()||!allowed.keySet().containsAll(roots))throw fail("Explicit reviewed roots required.");}
    private static void validateId(String id) throws IOException {if(id==null||!id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"))throw fail("Invalid case ID.");}
    private static void validatePath(String path) throws IOException {if(path.isEmpty()||path.length()>1024||path.startsWith("/")||path.contains("\\")||path.matches("(?s).*\\p{Cntrl}.*"))throw fail("Invalid relative state path.");for(String part:path.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))throw fail("Invalid relative state path.");}
    private static Result result(Manifest manifest,String hash,boolean matches){int files=0;long bytes=0;for(Entry entry:manifest.entries)if(!entry.directory){files++;bytes+=entry.size;}return new Result(manifest.id,hash,files,bytes,matches);}
    private Lock lock() throws IOException {safe(home);directory(home);try(DirectoryStream<Path> children=Files.newDirectoryStream(home)){for(Path child:children)if(!child.getFileName().toString().equals("lock")&&!child.getFileName().toString().equals("active"))throw fail("Unexpected guard directory contents.");}
        Path lock=home.resolve("lock");safe(lock);FileChannel channel=FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE);try{FileLock held=channel.tryLock();if(held==null)throw fail("Another guard helper is active.");return new Lock(channel,held);}catch(Exception failure){channel.close();if(failure instanceof IOException)throw(IOException)failure;throw fail("Another guard helper is active.");}}
    private static final class Lock implements AutoCloseable {final FileChannel channel;final FileLock lock;Lock(FileChannel channel,FileLock lock){this.channel=channel;this.lock=lock;}void requireHeld() throws IOException {if(!lock.isValid())throw fail("Guard lock was lost.");}@Override public void close() throws IOException {lock.release();channel.close();}}
    public static final class GuardFailure extends IOException {
        private static final long serialVersionUID=1L;
        GuardFailure(String message){super(message);}
    }
    private static IOException fail(String message){return new GuardFailure(message);}
}
