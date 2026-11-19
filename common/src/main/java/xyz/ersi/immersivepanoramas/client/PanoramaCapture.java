package xyz.ersi.immersivepanoramas.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.client.renderer.PanoramicScreenshotParameters;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.system.MemoryUtil;
import xyz.ersi.immersivepanoramas.client.CustomPanoramaManager.CapturedView;
import xyz.ersi.immersivepanoramas.client.config.ModConfig;
import xyz.ersi.immersivepanoramas.client.config.ModConfig.CaptureResolution;
import xyz.ersi.immersivepanoramas.mixin.client.GameRendererAccessor;
import xyz.ersi.immersivepanoramas.mixin.client.PanoramaAccessor;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

public class PanoramaCapture {
    private static final int FACE_SIZE_STEP = 64;
    private static final int FACE_COUNT = 6;
    private static final long READBACK_TIMEOUT = TimeUnit.SECONDS.toNanos(10);

    private static boolean isCapturing;
    private static CameraView captureView;
    private static PendingCapture pendingCapture;

    public static boolean isCapturing() {
        return isCapturing;
    }

    public static void captureOnDisconnect(Minecraft client) {
        LocalPlayer player = client.player;
        if (client.level == null || player == null || isCapturing) {
            return;
        }
        ModConfig config = ModConfig.getInstance();
        if (!config.isAllowed(client)) {
            return;
        }

        CameraView view = FrozenWorldRenderer.getCapturedView(client);
        float yaw = view == null ? player.getYRot() : view.yaw();
        float pitch = view == null ? player.getXRot() : view.pitch();
        float fov = view == null ? 0.0F : view.fov();
        int faceSize = config.captureResolution == CaptureResolution.AUTO
            ? chooseFaceSize(client.getMainRenderTarget().height, fov)
            : config.captureResolution.getFaceSize();
        CapturedView capturedView = new CapturedView(CustomPanoramaManager.getWorldKey(client), yaw, Mth.clamp(pitch, -90.0F, 90.0F), fov);
        PendingCapture capture = new PendingCapture(capturedView, faceSize);
        isCapturing = true;
        try {
            close();
            captureFaces(client, player, capture, view);
            capture.submittedAt = System.nanoTime();
            pendingCapture = capture;
        } catch (Exception e) {
            capture.close();
            ModLogger.error("Failed to capture panorama on disconnect", e);
        } finally {
            isCapturing = false;
        }
    }

    public static CameraView getCaptureView(Camera camera) {
        if (captureView == null || camera != Minecraft.getInstance().gameRenderer.getMainCamera()) {
            return null;
        }
        return captureView;
    }

    private static int chooseFaceSize(int framebufferHeight, float fov) {
        float menuFov = fov > 0.0F && fov < 180.0F ? fov : 85.0F;
        // a 90-degree face needs more pixels when the same view is shown at a narrower FOV
        double pixels = framebufferHeight / Math.tan(Math.toRadians(menuFov) / 2.0);
        double boundedPixels = Mth.clamp(pixels, CaptureResolution.STANDARD.getFaceSize(), CaptureResolution.HIGH.getFaceSize());
        return (int) Math.ceil(boundedPixels / FACE_SIZE_STEP) * FACE_SIZE_STEP;
    }

    private static void captureFaces(Minecraft client, LocalPlayer player, PendingCapture capture, CameraView view) {
        int windowWidth = client.getWindow().getWidth();
        int windowHeight = client.getWindow().getHeight();
        RenderTarget target = client.getMainRenderTarget();
        int targetWidth = target.width;
        int targetHeight = target.height;
        float pitch = player.getXRot();
        float originalYaw = player.getYRot();
        float yaw = capture.view.yaw();
        float previousPitch = player.xRotO;
        float previousYaw = player.yRotO;
        CameraType cameraType = client.options.getCameraType();
        PanoramicScreenshotParameters previousParameters = client.gameRenderer.getPanoramicScreenshotParameters();
        boolean renderBlockOutline = ((GameRendererAccessor) client.gameRenderer).isRenderBlockOutline();
        boolean shadersActive = IrisCompat.isShaderPackInUse();
        int faceSize = capture.faceSize;
        int faceBytes = faceSize * faceSize * Integer.BYTES;
        Vec3 position = view == null ? null : view.position();

        try {
            client.gameRenderer.setRenderBlockOutline(false);
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.gameRenderer.setPanoramicScreenshotParameters(new PanoramicScreenshotParameters(
                new Vector3f(client.gameRenderer.getMainCamera().forwardVector())
            ));

            if (view != null) {
                renderView(client, view);
                FrozenWorldRenderer.freeze(target);
                resetCaptureUniforms();
            }

            client.getWindow().setWidth(faceSize);
            client.getWindow().setHeight(faceSize);
            target.resize(faceSize, faceSize);

            GpuTexture colorTexture = target.getColorTexture();
            if (colorTexture == null || colorTexture.getFormat() != TextureFormat.RGBA8) {
                throw new IllegalStateException("Panorama capture requires an RGBA8 main render target");
            }

            GpuDevice device = RenderSystem.getDevice();
            capture.buffer = device.createBuffer(
                () -> "Panorama capture",
                GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                (long) faceBytes * FACE_COUNT
            );

            if (shadersActive) {
                // turn the camera once after resizing so the first saved face rejects stale shader history
                position = renderFace(client, player, (yaw - 90.0F) % 360.0F, 0.0F, position);
                resetCaptureUniforms();
            }

            for (int i = 0; i < FACE_COUNT; i++) {
                float faceYaw = switch (i) {
                    case 1 -> (yaw + 90.0F) % 360.0F;
                    case 2 -> (yaw + 180.0F) % 360.0F;
                    case 3 -> (yaw - 90.0F) % 360.0F;
                    default -> yaw;
                };
                float facePitch = switch (i) {
                    case 4 -> -90.0F;
                    case 5 -> 90.0F;
                    default -> 0.0F;
                };
                position = renderFace(client, player, faceYaw, facePitch, position);

                CommandEncoder encoder = device.createCommandEncoder();
                encoder.copyTextureToBuffer(colorTexture, capture.buffer, (long) i * faceBytes, () -> {}, 0);
                if (i == FACE_COUNT - 1) {
                    capture.fence = encoder.createFence();
                }
                resetCaptureUniforms();
            }
        } finally {
            player.setXRot(pitch);
            player.setYRot(originalYaw);
            player.xRotO = previousPitch;
            player.yRotO = previousYaw;
            client.options.setCameraType(cameraType);
            client.gameRenderer.setRenderBlockOutline(renderBlockOutline);
            client.getWindow().setWidth(windowWidth);
            client.getWindow().setHeight(windowHeight);
            client.gameRenderer.setPanoramicScreenshotParameters(previousParameters);
            target.resize(targetWidth, targetHeight);
        }
    }

