package com.jeremykenedy.firetv.ui.nativeapp.locks;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class LockState {
    static final int MAX_BYTES = 512 * 1024;
    private static final int ITERATIONS = 120000;
    private static final int MAGIC = 0x4c4f434b;
    final Set<String> targets = new LinkedHashSet<>();
    private byte[] salt = new byte[0];
    private byte[] verifier = new byte[0];
    private int failures;
    private long blockedUntil;

    boolean hasPin() { return verifier.length != 0; }

    static String key(String value) {
        if (value == null || value.length() > 512 || !(value.matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+")
                || value.matches("folder:[A-Za-z0-9_-]{1,128}"))) {
            throw new IllegalArgumentException("A stable app component or folder identity is required.");
        }
        int slash = value.indexOf('/');
        if (slash > 0 && value.charAt(slash + 1) == '.') {
            String normalized = value.substring(0, slash + 1) + value.substring(0, slash) + value.substring(slash + 1);
            if (normalized.length() > 512) throw new IllegalArgumentException("Component identity exceeds its limit.");
            return normalized;
        }
        return value;
    }

    void createPin(char[] pin) throws GeneralSecurityException {
        if (hasPin()) throw new IllegalStateException("A PIN already exists.");
        replacePin(pin);
    }

    void replacePin(char[] pin) throws GeneralSecurityException {
        validatePin(pin);
        byte[] nextSalt = new byte[16];
        new SecureRandom().nextBytes(nextSalt);
        byte[] next = derive(pin, nextSalt);
        Arrays.fill(verifier, (byte) 0);
        salt = nextSalt;
        verifier = next;
        failures = 0;
        blockedUntil = 0;
    }

    static void validatePin(char[] pin) {
        if (pin == null || pin.length < 4 || pin.length > 12) throw new IllegalArgumentException("Use 4 through 12 digits.");
        for (char digit : pin) if (digit < '0' || digit > '9') throw new IllegalArgumentException("Use 4 through 12 digits.");
    }

    long remaining(long now) {
        return Math.max(0, Math.min(300000L, blockedUntil - Math.max(0, now)));
    }

    LockResult verify(char[] pin, long now) throws GeneralSecurityException {
        if (!hasPin()) return LockResult.NO_PIN;
        if (remaining(now) > 0) {
            // A clock correction cannot turn a five-minute delay into an indefinite lockout.
            blockedUntil = Math.min(blockedUntil, Math.max(0, now) + 300000L);
            return LockResult.THROTTLED;
        }
        boolean matches = false;
        if (pin != null && pin.length >= 4 && pin.length <= 12) {
            byte[] candidate = derive(pin, salt);
            matches = MessageDigest.isEqual(verifier, candidate);
            Arrays.fill(candidate, (byte) 0);
        }
        if (matches) {
            failures = 0;
            blockedUntil = 0;
            return LockResult.GRANTED;
        }
        failures = Math.min(15, failures + 1);
        if (failures >= 5) blockedUntil = Math.max(0, now) + Math.min(300000L, 30000L << Math.min(4, failures - 5));
        return LockResult.WRONG_PIN;
    }

    void lock(String target, boolean locked) {
        target = key(target);
        if (locked && !hasPin()) throw new IllegalStateException("Create a PIN first.");
        if (locked && !targets.contains(target) && targets.size() >= 512) throw new IllegalArgumentException("Up to 512 app and folder locks are supported.");
        if (locked) targets.add(target); else targets.remove(target);
    }

    byte[] encode() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(MAGIC); out.writeInt(1);
        out.writeInt(salt.length); out.write(salt);
        out.writeInt(verifier.length); out.write(verifier);
        out.writeInt(failures); out.writeLong(blockedUntil);
        out.writeInt(targets.size());
        for (String target : targets) out.writeUTF(key(target));
        out.flush();
        return bytes.toByteArray();
    }

    static LockState decode(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("Lock storage exceeds its limit.");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readInt() != MAGIC || in.readInt() != 1) throw new IOException("Unsupported lock storage.");
        LockState state = new LockState();
        int saltLength = in.readInt();
        if (saltLength != 0 && saltLength != 16) throw new IOException("Invalid lock storage.");
        state.salt = new byte[saltLength]; in.readFully(state.salt);
        int hashLength = in.readInt();
        if (hashLength != (saltLength == 0 ? 0 : 32)) throw new IOException("Invalid lock storage.");
        state.verifier = new byte[hashLength]; in.readFully(state.verifier);
        state.failures = in.readInt(); state.blockedUntil = in.readLong();
        int size = in.readInt();
        if (state.failures < 0 || state.failures > 15 || state.blockedUntil < 0 || size < 0 || size > 512
                || (!state.hasPin() && (size != 0 || state.failures != 0 || state.blockedUntil != 0))) throw new IOException("Invalid lock storage.");
        try {
            for (int i = 0; i < size; i++) if (!state.targets.add(key(in.readUTF()))) throw new IOException("Duplicate lock identity.");
        } catch (IllegalArgumentException invalid) { throw new IOException("Invalid lock identity.", invalid); }
        if (in.read() != -1) throw new IOException("Unexpected lock storage data.");
        return state;
    }

    private static byte[] derive(char[] pin, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(pin, salt, ITERATIONS, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
}
