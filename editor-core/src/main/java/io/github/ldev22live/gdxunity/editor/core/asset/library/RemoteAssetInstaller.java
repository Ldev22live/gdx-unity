package io.github.ldev22live.gdxunity.editor.core.asset.library;

import io.github.ldev22live.gdxunity.editor.core.material.MaterialAssets;
import io.github.ldev22live.gdxunity.engine.core.asset.ThirdPartyManifest;
import io.github.ldev22live.gdxunity.engine.material.Material;
import io.github.ldev22live.gdxunity.engine.material.MaterialFormatException;
import io.github.ldev22live.gdxunity.engine.material.MaterialIO;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Downloads a provider's asset into the open project (TD-094, TD-095). Everything is checked before the project is touched: the
 * stated size (at most {@link #MAX_BYTES}), free disk space, safe paths, formats ForgeRoom reads, the provider's MD5, and the size
 * again while reading. Files are downloaded to a staging folder first, so a failed or cancelled download leaves the project as it was.
 * The files go to {@link RemoteAsset#folder()}, are recorded with their SHA-256 in {@code licenses/manifest.json}, and replace an
 * earlier download of the same asset (only the files that download recorded). A texture set also gets a {@code .material} that uses
 * it, named after the asset; an existing one keeps the person's settings and only has its texture paths moved to the new files.
 * A provider that packs an asset in a zip (ambientCG, UI M6) is unpacked with the pack importer's rules: safe paths only, an executable
 * or script refuses the whole download, and only formats ForgeRoom reads are kept; the rest are listed, never imported silently.
 */
public final class RemoteAssetInstaller {
    /** The largest download (TD-095): 4 GB, the same as a pack import. */
    public static final long MAX_BYTES = PackImporter.MAX_TOTAL_BYTES;
    /** Downloads larger than this ask the person first, showing the size (TD-095). */
    public static final long CONFIRM_BYTES = 500L * 1024 * 1024;
    /** Free space kept beyond the download itself, so a download never fills a disk. */
    static final long DISK_MARGIN_BYTES = 64L * 1024 * 1024;
    /** Read buffer size. */
    private static final int BUFFER_BYTES = 64 * 1024;
    /** The staging folder's name prefix in the system's temporary folder. */
    private static final String STAGING_PREFIX = "forgeroom-download-";
    /** The folder inside staging that keeps an earlier download's files until the new ones are in place. */
    private static final String PREVIOUS_FOLDER = ".previous";
    /** The folder inside staging the asset's files are gathered in, laid out as in the project's asset folder. */
    private static final String FILES_FOLDER = "files";
    /** The folder inside staging downloaded archives wait in until they are unpacked. */
    private static final String ARCHIVES_FOLDER = "archives";
    /** The only archive format a provider's download may be. */
    private static final String ARCHIVE_EXTENSION = "zip";

    /** One file gathered in staging: where it goes inside the asset's folder, its name at the source, size, SHA-256 and use. */
    private record Staged(String path, String original, long bytes, String sha256, DownloadOption.Role role) {
    }

    /** Reports progress, from the download's thread. */
    @FunctionalInterface
    public interface Progress {
        /** {@code done} of {@code total} bytes, while {@code file} downloads. */
        void update(long done, long total, String file);
    }

    /** The download was refused before or while downloading; nothing in the project changed. */
    public static final class RefusedException extends IOException {
        private final List<String> problems;

        RefusedException(List<String> problems) {
            super(String.join("; ", problems));
            this.problems = List.copyOf(problems);
        }

        /** Every reason. */
        public List<String> getProblems() {
            return problems;
        }
    }

    /** The person cancelled; nothing in the project changed. */
    public static final class CancelledException extends IOException {
        CancelledException() {
            super("Download cancelled; nothing was written");
        }
    }

    /**
     * What was installed.
     *
     * @param entry        the manifest entry written
     * @param files        the project-relative paths written
     * @param materialPath the {@code .material} made or updated for a texture set, or an empty string
     * @param leftOut      files of an archive ForgeRoom does not read, left out (paths inside the archive)
     */
    public record Result(ThirdPartyManifest.Entry entry, List<String> files, String materialPath, List<String> leftOut) {
        public Result {
            Objects.requireNonNull(entry, "entry cannot be null");
            files = List.copyOf(files);
            Objects.requireNonNull(materialPath, "materialPath cannot be null");
            leftOut = List.copyOf(leftOut);
        }

        /** The glTF or GLB a model download brought, to place in the world (UI M7); empty for a texture set. */
        public Optional<String> mainModel() {
            return files.stream().filter(path -> {
                String lower = path.toLowerCase(Locale.ROOT);
                return lower.endsWith(".gltf") || lower.endsWith(".glb");
            }).findFirst();
        }
    }

    private RemoteAssetInstaller() { }

    /** Whether {@code option} is large enough that the person is asked first (TD-095). */
    public static boolean needsConfirmation(DownloadOption option) {
        return option.totalBytes() > CONFIRM_BYTES;
    }

    /**
     * Downloads {@code option} of {@code asset} into the project.
     *
     * @param assetsRoot the open project's asset folder
     * @param today      the retrieval date written to the manifest
     * @param cancelled  checked while downloading; true stops the download and leaves the project unchanged
     * @throws RefusedException   when a check fails
     * @throws CancelledException when cancelled
     * @throws IOException        when the provider cannot be reached or a file cannot be written
     */
    public static Result install(AssetProvider provider, RemoteAsset asset, DownloadOption option, File assetsRoot, LocalDate today,
                                 HttpSource http, Progress progress, BooleanSupplier cancelled) throws IOException {
        return install(provider, asset, option, assetsRoot, today, http, progress, cancelled,
            new File(System.getProperty("java.io.tmpdir")), File::getUsableSpace);
    }

    static Result install(AssetProvider provider, RemoteAsset asset, DownloadOption option, File assetsRoot, LocalDate today,
                          HttpSource http, Progress progress, BooleanSupplier cancelled, File stagingParent,
                          ToLongFunction<File> usableSpace) throws IOException {
        Objects.requireNonNull(provider, "provider cannot be null");
        Objects.requireNonNull(asset, "asset cannot be null");
        Objects.requireNonNull(option, "option cannot be null");
        Objects.requireNonNull(assetsRoot, "assetsRoot cannot be null");
        Objects.requireNonNull(today, "today cannot be null");
        Objects.requireNonNull(http, "http cannot be null");
        Objects.requireNonNull(progress, "progress cannot be null");
        Objects.requireNonNull(cancelled, "cancelled cannot be null");
        if (!provider.id().equals(asset.provider())) {
            throw new IllegalArgumentException("Asset " + asset.id() + " is from " + asset.provider() + ", not " + provider.id());
        }
        ThirdPartyManifest manifest = ThirdPartyManifest.read(assetsRoot);
        check(asset, option, assetsRoot, stagingParent, usableSpace);

        Path staging = Files.createTempDirectory(stagingParent.toPath(), STAGING_PREFIX);
        try {
            List<String> leftOut = new ArrayList<>();
            List<Staged> staged = download(provider, option, http, progress, cancelled, staging, leftOut);
            if (staged.isEmpty()) {
                throw new RefusedException(List.of("no file of a format ForgeRoom reads (" + String.join(", ", new TreeSet<>(PackImporter.SUPPORTED))
                    + ")"));
            }
            List<ThirdPartyManifest.FileRecord> records = new ArrayList<>();
            Set<String> formats = new TreeSet<>();
            for (Staged file : staged) {
                records.add(new ThirdPartyManifest.FileRecord(asset.folder() + "/" + file.path(), file.original(), file.bytes(), file.sha256()));
                formats.add(extension(file.path()));
            }
            formats.removeAll(Set.of("bin", "txt", "md"));
            if (asset.type() == AssetType.MODEL && formats.contains("gltf")) {
                formats = Set.of("gltf");
            }
            String status = "imported" + (leftOut.isEmpty() ? "" : "; left out " + leftOut.size() + " file(s) ForgeRoom cannot read ("
                + String.join(", ", leftOutFormats(leftOut)) + ")");
            ThirdPartyManifest.Entry entry = new ThirdPartyManifest.Entry(asset.manifestId(), asset.name(), asset.type().category(),
                asset.page(), "CC0-1.0", provider.licenceEvidence(), asset.authors(), today.toString(), provider.retrievedWith(option),
                String.join(", ", formats), "none", status, records);
            // Built before anything moves, so a file another entry already lists is refused with the project untouched
            ThirdPartyManifest updated;
            try {
                updated = manifest.with(entry);
            } catch (IllegalArgumentException e) {
                throw new RefusedException(List.of(e.getMessage()));
            }
            List<String> previous = manifest.entry(asset.manifestId())
                .map(old -> old.files().stream().map(ThirdPartyManifest.FileRecord::path).toList()).orElse(List.of());
            List<String> written = moveIntoProject(asset, records, previous, assetsRoot, staging);
            String materialPath = asset.type() == AssetType.TEXTURE ? writeMaterial(asset, staged, assetsRoot) : "";
            updated.write(assetsRoot);
            deleteTree(staging);
            return new Result(entry, written, materialPath, leftOut);
        } catch (IOException | RuntimeException e) {
            try {
                deleteTree(staging);
            } catch (IOException notDeleted) {
                e.addSuppressed(notDeleted);
            }
            throw e;
        }
    }

    private static void check(RemoteAsset asset, DownloadOption option, File assetsRoot, File stagingParent,
                              ToLongFunction<File> usableSpace) throws RefusedException {
        List<String> problems = new ArrayList<>();
        long total = option.totalBytes();
        if (total > MAX_BYTES) {
            problems.add(DownloadOption.formatBytes(total) + " is larger than the " + (MAX_BYTES >> 30) + " GB limit");
        }
        Set<String> seen = new HashSet<>();
        for (DownloadOption.RemoteFile file : option.files()) {
            if (!PackImporter.isSafe(file.path())) {
                problems.add("unsafe path: " + file.path());
            } else if (file.role() == DownloadOption.Role.ARCHIVE ? !ARCHIVE_EXTENSION.equals(extension(file.path()))
                : !PackImporter.SUPPORTED.contains(extension(file.path()))) {
                problems.add("a format ForgeRoom does not read: " + file.path());
            } else if (!seen.add(file.path().toLowerCase(Locale.ROOT))) {
                problems.add("listed twice: " + file.path());
            }
        }
        long needed = total + DISK_MARGIN_BYTES;
        for (File folder : List.of(assetsRoot, stagingParent)) {
            long free = usableSpace.applyAsLong(folder);
            if (free < needed) {
                problems.add("not enough free disk space on " + folder + ": " + DownloadOption.formatBytes(needed) + " needed, "
                    + DownloadOption.formatBytes(Math.max(0L, free)) + " free");
            }
        }
        if (!problems.isEmpty()) {
            throw new RefusedException(problems);
        }
    }

    private static List<Staged> download(AssetProvider provider, DownloadOption option, HttpSource http, Progress progress,
                                         BooleanSupplier cancelled, Path staging, List<String> leftOut) throws IOException {
        long total = option.totalBytes();
        long done = 0L;
        List<Staged> staged = new ArrayList<>();
        byte[] buffer = new byte[BUFFER_BYTES];
        Path filesRoot = staging.resolve(FILES_FOLDER);
        Path archivesRoot = staging.resolve(ARCHIVES_FOLDER);
        for (DownloadOption.RemoteFile file : option.files()) {
            if (cancelled.getAsBoolean()) {
                throw new CancelledException();
            }
            boolean archive = file.role() == DownloadOption.Role.ARCHIVE;
            Path base = archive ? archivesRoot : filesRoot;
            Path target = base.resolve(file.path()).normalize();
            if (!target.startsWith(base)) {
                throw new RefusedException(List.of("unsafe path: " + file.path()));
            }
            Files.createDirectories(target.getParent());
            MessageDigest md5 = digest("MD5");
            MessageDigest sha256 = digest("SHA-256");
            long bytes = 0L;
            progress.update(done, total, file.path());
            try (InputStream in = http.open(file.url()); OutputStream out = Files.newOutputStream(target)) {
                for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                    if (cancelled.getAsBoolean()) {
                        throw new CancelledException();
                    }
                    bytes += read;
                    done += read;
                    if (file.bytes() > 0 && bytes > file.bytes()) {
                        throw new RefusedException(List.of(file.path() + " is larger than the " + DownloadOption.formatBytes(file.bytes())
                            + " the provider stated"));
                    }
                    if (done > MAX_BYTES) {
                        throw new RefusedException(List.of("larger than the " + (MAX_BYTES >> 30) + " GB limit while downloading"));
                    }
                    md5.update(buffer, 0, read);
                    sha256.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                    progress.update(done, total, file.path());
                }
            }
            String actualMd5 = HexFormat.of().formatHex(md5.digest());
            if (!file.md5().isEmpty() && !file.md5().equalsIgnoreCase(actualMd5)) {
                throw new RefusedException(List.of(file.path() + " arrived damaged (MD5 " + actualMd5 + ", the provider says "
                    + file.md5() + ")"));
            }
            if (archive) {
                staged.addAll(unpack(provider, target, filesRoot, cancelled, leftOut));
                Files.delete(target);
            } else {
                staged.add(new Staged(file.path(), fileName(file.path()), bytes, HexFormat.of().formatHex(sha256.digest()), file.role()));
            }
        }
        List<String> seen = new ArrayList<>();
        for (Staged file : staged) {
            if (seen.contains(file.path().toLowerCase(Locale.ROOT))) {
                throw new RefusedException(List.of("listed twice: " + file.path()));
            }
            seen.add(file.path().toLowerCase(Locale.ROOT));
        }
        return staged;
    }

    /**
     * Unpacks a provider's zip into {@code filesRoot} with the pack importer's rules: the whole archive is checked first (safe paths, no
     * executable or script, at most {@link PackImporter#MAX_ENTRIES} entries and {@link #MAX_BYTES}), then only formats ForgeRoom reads
     * are written; the others are added to {@code leftOut}.
     */
    private static List<Staged> unpack(AssetProvider provider, Path archive, Path filesRoot, BooleanSupplier cancelled,
                                       List<String> leftOut) throws IOException {
        List<String> problems = new ArrayList<>();
        List<ZipEntry> wanted = new ArrayList<>();
        List<Staged> staged = new ArrayList<>();
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            long declared = 0L;
            int count = 0;
            for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                if (++count > PackImporter.MAX_ENTRIES) {
                    problems.add("more than " + PackImporter.MAX_ENTRIES + " entries");
                    break;
                }
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                String extension = extension(name);
                if (!PackImporter.isSafe(name)) {
                    problems.add("unsafe path in the archive: " + name);
                } else if (PackImporter.REFUSED.contains(extension)) {
                    problems.add("an executable or script in the archive: " + name);
                } else if (PackImporter.SUPPORTED.contains(extension)) {
                    wanted.add(entry);
                    declared += Math.max(0L, entry.getSize());
                } else {
                    leftOut.add(name);
                }
            }
            if (declared > MAX_BYTES) {
                problems.add("larger than the " + (MAX_BYTES >> 30) + " GB limit unpacked");
            }
            if (!problems.isEmpty()) {
                throw new RefusedException(problems);
            }
            byte[] buffer = new byte[BUFFER_BYTES];
            long written = 0L;
            for (ZipEntry entry : wanted) {
                Path target = filesRoot.resolve(entry.getName()).normalize();
                if (!target.startsWith(filesRoot)) {
                    throw new RefusedException(List.of("unsafe path in the archive: " + entry.getName()));
                }
                Files.createDirectories(target.getParent());
                MessageDigest sha256 = digest("SHA-256");
                long bytes = 0L;
                try (InputStream in = zip.getInputStream(entry); OutputStream out = Files.newOutputStream(target)) {
                    for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                        if (cancelled.getAsBoolean()) {
                            throw new CancelledException();
                        }
                        bytes += read;
                        written += read;
                        if (written > MAX_BYTES) {
                            throw new RefusedException(List.of("larger than the " + (MAX_BYTES >> 30) + " GB limit while unpacking"));
                        }
                        sha256.update(buffer, 0, read);
                        out.write(buffer, 0, read);
                    }
                }
                staged.add(new Staged(entry.getName(), fileName(entry.getName()), bytes, HexFormat.of().formatHex(sha256.digest()),
                    provider.roleOf(entry.getName())));
            }
        }
        return staged;
    }

    private static List<String> leftOutFormats(List<String> leftOut) {
        Set<String> formats = new TreeSet<>();
        for (String path : leftOut) {
            String extension = extension(path);
            formats.add(extension.isEmpty() ? "no extension" : "." + extension);
        }
        return new ArrayList<>(formats);
    }

    /**
     * Moves the staged files into the project. An earlier download's files are moved aside first and put back if anything fails,
     * so the project ends either with the new files or exactly as it was.
     */
    private static List<String> moveIntoProject(RemoteAsset asset, List<ThirdPartyManifest.FileRecord> records, List<String> previous,
                                                File assetsRoot, Path staging) throws IOException {
        Path root = assetsRoot.toPath();
        Path folder = root.resolve(asset.folder()).normalize();
        Path aside = staging.resolve(PREVIOUS_FOLDER);
        List<String> movedAside = new ArrayList<>();
        List<Path> moved = new ArrayList<>();
        try {
            for (String old : previous) {
                Path source = root.resolve(old).normalize();
                if (source.startsWith(root) && Files.isRegularFile(source)) {
                    Path keep = aside.resolve(old);
                    Files.createDirectories(keep.getParent());
                    Files.move(source, keep, StandardCopyOption.REPLACE_EXISTING);
                    movedAside.add(old);
                }
            }
            List<String> written = new ArrayList<>();
            for (ThirdPartyManifest.FileRecord record : records) {
                Path target = root.resolve(record.path()).normalize();
                if (!target.startsWith(folder)) {
                    throw new RefusedException(List.of("unsafe path: " + record.path()));
                }
                Files.createDirectories(target.getParent());
                Files.move(staging.resolve(FILES_FOLDER).resolve(record.path().substring(asset.folder().length() + 1)), target,
                    StandardCopyOption.REPLACE_EXISTING);
                moved.add(target);
                written.add(record.path());
            }
            return written;
        } catch (IOException | RuntimeException e) {
            for (Path path : moved) {
                Files.deleteIfExists(path);
            }
            for (String old : movedAside) {
                Path target = root.resolve(old);
                Files.createDirectories(target.getParent());
                Files.move(aside.resolve(old), target, StandardCopyOption.REPLACE_EXISTING);
            }
            throw e;
        }
    }

    /**
     * Makes {@code materials/<Name>.material} for a texture set, or moves an existing one's texture paths that point into this
     * asset's folder to the new files (the person's tiling, colours and other settings stay).
     */
    private static String writeMaterial(RemoteAsset asset, List<Staged> staged, File assetsRoot) throws IOException {
        String baseColor = null;
        String normal = null;
        String arm = null;
        String roughness = null;
        String occlusion = null;
        for (Staged file : staged) {
            String path = asset.folder() + "/" + file.path();
            switch (file.role()) {
                case BASE_COLOR -> baseColor = path;
                case NORMAL -> normal = path;
                case OCCLUSION_ROUGHNESS_METALLIC -> arm = path;
                case ROUGHNESS -> roughness = path;
                case AMBIENT_OCCLUSION -> occlusion = path;
                default -> { }
            }
        }
        // Packed ARM (Poly Haven) serves both slots; otherwise a grey roughness map is read through the roughness slot (glTF reads its
        // green channel) and the grey occlusion map through the occlusion slot (its red channel)
        String roughnessSlot = arm != null ? arm : roughness;
        String occlusionSlot = arm != null ? arm : occlusion;
        String name = materialName(asset.id());
        String path = MaterialAssets.FOLDER + "/" + name + MaterialAssets.EXTENSION;
        File file = new File(assetsRoot, path);
        Material material;
        if (file.isFile()) {
            try {
                material = MaterialIO.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            } catch (MaterialFormatException | IllegalArgumentException e) {
                throw new IOException("The texture files are in " + asset.folder() + ", but " + path
                    + " is unreadable and was left as it is: " + e.getMessage(), e);
            }
            String prefix = asset.folder() + "/";
            replaceIfOurs(material, Material.Slot.BASE_COLOR, baseColor, prefix);
            replaceIfOurs(material, Material.Slot.NORMAL, normal, prefix);
            replaceIfOurs(material, Material.Slot.METALLIC_ROUGHNESS, roughnessSlot, prefix);
            replaceIfOurs(material, Material.Slot.OCCLUSION, occlusionSlot, prefix);
        } else {
            material = new Material();
            material.setName(name);
            // The maps carry the look; the factors multiply them, as in glTF, so they are 1 (the shipped Poly Haven materials do the
            // same). A grey roughness map has the roughness in its blue channel too, which glTF reads as metalness, so metalness is 0
            material.setMetallic(arm != null ? 1f : 0f);
            material.setRoughness(1f);
            material.setTexture(Material.Slot.BASE_COLOR, baseColor);
            material.setTexture(Material.Slot.NORMAL, normal);
            material.setTexture(Material.Slot.METALLIC_ROUGHNESS, roughnessSlot);
            material.setTexture(Material.Slot.OCCLUSION, occlusionSlot);
        }
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), MaterialIO.toJson(material), StandardCharsets.UTF_8);
        return path;
    }

    private static void replaceIfOurs(Material material, Material.Slot slot, String replacement, String prefix) {
        String current = material.getTexture(slot);
        if (replacement != null && current != null && current.startsWith(prefix)) {
            material.setTexture(slot, replacement);
        }
    }

    /** The material's name for an asset id: {@code red_brick_03} gives {@code RedBrick03}. */
    public static String materialName(String id) {
        StringBuilder name = new StringBuilder();
        for (String part : id.split("[_\\-\\s]+")) {
            if (!part.isEmpty()) {
                name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return name.isEmpty() ? "Material" : name.toString();
    }

    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String extension(String path) {
        String name = fileName(path);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static MessageDigest digest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("The JDK has no " + algorithm, e);
        }
    }

    private static void deleteTree(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
