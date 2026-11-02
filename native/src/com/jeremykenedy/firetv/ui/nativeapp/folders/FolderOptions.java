package com.jeremykenedy.firetv.ui.nativeapp.folders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class FolderOptions {
    public static final int MAX_FOLDERS = 100;
    public static final int MAX_APPS = 500;

    public static final class Folder {
        public final String id;
        public final String name;
        public final List<String> components;

        public Folder(String id, String name, List<String> components) {
            if (id == null || id.trim().isEmpty() || id.length() > 160 || hasControl(id))
                throw new IllegalArgumentException("Invalid folder identity.");
            this.id = id;
            this.name = validName(name);
            if (components == null || components.size() > MAX_APPS)
                throw new IllegalArgumentException("Too many apps in this folder.");
            List<String> copy = new ArrayList<>();
            for (String component : components) {
                String key = componentKey(component);
                if (!copy.contains(key)) copy.add(key);
            }
            this.components = Collections.unmodifiableList(copy);
        }
    }

    public final List<Folder> folders;

    public FolderOptions(List<Folder> folders) {
        if (folders == null || folders.size() > MAX_FOLDERS)
            throw new IllegalArgumentException("Too many folders.");
        Set<String> ids = new HashSet<>();
        List<Folder> copy = new ArrayList<>();
        for (Folder folder : folders) {
            if (folder == null || !ids.add(folder.id)) throw new IllegalArgumentException("Duplicate folder identity.");
            copy.add(folder);
        }
        this.folders = Collections.unmodifiableList(copy);
    }

    public static FolderOptions empty() { return new FolderOptions(Collections.<Folder>emptyList()); }
    public Folder find(String id) {
        for (Folder folder : folders) if (folder.id.equals(id)) return folder;
        return null;
    }
    public FolderOptions create(String name) { return create(UUID.randomUUID().toString(), name); }
    public FolderOptions create(String id, String name) {
        List<Folder> next = new ArrayList<>(folders);
        next.add(new Folder(id, name, Collections.<String>emptyList()));
        return new FolderOptions(next);
    }
    public FolderOptions rename(String id, String name) {
        Folder old = require(id);
        return replace(new Folder(id, name, old.components));
    }
    public FolderOptions remove(String id) {
        require(id);
        List<Folder> next = new ArrayList<>(folders);
        next.remove(find(id));
        return new FolderOptions(next);
    }
    public FolderOptions move(String id, int direction) {
        requireDirection(direction);
        List<Folder> next = new ArrayList<>(folders);
        int position = next.indexOf(require(id));
        int target = position + direction;
        if (target >= 0 && target < next.size()) Collections.swap(next, position, target);
        return new FolderOptions(next);
    }
    public FolderOptions addApp(String id, String component) {
        Folder old = require(id);
        String key = componentKey(component);
        if (old.components.contains(key)) return this;
        List<String> next = new ArrayList<>(old.components); next.add(key);
        return replace(new Folder(id, old.name, next));
    }
    public FolderOptions removeApp(String id, String component) {
        Folder old = require(id);
        List<String> next = new ArrayList<>(old.components); next.remove(componentKey(component));
        return replace(new Folder(id, old.name, next));
    }
    public FolderOptions moveApp(String id, String component, int direction) {
        requireDirection(direction);
        Folder old = require(id);
        List<String> next = new ArrayList<>(old.components);
        int position = next.indexOf(componentKey(component)), target = position + direction;
        if (position >= 0 && target >= 0 && target < next.size()) Collections.swap(next, position, target);
        return replace(new Folder(id, old.name, next));
    }
    private Folder require(String id) {
        Folder folder = find(id);
        if (folder == null) throw new IllegalArgumentException("Folder no longer exists.");
        return folder;
    }
    private FolderOptions replace(Folder value) {
        List<Folder> next = new ArrayList<>(folders);
        next.set(next.indexOf(require(value.id)), value);
        return new FolderOptions(next);
    }
    private static void requireDirection(int value) {
        if (value != -1 && value != 1) throw new IllegalArgumentException("Move one position at a time.");
    }
    private static String validName(String name) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 80 || hasControl(name))
            throw new IllegalArgumentException("Enter a name with 1 through 80 characters.");
        return name.trim();
    }
    private static boolean hasControl(String value) {
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) return true;
        return false;
    }
    private static String componentKey(String value) {
        if (!validComponent(value)) throw new IllegalArgumentException("Invalid app identity.");
        int slash = value.indexOf('/');
        String name = value.substring(slash + 1);
        String canonical = name.startsWith(".")
                ? value.substring(0, slash + 1) + value.substring(0, slash) + name : value;
        if (canonical.length() > 512) throw new IllegalArgumentException("Invalid app identity.");
        return canonical;
    }
    public static boolean validComponent(String value) {
        return value != null && value.length() <= 512
                && value.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+")
                && !value.endsWith("/.");
    }
}
