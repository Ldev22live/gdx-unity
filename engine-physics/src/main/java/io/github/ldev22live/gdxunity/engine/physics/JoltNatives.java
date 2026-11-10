package io.github.ldev22live.gdxunity.engine.physics;

import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.JoltPhysicsObject;
import com.github.stephengold.joltjni.NativeLibraryLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Loads the Jolt Physics native library once per process (TD-028). jolt-jni ships it inside a jar (the {@code ReleaseSp}
 * artifact of the host platform), so it is copied to a temporary folder named after the file's hash and loaded from there:
 * a changed library never meets a stale copy, and two game processes share one copy instead of fighting over a file lock.
 * <p>
 * Supported: Windows x64, Linux x64 and macOS arm64 (the artifacts the build bundles). Anything else fails with a message
 * naming the platform.
 */
public final class JoltNatives {
    /** Characters of the library's SHA-256 used in the folder name: enough to tell two builds apart. */
    private static final int HASH_CHARS = 16;
    /** Prefix of the temporary folder that holds the extracted library. */
    private static final String FOLDER_PREFIX = "forgeroom-jolt-";

    private static boolean loaded;

    private JoltNatives() {}

    /**
     * Extracts and loads the native library and initialises Jolt's global state (allocator, type registry). Safe to call
     * repeatedly; later calls do nothing. Jolt's global state lives until the process ends.
     *
     * @throws IllegalStateException if this platform has no bundled library or loading fails; the message says why
     */
    public static synchronized void load() {
        if (loaded) {
            return;
        }
        String resource = resourceFor(System.getProperty("os.name"), System.getProperty("os.arch"));
        try {
            Path library = extract(resource);
            NativeLibraryLoader.loadLibrary(library.toAbsolutePath().toString());
        } catch (IOException | UnsatisfiedLinkError e) {
            throw new IllegalStateException("Cannot load the Jolt Physics library (" + resource + "): " + e.getMessage(), e);
        }
        JoltPhysicsObject.startCleaner();
        Jolt.registerDefaultAllocator();
        Jolt.installDefaultAssertCallback();
        Jolt.installDefaultTraceCallback();
        if (!Jolt.newFactory()) {
            throw new IllegalStateException("Jolt Physics could not create its factory");
        }
        Jolt.registerTypes();
        loaded = true;
    }

    /** Path of the native library inside the jolt-jni artifacts, for an operating system and CPU architecture. */
    static String resourceFor(String osName, String arch) {
        String os = osName.toLowerCase(Locale.ROOT);
        String cpu = arch.toLowerCase(Locale.ROOT);
        boolean x64 = cpu.equals("amd64") || cpu.equals("x86_64");
        boolean arm64 = cpu.equals("aarch64") || cpu.equals("arm64");
        if (os.contains("win") && x64) {
            return "windows/x86-64/com/github/stephengold/joltjni.dll";
        }
        if (os.contains("linux") && x64) {
            return "linux/x86-64/com/github/stephengold/libjoltjni.so";
        }
        if (os.contains("mac") && arm64) {
            return "osx/aarch64/com/github/stephengold/libjoltjni.dylib";
        }
        throw new IllegalStateException("3D physics has no native library for " + osName + " on " + arch
            + " (supported: Windows x64, Linux x64, macOS arm64)");
    }

    private static Path extract(String resource) throws IOException {
        byte[] bytes;
        try (InputStream in = JoltNatives.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("the jolt-jni native jar for this platform is not on the class path");
            }
            bytes = in.readAllBytes();
        }
        Path folder = Path.of(System.getProperty("java.io.tmpdir"), FOLDER_PREFIX + hash(bytes));
        Path target = folder.resolve(Path.of(resource).getFileName().toString());
        if (Files.isRegularFile(target) && Files.size(target) == bytes.length) {
            return target;
        }
        Files.createDirectories(folder);
        // Write beside the target and move, so a half-written file is never loaded by a process that starts meanwhile
        Path partial = Files.createTempFile(folder, "joltjni", ".part");
        Files.write(partial, bytes);
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).substring(0, HASH_CHARS);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }
}
