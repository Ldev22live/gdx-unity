package io.github.ldev22live.gdxunity.editor.core.asset.library;

import io.github.ldev22live.gdxunity.engine.core.asset.ThirdPartyManifest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Imports an asset pack a person downloaded (UI M4, TD-094 and TD-095): a zip archive is checked whole before anything is written, the
 * files ForgeRoom can read are copied into the project under {@link CuratedPack#folder()} keeping the archive's layout (so a glTF
 * still finds its buffers and textures), and the pack is recorded in {@code licenses/manifest.json} with every file's SHA-256.
 *
 * <p>An archive is refused, and nothing is written, when an entry's path is absolute or leaves the folder, when it holds an
 * executable or a script, when it has no licence file or its licence file does not say CC0, or when it is larger than
 * {@link #MAX_TOTAL_BYTES}. Files of a format ForgeRoom cannot read are left out and listed, never imported silently. Importing a
 * pack again replaces the files its earlier import recorded, and only those.
 */
public final class PackImporter {
    /** The largest pack, uncompressed (TD-095: high-definition assets, up to 4 GB a download). */
    public static final long MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024;
    /** The most entries an archive may have; more is refused rather than walked. */
    public static final int MAX_ENTRIES = 50_000;
    /** The formats ForgeRoom reads and imports (AssetType), plus licence and readme text. */
    public static final Set<String> SUPPORTED = Set.of("gltf", "glb", "bin", "png", "jpg", "jpeg", "wav", "ogg", "txt", "md");
    /** Executables and scripts: an archive holding one is refused whole. */
    public static final Set<String> REFUSED = Set.of("exe", "dll", "so", "dylib", "bat", "cmd", "ps1", "sh", "com", "msi", "jar", "js",
        "vbs", "py", "scr", "app", "apk", "lnk");
    /** What a licence file must say for the pack to be CC0 (TD-094 accepts CC0 only). */
    private static final List<String> CC0_MARKERS = List.of("cc0", "creative commons zero", "publicdomain/zero");
    /** A licence file larger than this is not read as one. */
    private static final int MAX_LICENCE_BYTES = 64 * 1024;
    private static final int COPY_BUFFER = 64 * 1024;

    /**
     * What an import did.
     *
     * @param entry    the pack's manifest entry, as written
     * @param imported the files copied, relative to the asset root
     * @param leftOut  the archive's files of formats ForgeRoom cannot read, by their path in the archive
     */
    public record Result(ThirdPartyManifest.Entry entry, List<String> imported, List<String> leftOut) {
        public Result {
            imported = List.copyOf(imported);
            leftOut = List.copyOf(leftOut);
        }
    }

    /** An archive that cannot be imported; {@link #getProblems()} lists every reason, and nothing was written. */
    public static final class RefusedException extends IOException {
        private final List<String> problems;

        RefusedException(List<String> problems) {
            super("The pack cannot be imported: " + String.join("; ", problems));
            this.problems = List.copyOf(problems);
        }

        public List<String> getProblems() {
            return problems;
        }
    }

    private PackImporter() {
    }

    /**
     * Imports {@code archive} as {@code pack} into the project whose asset root is {@code assetsRoot}.
     *
     * @param today the retrieval date recorded in the manifest
     * @throws RefusedException when the archive fails a check; nothing is written
     * @throws IOException      when it cannot be read or the project cannot be written
     */
    public static Result importPack(File archive, CuratedPack pack, File assetsRoot, LocalDate today) throws IOException {
        Objects.requireNonNull(archive, "archive cannot be null");
        Objects.requireNonNull(pack, "pack cannot be null");
        Objects.requireNonNull(assetsRoot, "assetsRoot cannot be null");
        Objects.requireNonNull(today, "today cannot be null");
        ThirdPartyManifest manifest = ThirdPartyManifest.read(assetsRoot);
        try (ZipFile zip = new ZipFile(archive, StandardCharsets.UTF_8)) {
            List<ZipEntry> files = new ArrayList<>();
            List<String> leftOut = new ArrayList<>();
            List<String> problems = new ArrayList<>();
            boolean cc0Licence = false;
            boolean anyLicence = false;
            long total = 0L;
            int count = 0;
            for (Enumeration<? extends ZipEntry> all = zip.entries(); all.hasMoreElements(); ) {
                ZipEntry entry = all.nextElement();
                if (++count > MAX_ENTRIES) {
                    problems.add("more than " + MAX_ENTRIES + " entries");
                    break;
                }
                String name = entry.getName();
                if (entry.isDirectory()) {
                    if (!isSafe(name.endsWith("/") ? name.substring(0, name.length() - 1) : name)) {
                        problems.add("unsafe folder path: " + name);
                    }
                    continue;
                }
                if (!isSafe(name)) {
                    problems.add("unsafe path: " + name);
                    continue;
                }
                String extension = extension(name);
                if (REFUSED.contains(extension)) {
                    problems.add("executable or script: " + name);
                    continue;
                }
                if (isLicenceFile(name)) {
                    anyLicence = true;
                    cc0Licence |= saysCc0(zip, entry);
                }
                if (!SUPPORTED.contains(extension)) {
                    leftOut.add(name);
                    continue;
                }
                long size = entry.getSize();
                total += Math.max(0L, size);
                files.add(entry);
            }
            if (!anyLicence) {
                problems.add("no licence file (License.txt or similar): the pack's licence cannot be checked");
            } else if (!cc0Licence) {
                problems.add("its licence file does not say CC0; only CC0 packs are imported (TD-094)");
            }
            if (total > MAX_TOTAL_BYTES) {
                problems.add("larger than " + (MAX_TOTAL_BYTES >> 30) + " GB uncompressed");
            }
            if (files.isEmpty() && problems.isEmpty()) {
                problems.add("no file of a format ForgeRoom reads (" + String.join(", ", new TreeSet<>(SUPPORTED)) + ")");
            }
            if (!problems.isEmpty()) {
                throw new RefusedException(problems);
            }

            // Checked whole: now replace what an earlier import of this pack wrote, and copy
            for (String old : manifest.entry(pack.manifestId()).map(e -> e.files().stream().map(ThirdPartyManifest.FileRecord::path).toList())
                .orElse(List.of())) {
                Files.deleteIfExists(assetsRoot.toPath().resolve(old));
            }
            List<ThirdPartyManifest.FileRecord> records = new ArrayList<>();
            List<String> imported = new ArrayList<>();
            Set<String> formats = new TreeSet<>();
            long written = 0L;
            List<Path> created = new ArrayList<>();
            try {
                for (ZipEntry entry : files) {
                    String relative = pack.folder() + "/" + entry.getName();
                    Path target = assetsRoot.toPath().resolve(relative).normalize();
                    if (!target.startsWith(assetsRoot.toPath().resolve(pack.folder()).normalize())) {
                        throw new RefusedException(List.of("unsafe path: " + entry.getName()));
                    }
                    Files.createDirectories(target.getParent());
                    created.add(target);
                    MessageDigest sha256 = sha256();
                    long bytes = 0L;
                    try (InputStream in = zip.getInputStream(entry); OutputStream out = Files.newOutputStream(target)) {
                        byte[] buffer = new byte[COPY_BUFFER];
                        for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                            bytes += read;
                            written += read;
                            if (written > MAX_TOTAL_BYTES) {
                                throw new RefusedException(List.of("larger than " + (MAX_TOTAL_BYTES >> 30) + " GB uncompressed while reading"));
                            }
                            sha256.update(buffer, 0, read);
                            out.write(buffer, 0, read);
                        }
                    }
                    records.add(new ThirdPartyManifest.FileRecord(relative, fileName(entry.getName()), bytes,
                        HexFormat.of().formatHex(sha256.digest())));
                    imported.add(relative);
                    formats.add(extension(entry.getName()));
                }
            } catch (IOException | RuntimeException e) {
                // A copy that fails part way leaves nothing behind: the files it wrote are removed, the manifest is not touched
                for (Path path : created) {
                    Files.deleteIfExists(path);
                }
                throw e;
            }
            formats.removeAll(Set.of("txt", "md"));
            String status = "imported" + (leftOut.isEmpty() ? "" : "; left out " + leftOut.size() + " file(s) ForgeRoom cannot read ("
                + String.join(", ", leftOutFormats(leftOut)) + ")");
            ThirdPartyManifest.Entry entry = new ThirdPartyManifest.Entry(pack.manifestId(), pack.name(), pack.category(), pack.page(),
                "CC0-1.0", pack.licenceEvidence(), List.of(pack.author()), today.toString(),
                "ForgeRoom Asset Library: pack import of " + archive.getName() + " (downloaded by a person)",
                formats.isEmpty() ? "txt" : String.join(", ", formats), "none", status, records);
            manifest.with(entry).write(assetsRoot);
            return new Result(entry, imported, leftOut);
        }
    }

    /** True for a forward-slash path relative to the folder, with no '..', '.', empty part, drive or backslash. */
    static boolean isSafe(String path) {
        if (path.isEmpty() || path.startsWith("/") || path.contains("\\") || path.contains(":")) {
            return false;
        }
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLicenceFile(String path) {
        String name = fileName(path).toLowerCase(Locale.ROOT);
        return name.startsWith("license") || name.startsWith("licence");
    }

    private static boolean saysCc0(ZipFile zip, ZipEntry entry) throws IOException {
        if (entry.getSize() > MAX_LICENCE_BYTES) {
            return false;
        }
        try (InputStream in = zip.getInputStream(entry)) {
            String text = new String(in.readNBytes(MAX_LICENCE_BYTES), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            return CC0_MARKERS.stream().anyMatch(text::contains);
        }
    }

    private static List<String> leftOutFormats(List<String> leftOut) {
        Set<String> formats = new TreeSet<>();
        for (String path : leftOut) {
            String extension = extension(path);
            formats.add(extension.isEmpty() ? "no extension" : "." + extension);
        }
        return Collections.unmodifiableList(new ArrayList<>(formats));
    }

    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String extension(String path) {
        String name = fileName(path);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("The JDK has no SHA-256", e);
        }
    }
}
