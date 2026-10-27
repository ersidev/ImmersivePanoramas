package xyz.ersi.immersivepanoramas.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.GpuFence;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Panorama;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Util;
import xyz.ersi.immersivepanoramas.client.config.ModConfig;
import xyz.ersi.immersivepanoramas.mixin.client.PanoramaAccessor;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

public class PanoramaCapture {
    private static final int CAPTURE_SIZE = 1024;
    private static boolean isCapturing;
    private static PendingCapture pendingCapture;
    private static long generation;
    private static CompletableFuture<Void> saveTask = CompletableFuture.completedFuture(null);

    public static void captureOnDisconnect(Minecraft client) {
        if (client.level == null || client.player == null || isCapturing) {
            FrozenWorldRenderer.clear();
            return;
        }

        if (!ModConfig.getInstance().isAllowed(client)) {
            FrozenWorldRenderer.clear();
            return;
        }

        isCapturing = true;
        close();
        PendingCapture capture = new PendingCapture(generation, CustomPanoramaManager.getWorldKey(client), client.player.getXRot());
        try {
            int ow = client.getWindow().getWidth();
            int oh = client.getWindow().getHeight();
            RenderTarget target = client.gameRenderer.mainRenderTarget();
            float xRot = client.player.getXRot();
            float yRot = client.player.getYRot();
            float xRotO = client.player.xRotO;
            float yRotO = client.player.yRotO;
            CameraType originalCameraType = client.options.getCameraType();
            Camera camera = client.gameRenderer.mainCamera();

            try {
                client.gameRenderer.setRenderBlockOutline(false);
                client.options.setCameraType(CameraType.FIRST_PERSON);
                camera.enablePanoramicMode();
                client.getWindow().setWidth(CAPTURE_SIZE);
                client.getWindow().setHeight(CAPTURE_SIZE);
                target.resize(CAPTURE_SIZE, CAPTURE_SIZE);

                GpuTexture colorTexture = target.getColorTexture();
                if (colorTexture == null) {
                    throw new IllegalStateException("Main render target has no color texture");
                }
                capture.blockSize = colorTexture.getFormat().blockSize();
                if (capture.blockSize != 4) {
                    throw new IllegalStateException("Unsupported panorama pixel format: " + colorTexture.getFormat());
                }
                long bufferSize = (long) CAPTURE_SIZE * CAPTURE_SIZE * capture.blockSize;
                GpuDevice device = RenderSystem.getDevice();

                for (int i = 0; i < 6; i++) {
                    switch (i) {
                        case 0 -> { client.player.setYRot(yRot); client.player.setXRot(0.0F); }
                        case 1 -> { client.player.setYRot((yRot + 90.0F) % 360.0F); client.player.setXRot(0.0F); }
                        case 2 -> { client.player.setYRot((yRot + 180.0F) % 360.0F); client.player.setXRot(0.0F); }
                        case 3 -> { client.player.setYRot((yRot - 90.0F) % 360.0F); client.player.setXRot(0.0F); }
                        case 4 -> { client.player.setYRot(yRot); client.player.setXRot(-90.0F); }
                        case 5 -> { client.player.setYRot(yRot); client.player.setXRot(90.0F); }
                    }
                    client.player.yRotO = client.player.getYRot();
                    client.player.xRotO = client.player.getXRot();

                    try (Gizmos.TemporaryCollection gizmos = client.levelExtractor.collectPerFrameMainThreadGizmos()) {
                        client.gameRenderer.update(DeltaTracker.ONE);
                        client.gameRenderer.extract(DeltaTracker.ONE, true);
                        client.gameRenderer.renderLevel();
                    }

                    final int faceIndex = i;
                    capture.buffers[i] = device.createBuffer(
                        () -> "Panorama capture face " + faceIndex,
                        GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                        bufferSize
                    );
                    CommandEncoder encoder = device.createCommandEncoder();
                    encoder.copyTextureToBuffer(colorTexture, capture.buffers[i], 0L, () -> {}, 0);
                    if (i == 5) {
                        capture.fence = encoder.createFence();
                    }
                    encoder.submit();
                    RenderSystem.getDynamicUniforms().reset();
                }
            } finally {
                client.options.setCameraType(originalCameraType);
                client.player.setXRot(xRot);
                client.player.setYRot(yRot);
                client.player.xRotO = xRotO;
                client.player.yRotO = yRotO;
                client.gameRenderer.setRenderBlockOutline(true);
                client.getWindow().setWidth(ow);
                client.getWindow().setHeight(oh);
                camera.disablePanoramicMode();
                target.resize(ow, oh);
            }

            pendingCapture = capture;
            if (client.isLocalServer() || client.getSingleplayerServer() != null) {
                FrozenWorldRenderer.setAllowedForSavingScreen(true);
            }
        } catch (Exception e) {
            capture.close();
            ModLogger.error("Failed to capture panorama on disconnect", e);
            FrozenWorldRenderer.clear();
        } finally {
            isCapturing = false;
        }
    }

