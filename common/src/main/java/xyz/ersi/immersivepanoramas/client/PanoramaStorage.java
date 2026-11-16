package xyz.ersi.immersivepanoramas.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.Util;
import org.lwjgl.system.MemoryUtil;
import xyz.ersi.immersivepanoramas.platform.Services;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

final class PanoramaStorage {
    private static final String CURRENT_FILE = "current";
    private static final String CAPTURE_PREFIX = "capture-";
    private static final Pattern CAPTURE_NAME = Pattern.compile(CAPTURE_PREFIX + "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");

    private static NativeImage[] queuedSave;
    private static boolean saveWorkerRunning;

    private PanoramaStorage() {}

    static boolean load(CustomCubeMapTexture texture) {
        Path root = panoramaDirectory();
        Path published = readPublishedDirectory(root);
        if (published != null) {
            if (loadFromDirectory(texture, published)) {
                return true;
            }
            // another instance may have replaced this save during loading
            Path latest = readPublishedDirectory(root);
            if (latest != null && !latest.equals(published) && loadFromDirectory(texture, latest)) {
                return true;
            }
        }
        return loadFromDirectory(texture, root);
    }

    private static boolean loadFromDirectory(CustomCubeMapTexture texture, Path directory) {
        if (!texture.loadFromDirectory(directory)) {
            return false;
        }
        ModLogger.info("Successfully loaded custom world panorama from {}", directory);
        return true;
    }

    static synchronized void queueSave(NativeImage[] faces) {
        // the writer owns its active capture, only the waiting capture can be replaced here
        closeImages(queuedSave);
        queuedSave = faces;
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

    private static void drainSaves() {
        try {
            NativeImage[] faces;
            while ((faces = takeNextSave()) != null) {
                try {
                    writeFaces(panoramaDirectory(), faces);
                } catch (IOException | RuntimeException e) {
                    ModLogger.error("Failed to save panorama", e);
                } finally {
                    closeImages(faces);
                }
            }
        } catch (RuntimeException | Error e) {
            synchronized (PanoramaStorage.class) {
                closeImages(queuedSave);
                queuedSave = null;
                saveWorkerRunning = false;
            }
            throw e;
        }
    }

    private static synchronized NativeImage[] takeNextSave() {
        NativeImage[] faces = queuedSave;
        queuedSave = null;
        if (faces == null) {
            saveWorkerRunning = false;
        }
        return faces;
    }

    private static void writeFaces(Path root, NativeImage[] faces) throws IOException {
        Files.createDirectories(root);
        Path capture = Files.createDirectory(root.resolve(CAPTURE_PREFIX + UUID.randomUUID()));
        Path marker = null;
        boolean published = false;
        try {
            for (int i = 0; i < faces.length; i++) {
                // shader packs can leave the world render target transparent
                NativeImage face = faces[i];
                ByteBuffer pixels = MemoryUtil.memByteBuffer(face.getPointer(), face.getWidth() * face.getHeight() * Integer.BYTES);
                for (int offset = 3; offset < pixels.limit(); offset += Integer.BYTES) {
                    pixels.put(offset, (byte) 255);
                }
                face.writeToFile(capture.resolve(faceFileName(i)));
            }

            Path previous = readPublishedDirectory(root);
            marker = Files.createTempFile(root, ".current-", ".tmp");
            Files.writeString(marker, capture.getFileName().toString());
            // readers see the old marker or the new one, each pointing to a complete set of faces
            Files.move(marker, root.resolve(CURRENT_FILE), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            published = true;
            if (previous != null) {
                deleteCapture(previous, faces.length);
            }
        } finally {
            if (!published) {
                deleteCapture(capture, faces.length);
                if (marker != null) {
                    deletePath(marker);
                }
            }
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

    static void closeImages(NativeImage[] faces) {
        if (faces != null) {
            for (NativeImage face : faces) {
                if (face != null) {
                    face.close();
                }
            }
        }
    }
}