    private static void resetCaptureUniforms() {
        RenderSystem.getDynamicUniforms().reset();
        if (RenderSystem.getDevice() instanceof GlDevice) {
            // flush the buffer reuse fences before a later face waits on them
            GL11C.glFlush();
        }
    }

    private static Vec3 renderFace(Minecraft client, LocalPlayer player, float yaw, float pitch, Vec3 position) {
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = player.getYRot();
        player.xRotO = player.getXRot();
        return renderView(client, new CameraView(position, yaw, pitch, 90.0F));
    }

    private static Vec3 renderView(Minecraft client, CameraView view) {
        captureView = view;
        try (Gizmos.TemporaryCollection gizmos = client.levelRenderer.collectPerFrameGizmos()) {
            client.gameRenderer.updateCamera(DeltaTracker.ONE);
            Vec3 position = client.gameRenderer.getMainCamera().position();
            client.gameRenderer.renderLevel(DeltaTracker.ONE);
            return position;
        } finally {
            captureView = null;
        }
    }

    public static void processPendingCapture(Minecraft client) {
        PendingCapture capture = pendingCapture;
        if (capture == null) {
            return;
        }

        NativeImage[] faces = null;
        try {
            if (!capture.fence.awaitCompletion(0L)) {
                if (System.nanoTime() - capture.submittedAt > READBACK_TIMEOUT) {
                    throw new IllegalStateException("Timed out waiting for panorama readback");
                }
                return;
            }
            pendingCapture = null;
            faces = new NativeImage[FACE_COUNT];
            int faceSize = capture.faceSize;
            int rowBytes = faceSize * Integer.BYTES;
            int faceBytes = faceSize * rowBytes;
            try (capture) {
                try (GpuBuffer.MappedView view = RenderSystem.getDevice().createCommandEncoder().mapBuffer(capture.buffer, true, false)) {
                    ByteBuffer pixels = view.data();
                    for (int i = 0; i < FACE_COUNT; i++) {
                        NativeImage face = new NativeImage(faceSize, faceSize, false);
                        faces[i] = face;
                        ByteBuffer destination = MemoryUtil.memByteBuffer(face.getPointer(), faceBytes);
                        int faceOffset = i * faceBytes;
                        for (int y = faceSize - 1; y >= 0; y--) {
                            int rowOffset = faceOffset + y * rowBytes;
                            int destinationOffset = (faceSize - 1 - y) * rowBytes;
                            destination.put(destinationOffset, pixels, rowOffset, rowBytes);
                        }
                    }
                }
                CustomPanoramaManager.uploadCapture(faces, capture.view);
            }

            PanoramaRenderer panorama = client.gameRenderer.getPanorama();
            if (panorama != null) {
                ((PanoramaAccessor) panorama).setSpin(0.0F);
            }
            PanoramaStorage.queueSave(faces, capture.view);
            faces = null;
        } catch (Exception e) {
            if (pendingCapture == capture) {
                close();
            }
            ModLogger.error("Failed to finish panorama capture", e);
        } finally {
            if (faces != null) {
                PanoramaStorage.closeImages(faces);
            }
        }
    }

    static boolean hasPendingCapture() {
        return pendingCapture != null;
    }

    public static void close() {
        PendingCapture capture = pendingCapture;
        pendingCapture = null;
        if (capture != null) {
            capture.close();
        }
    }

    public record CameraView(Vec3 position, float yaw, float pitch, float fov) {}

    private static final class PendingCapture implements AutoCloseable {
        private final CapturedView view;
        private final int faceSize;
        private long submittedAt;
        private GpuBuffer buffer;
        private GpuFence fence;

        private PendingCapture(CapturedView view, int faceSize) {
            this.view = view;
            this.faceSize = faceSize;
        }

        @Override
        public void close() {
            try {
                if (fence != null) {
                    fence.close();
                }
            } finally {
                if (buffer != null) {
                    buffer.close();
                }
            }
        }
    }
}
