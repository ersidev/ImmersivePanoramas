package xyz.ersi.immersivepanoramas.client;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import org.lwjgl.system.MemoryUtil;
import xyz.ersi.immersivepanoramas.mixin.client.NativeImageAccessor;
import xyz.ersi.immersivepanoramas.client.CustomPanoramaManager.CapturedView;
import xyz.ersi.immersivepanoramas.platform.Services;

import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

final class PanoramaStorage {
    private static final Gson GSON = new Gson();
    private static final String CURRENT_FILE = "current";
    private static final String VIEW_FILE = "view.json";
    private static final String CAPTURE_PREFIX = "capture-";
    private static final String PENDING_SUFFIX = ".pending";
    private static final Pattern CAPTURE_NAME = Pattern.compile(CAPTURE_PREFIX + "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    private static final long SHUTDOWN_SAVE_TIMEOUT = TimeUnit.SECONDS.toNanos(15);

    private static SavedCapture queuedSave;
    private static boolean saveWorkerRunning;

    private PanoramaStorage() {}

    static CapturedView load(CustomCubeMapTexture texture) {
        Path root = panoramaDirectory();
        cleanAbandonedCaptures(root);
        Path published = readPublishedDirectory(root);
        if (published != null) {
            CapturedView view = readView(published);
            if (loadFromDirectory(texture, published)) {
                return view;
            }
            // another instance may have replaced this save during loading
            Path latest = readPublishedDirectory(root);
            if (latest != null && !latest.equals(published)) {
                view = readView(latest);
                if (loadFromDirectory(texture, latest)) {
                    return view;
                }
            }
        }
        return loadFromDirectory(texture, root) ? readView(root) : null;
    }

    private static boolean loadFromDirectory(CustomCubeMapTexture texture, Path directory) {
        if (!texture.loadFromDirectory(directory)) {
            return false;
        }
        ModLogger.info("Successfully loaded custom world panorama from {}", directory);
        return true;
    }

    private static CapturedView readView(Path directory) {
        try (Reader reader = Files.newBufferedReader(directory.resolve(VIEW_FILE))) {
            CapturedView view = GSON.fromJson(reader, CapturedView.class);
            if (view != null && Float.isFinite(view.yaw()) && Math.abs(view.pitch()) <= 90.0F && Float.isFinite(view.fov())) {
                return view;
            }
            ModLogger.warn("Invalid panorama view in {}", directory);
        } catch (NoSuchFileException e) {
            // older panoramas only contain the face images
        } catch (IOException | JsonParseException e) {
            ModLogger.warn("Failed to read panorama view from {}", directory, e);
        }
        return null;
    }

    static synchronized void queueSave(NativeImage[] faces, CapturedView view) {
        // the writer owns its active capture, only the waiting capture can be replaced here
        if (queuedSave != null) {
            closeImages(queuedSave.faces());
        }
        queuedSave = new SavedCapture(faces, view);
        if (!saveWorkerRunning) {
            saveWorkerRunning = true;
            try {
                Util.ioPool().execute(PanoramaStorage::drainSaves);
            } catch (RuntimeException | Error e) {
                queuedSave = null;
                saveWorkerRunning = false;
                throw e;
            }
        }
    }

    static synchronized void awaitPendingSaves() {
        long deadline = System.nanoTime() + SHUTDOWN_SAVE_TIMEOUT;
        while (saveWorkerRunning) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                ModLogger.warn("Timed out waiting for panorama save on shutdown");
                return;
            }
            try {
                TimeUnit.NANOSECONDS.timedWait(PanoramaStorage.class, remaining);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ModLogger.warn("Interrupted while waiting for panorama save on shutdown");
                return;
            }
        }
    }

    private static void drainSaves() {
        try {
            SavedCapture save;
            while ((save = takeNextSave()) != null) {
                try {
                    writeCapture(panoramaDirectory(), save);
                } catch (IOException | RuntimeException e) {
                    ModLogger.error("Failed to save panorama", e);
                } finally {
                    closeImages(save.faces());
                }
            }
        } catch (RuntimeException | Error e) {
            synchronized (PanoramaStorage.class) {
                if (queuedSave != null) {
                    closeImages(queuedSave.faces());
                }
                queuedSave = null;
                saveWorkerRunning = false;
                PanoramaStorage.class.notifyAll();
            }
            throw e;
        }
    }

    private static synchronized SavedCapture takeNextSave() {
        SavedCapture save = queuedSave;
        queuedSave = null;
        if (save == null) {
            saveWorkerRunning = false;
            PanoramaStorage.class.notifyAll();
        }
        return save;
    }

    private static void writeCapture(Path root, SavedCapture save) throws IOException {
        Files.createDirectories(root);
        try (FileChannel channel = openSaveLock(root); FileLock lock = channel.lock()) {
            publishCapture(root, save);
        }
    }

    private static FileChannel openSaveLock(Path root) throws IOException {
        return FileChannel.open(root.resolve(".capture.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }

    private static void publishCapture(Path root, SavedCapture save) throws IOException {
        String name = CAPTURE_PREFIX + UUID.randomUUID();
        Path capture = root.resolve(name);
        Path marker = root.resolve("." + name + PENDING_SUFFIX);
        NativeImage[] faces = save.faces();
        Files.writeString(marker, name, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        boolean published = false;
        try {
            Files.createDirectory(capture);
            for (int i = 0; i < faces.length; i++) {
                makeOpaque(faces[i]);
                faces[i].writeToFile(capture.resolve(faceFileName(i)));
            }
            Files.writeString(capture.resolve(VIEW_FILE), GSON.toJson(save.view()));

            Path previous = readPublishedDirectory(root);
            // publish the faces and their view together
            Files.move(marker, root.resolve(CURRENT_FILE), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            published = true;
            if (previous != null) {
                deleteCapture(previous, faces.length);
            }
        } finally {
            if (!published) {
                deleteCapture(capture, faces.length);
                if (Files.notExists(capture, LinkOption.NOFOLLOW_LINKS)) {
                    deletePath(marker);
                }
            }
        }
    }

    private static void cleanAbandonedCaptures(Path root) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (FileChannel channel = openSaveLock(root); FileLock lock = channel.tryLock()) {
            if (lock == null) {
                return;
            }
            // only captures using this lock leave pending markers
            try (DirectoryStream<Path> markers = Files.newDirectoryStream(root, "." + CAPTURE_PREFIX + "*" + PENDING_SUFFIX)) {
                for (Path marker : markers) {
                    String filename = marker.getFileName().toString();
                    String name = filename.substring(1, filename.length() - PENDING_SUFFIX.length());
                    if (CAPTURE_NAME.matcher(name).matches()) {
                        Path capture = root.resolve(name);
                        deleteCapture(capture, PanoramaCapture.FACE_COUNT);
                        if (Files.notExists(capture, LinkOption.NOFOLLOW_LINKS)) {
                            deletePath(marker);
                        }
                    }
                }
            }
        } catch (OverlappingFileLockException e) {
            // another writer in this process owns the lock
        } catch (IOException | RuntimeException e) {
            ModLogger.warn("Failed to clean up abandoned panorama captures", e);
        }
    }

    private static Path readPublishedDirectory(Path root) {
        try {
            String name = Files.readString(root.resolve(CURRENT_FILE));
            if (CAPTURE_NAME.matcher(name).matches()) {
                Path capture = root.resolve(name);
                if (Files.isDirectory(capture, LinkOption.NOFOLLOW_LINKS)) {
                    return capture;
                }
            }
            ModLogger.error("Invalid saved panorama reference in {}", root.resolve(CURRENT_FILE));
        } catch (NoSuchFileException e) {
            // older saves store their faces directly in the panorama directory
        } catch (IOException e) {
            ModLogger.error("Failed to read saved panorama reference", e);
        }
        return null;
    }

    private static void deleteCapture(Path capture, int faceCount) {
        if (Files.isDirectory(capture, LinkOption.NOFOLLOW_LINKS)) {
            for (int i = 0; i < faceCount; i++) {
                deletePath(capture.resolve(faceFileName(i)));
            }
            deletePath(capture.resolve(VIEW_FILE));
            deletePath(capture);
        }
    }

    private static void deletePath(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            ModLogger.warn("Failed to remove old panorama file {}", path, e);
        }
    }

    private static Path panoramaDirectory() {
        return Services.PLATFORM.getConfigDir().resolve("immersivepanoramas").resolve("panorama");
    }

    static String faceFileName(int face) {
        return "panorama_" + face + ".png";
    }

    static ByteBuffer pixels(NativeImage face) {
        long pointer = ((NativeImageAccessor) (Object) face).getPixelsPointer();
        return MemoryUtil.memByteBuffer(pointer, face.getWidth() * face.getHeight() * Integer.BYTES);
    }

    static void makeOpaque(NativeImage face) {
        // shader packs can leave the world render target transparent
        ByteBuffer pixels = pixels(face);
        for (int offset = 3; offset < pixels.limit(); offset += Integer.BYTES) {
            pixels.put(offset, (byte) 255);
        }
    }

    static void closeImages(NativeImage[] faces) {
        if (faces != null) {
            for (NativeImage face : faces) {
                if (face != null) {
                    face.close();
                }
            }
        }
    }

    private record SavedCapture(NativeImage[] faces, CapturedView view) {}
}
