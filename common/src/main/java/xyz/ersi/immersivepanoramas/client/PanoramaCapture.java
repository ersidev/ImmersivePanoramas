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
import net.minecraft.util.Util;
import xyz.ersi.immersivepanoramas.client.config.ModConfig;
import xyz.ersi.immersivepanoramas.mixin.client.PanoramaAccessor;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public class PanoramaCapture {
    private static boolean isCapturing = false;

    public static void captureOnDisconnect(Minecraft client) {
        if (client.level == null || client.player == null || isCapturing) {
            FrozenWorldRenderer.clear();
            return;
        }

        boolean isSingleplayer = client.isLocalServer() || (client.getSingleplayerServer() != null);
        ModConfig config = ModConfig.getInstance();
        if (!config.enabled || (isSingleplayer && !config.enableSingleplayer) || !config.isAllowed(client)) {
            FrozenWorldRenderer.clear();
            return;
        }

        isCapturing = true;
        try {
            Path panoramaDir = CustomPanoramaManager.getPanoramaDir();
            Files.createDirectories(panoramaDir);

            int ow = client.getWindow().getWidth();
            int oh = client.getWindow().getHeight();
            RenderTarget target = client.gameRenderer.mainRenderTarget();

            float xRot = client.player.getXRot();
            float yRot = client.player.getYRot();
            float xRotO = client.player.xRotO;
            float yRotO = client.player.yRotO;
            CameraType originalCameraType = client.options.getCameraType();

            CustomPanoramaManager.setSavedPitch(xRot);
            Panorama panorama = client.gameRenderer.panorama();
            if (panorama != null) {
                ((PanoramaAccessor) panorama).setSpin(0.0F);
                panorama.startSpin();
            }

            int captureSize = 1024;
            client.gameRenderer.setRenderBlockOutline(false);
            Camera camera = client.gameRenderer.mainCamera();
            NativeImage[] capturedFaces = new NativeImage[6];

            try {
                if (originalCameraType != CameraType.FIRST_PERSON) {
                    client.options.setCameraType(CameraType.FIRST_PERSON);
                }
                camera.enablePanoramicMode();
                client.getWindow().setWidth(captureSize);
                client.getWindow().setHeight(captureSize);
                target.resize(captureSize, captureSize);

                GpuTexture colorTexture = target.getColorTexture();
                if (colorTexture == null) {
                    throw new IllegalStateException("Main render target has no color texture");
                }
                int blockSize = colorTexture.getFormat().blockSize();
                long bufferSize = (long) captureSize * captureSize * blockSize;
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

                    client.gameRenderer.update(DeltaTracker.ONE);
                    client.gameRenderer.extract(DeltaTracker.ONE, true);
                    client.gameRenderer.renderLevel();

                    final int faceIndex = i;
                    try (GpuBuffer buffer = device.createBuffer(
                        () -> "Panorama capture face " + faceIndex,
                        GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                        bufferSize
                    )) {
                        CommandEncoder encoder = device.createCommandEncoder();
                        encoder.copyTextureToBuffer(colorTexture, buffer, 0L, () -> {}, 0);
                        try (GpuFence fence = encoder.createFence()) {
                            encoder.submit();
                            fence.awaitCompletion(GpuFence.NO_TIMEOUT);
                        }

                        try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
                            ByteBuffer data = view.data();
                            NativeImage image = new NativeImage(captureSize, captureSize, false);
                            for (int y = 0; y < captureSize; y++) {
                                for (int x = 0; x < captureSize; x++) {
                                    int pixel = data.getInt((x + y * captureSize) * blockSize);
                                    image.setPixelABGR(x, captureSize - y - 1, pixel | 0xFF000000);
                                }
                            }
                            capturedFaces[i] = image;
                        }
                    }
                    RenderSystem.getDynamicUniforms().reset();
                }

                boolean allCaptured = true;
                for (int i = 0; i < 6; i++) {
                    if (capturedFaces[i] == null) {
                        allCaptured = false;
                        break;
                    }
                }

                if (allCaptured) {
                    CustomPanoramaManager.uploadFaces(capturedFaces);
                    if (isSingleplayer) {
                        FrozenWorldRenderer.setAllowedForSavingScreen(true);
                    }

                    Util.ioPool().execute(() -> {
                        for (int i = 0; i < 6; i++) {
                            try (NativeImage img = capturedFaces[i]) {
                                Path file = panoramaDir.resolve("panorama_" + i + ".png");
                                img.writeToFile(file);
                            } catch (Exception e) {
                                ModLogger.error("Failed to save panorama face {}", i, e);
                            }
                        }
                    });
                } else {
                    ModLogger.warn("Not all panorama faces were captured successfully");
                    FrozenWorldRenderer.clear();
                    for (int i = 0; i < 6; i++) {
                        if (capturedFaces[i] != null) {
                            capturedFaces[i].close();
                        }
                    }
                }
            } finally {
                if (originalCameraType != CameraType.FIRST_PERSON) {
                    client.options.setCameraType(originalCameraType);
                }
                client.player.setXRot(xRot);
                client.player.setYRot(yRot);
                client.player.xRotO = xRotO;
                client.player.yRotO = yRotO;
                client.gameRenderer.setRenderBlockOutline(true);
                client.getWindow().setWidth(ow);
                client.getWindow().setHeight(oh);
                target.resize(ow, oh);
                camera.disablePanoramicMode();
            }
        } catch (Exception e) {
            ModLogger.error("Failed to capture panorama on disconnect", e);
            FrozenWorldRenderer.clear();
        } finally {
            isCapturing = false;
        }
    }
}