    public static void processPendingCapture(Minecraft client) {
        PendingCapture capture = pendingCapture;
        if (capture == null) {
            return;
        }
        try {
            if (!capture.fence.awaitCompletion(0L)) {
                if (System.nanoTime() - capture.startedAt > TimeUnit.SECONDS.toNanos(10)) {
                    throw new IllegalStateException("Timed out waiting for panorama readback");
                }
                return;
            }
            pendingCapture = null;
            byte[][] pixels = new byte[6][];
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = new byte[CAPTURE_SIZE * CAPTURE_SIZE * capture.blockSize];
                try (GpuBufferSlice.MappedView view = capture.buffers[i].map(true, false)) {
                    view.data().get(pixels[i]);
                }
            }
            capture.close();
            Util.ioPool().execute(() -> {
                for (byte[] face : pixels) {
                    for (int offset = 3; offset < face.length; offset += 4) {
                        face[offset] = (byte) 255;
                    }
                }
                client.execute(() -> completeCapture(client, capture, pixels));
            });
        } catch (Exception e) {
            pendingCapture = null;
            capture.close();
            ModLogger.error("Failed to read panorama capture", e);
            FrozenWorldRenderer.clear();
        }
    }

    private static void completeCapture(Minecraft client, PendingCapture capture, byte[][] pixels) {
        if (capture.generation != generation) {
            return;
        }
        NativeImage[] faces = new NativeImage[6];
        boolean saving = false;
        try {
            int rowSize = CAPTURE_SIZE * capture.blockSize;
            for (int i = 0; i < faces.length; i++) {
                faces[i] = new NativeImage(CAPTURE_SIZE, CAPTURE_SIZE, false);
                ByteBuffer destination = faces[i].getPixelBytes();
                for (int y = CAPTURE_SIZE - 1; y >= 0; y--) {
                    destination.put(pixels[i], y * rowSize, rowSize);
                }
            }
            CustomPanoramaManager.uploadFaces(faces);
            CustomPanoramaManager.setSavedPitch(capture.pitch);
            CustomPanoramaManager.setWorldKey(capture.worldKey);
            Panorama panorama = client.gameRenderer.panorama();
            if (panorama != null) {
                ((PanoramaAccessor) panorama).setSpin(0.0F);
                panorama.startSpin();
            }
            Path panoramaDir = CustomPanoramaManager.getPanoramaDir();
            saveTask = saveTask.handle((result, error) -> null).thenRunAsync(() -> {
                try {
                    Files.createDirectories(panoramaDir);
                    for (int i = 0; i < faces.length; i++) {
                        faces[i].writeToFile(panoramaDir.resolve("panorama_" + i + ".png"));
                    }
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, Util.ioPool()).whenComplete((result, error) -> {
                try {
                    if (error != null) {
                        ModLogger.error("Failed to save panorama", error);
                    }
                } finally {
                    closeImages(faces);
                }
            });
            saving = true;
        } catch (Exception e) {
            ModLogger.error("Failed to upload panorama capture", e);
            FrozenWorldRenderer.clear();
        } finally {
            if (!saving) {
                closeImages(faces);
            }
        }
    }

    private static void closeImages(NativeImage[] faces) {
        for (NativeImage face : faces) {
            if (face != null) {
                face.close();
            }
        }
    }

    public static void close() {
        generation++;
        if (pendingCapture != null) {
            pendingCapture.close();
            pendingCapture = null;
        }
    }

    private static final class PendingCapture implements AutoCloseable {
        private final GpuBuffer[] buffers = new GpuBuffer[6];
        private final long generation;
        private final String worldKey;
        private final float pitch;
        private final long startedAt = System.nanoTime();
        private GpuFence fence;
        private int blockSize;

        private PendingCapture(long generation, String worldKey, float pitch) {
            this.generation = generation;
            this.worldKey = worldKey;
            this.pitch = pitch;
        }

        @Override
        public void close() {
            if (fence != null) {
                fence.close();
                fence = null;
            }
            for (int i = 0; i < buffers.length; i++) {
                if (buffers[i] != null) {
                    buffers[i].close();
                    buffers[i] = null;
                }
            }
        }
    }
}
