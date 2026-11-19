package xyz.ersi.immersivepanoramas.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL32C;
import xyz.ersi.immersivepanoramas.client.config.ModConfig;
import xyz.ersi.immersivepanoramas.client.config.ModConfig.CaptureResolution;
import xyz.ersi.immersivepanoramas.mixin.client.GameRendererAccessor;

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
        PendingCapture capture = new PendingCapture(CustomPanoramaManager.getWorldKey(client), yaw, pitch, fov, faceSize);
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
        // a 90-degree face needs more pixels when the same view is shown at a narrower fov
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
        float yaw = capture.yaw;
        float previousPitch = player.xRotO;
        float previousYaw = player.yRotO;
        CameraType cameraType = client.options.getCameraType();
        boolean wasPanoramic = client.gameRenderer.isPanoramicMode();
        boolean renderBlockOutline = ((GameRendererAccessor) client.gameRenderer).isRenderBlockOutline();
        boolean shadersActive = IrisCompat.isShaderPackInUse();
        int faceSize = capture.faceSize;
        Vec3 position = view == null ? null : view.position();

        try {
            client.gameRenderer.setRenderBlockOutline(false);
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.gameRenderer.setPanoramicMode(true);
            client.levelRenderer.graphicsChanged();

            if (view != null) {
                renderView(client, target, view);
                FrozenWorldRenderer.freeze(target);
            }

            client.getWindow().setWidth(faceSize);
            client.getWindow().setHeight(faceSize);
            target.resize(faceSize, faceSize, Minecraft.ON_OSX);

            capture.allocateReadback();

            if (shadersActive) {
                // turn the camera once after resizing so the first saved face rejects stale shader history
                position = renderFace(client, player, target, (yaw - 90.0F) % 360.0F, 0.0F, position);
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
                position = renderFace(client, player, target, faceYaw, facePitch, position);
                target.bindWrite(false);
                capture.copyFace(i);
            }
            capture.fenceReadback();
        } finally {
            player.setXRot(pitch);
            player.setYRot(originalYaw);
            player.xRotO = previousPitch;
            player.yRotO = previousYaw;
            client.options.setCameraType(cameraType);
            client.gameRenderer.setRenderBlockOutline(renderBlockOutline);
            client.getWindow().setWidth(windowWidth);
            client.getWindow().setHeight(windowHeight);
            client.gameRenderer.setPanoramicMode(wasPanoramic);
            client.levelRenderer.graphicsChanged();
            target.resize(targetWidth, targetHeight, Minecraft.ON_OSX);
            target.bindWrite(true);
        }
    }

    private static Vec3 renderFace(Minecraft client, LocalPlayer player, RenderTarget target, float yaw, float pitch, Vec3 position) {
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = player.getYRot();
        player.xRotO = player.getXRot();
        return renderView(client, target, new CameraView(position, yaw, pitch, 90.0F));
    }

    private static Vec3 renderView(Minecraft client, RenderTarget target, CameraView view) {
        captureView = view;
        try {
            // shader post-processing can leave depth writes disabled
            RenderSystem.depthMask(true);
            target.bindWrite(true);
            // iris expects the same clock as minecraft's normal world renders
            client.gameRenderer.renderLevel(1.0F, Util.getNanos());
            return client.gameRenderer.getMainCamera().getPosition();
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
            if (!capture.awaitCompletion()) {
                if (System.nanoTime() - capture.submittedAt > READBACK_TIMEOUT) {
                    throw new IllegalStateException("Timed out waiting for panorama readback");
                }
                return;
            }
            pendingCapture = null;
            faces = new NativeImage[FACE_COUNT];
            try (capture) {
                capture.readFaces(faces);
                CustomPanoramaManager.uploadCapture(client, faces, capture.worldKey, capture.yaw, capture.pitch, capture.fov);
            }

            CustomPanoramaManager.resetSpin();
            PanoramaStorage.queueSave(faces);
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
        private final String worldKey;
        private final float yaw;
        private final float pitch;
        private final float fov;
        private final int faceSize;
        private long submittedAt;
        private int buffer;
        private long fence;

        private PendingCapture(String worldKey, float yaw, float pitch, float fov, int faceSize) {
            this.worldKey = worldKey;
            this.yaw = yaw;
            this.pitch = pitch;
            this.fov = fov;
            this.faceSize = faceSize;
        }

        private void allocateReadback() {
            int previousPackBuffer = GL32C.glGetInteger(GL32C.GL_PIXEL_PACK_BUFFER_BINDING);
            buffer = GL32C.glGenBuffers();
            GL32C.glBindBuffer(GL32C.GL_PIXEL_PACK_BUFFER, buffer);
            try {
                GL32C.glBufferData(GL32C.GL_PIXEL_PACK_BUFFER, (long) faceSize * faceSize * Integer.BYTES * FACE_COUNT, GL32C.GL_STREAM_READ);
            } finally {
                GL32C.glBindBuffer(GL32C.GL_PIXEL_PACK_BUFFER, previousPackBuffer);
            }
        }

        private void copyFace(int index) {
            int previousPackBuffer = GL32C.glGetInteger(GL32C.GL_PIXEL_PACK_BUFFER_BINDING);
            GL32C.glBindBuffer(GL32C.GL_PIXEL_PACK_BUFFER, buffer);
            try {
                long offset = (long) index * faceSize * faceSize * Integer.BYTES;
                GL32C.glReadPixels(0, 0, faceSize, faceSize, GL32C.GL_RGBA, GL32C.GL_UNSIGNED_BYTE, offset);
            } finally {
                GL32C.glBindBuffer(GL32C.GL_PIXEL_PACK_BUFFER, previousPackBuffer);
            }
        }

        private void fenceReadback() {
            fence = GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            if (fence == 0) {
                throw new IllegalStateException("Could not fence panorama readback");
            }
        }

        private boolean awaitCompletion() {
            int status = GL32C.glClientWaitSync(fence, GL32C.GL_SYNC_FLUSH_COMMANDS_BIT, 0);
            if (status == GL32C.GL_WAIT_FAILED) {
                throw new IllegalStateException("Could not wait for panorama readback");
            }
            return status != GL32C.GL_TIMEOUT_EXPIRED;
        }

        private void readFaces(NativeImage[] faces) {
            int rowBytes = faceSize * Integer.BYTES;
            int faceBytes = faceSize * rowBytes;
            int previousPackBuffer = GL32C.glGetInteger(GL32C.GL_PIXEL_PACK_BUFFER_BINDING);
            GL32C.glBindBuffer(GL32C.GL_PIXEL_PACK_BUFFER, buffer);
            try {
                ByteBuffer pixels = GL32C.glMapBufferRange(GL32C.GL_PIXEL_PACK_BUFFER, 0, (long) faceBytes * FACE_COUNT, GL32C.GL_MAP_READ_BIT);
                if (pixels == null) {
                    throw new IllegalStateException("Could not map panorama readback");
                }
                try {
                    for (int i = 0; i < FACE_COUNT; i++) {
                        NativeImage face = new NativeImage(faceSize, faceSize, false);
                        faces[i] = face;
                        ByteBuffer destination = PanoramaStorage.pixels(face);
                        int faceOffset = i * faceBytes;
                        for (int y = faceSize - 1; y >= 0; y--) {
                            int rowOffset = faceOffset + y * rowBytes;
                            int destinationOffset = (faceSize - 1 - y) * rowBytes;
                            destination.put(destinationOffset, pixels, rowOffset, rowBytes);
                        }
                    }
                } finally {
                    GL32C.glUnmapBuffer(GL32C.GL_PIXEL_PACK_BUFFER);
                }
            } finally {
                GL32C.glBindBuffer(GL32C.GL_PIXEL_PACK_BUFFER, previousPackBuffer);
            }
        }

        @Override
        public void close() {
            try {
                if (fence != 0) {
                    GL32C.glDeleteSync(fence);
                }
            } finally {
                if (buffer != 0) {
                    GL32C.glDeleteBuffers(buffer);
                }
            }
        }
    }
}
