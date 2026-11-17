package xyz.ersi.immersivepanoramas.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.LevelResource;
import xyz.ersi.immersivepanoramas.client.config.ModConfig;
import xyz.ersi.immersivepanoramas.mixin.client.PanoramaAccessor;

import java.util.Locale;

public class CustomPanoramaManager {
    public static final Identifier CUSTOM_PANORAMA_ID = Identifier.fromNamespaceAndPath("immersivepanoramas", "panorama");

    private static CubeMap customCubeMap;
    private static CustomCubeMapTexture customTexture;
    private static CapturedView capturedView;
    private static boolean showCaptureAngle;

    public static float getSavedPitch() {
        return capturedView == null ? 10.0F : capturedView.pitch();
    }

    public static String getWorldKey(Minecraft client) {
        if (client.level == null) {
            return null;
        }
        String world;
        if (client.getSingleplayerServer() != null) {
            world = "world:" + client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        } else if (client.getCurrentServer() != null) {
            world = "server:" + client.getCurrentServer().ip.trim().toLowerCase(Locale.ROOT);
        } else {
            return null;
        }
        return world + "|" + client.level.dimension().identifier();
    }

    public static synchronized void init(Minecraft client) {
        if (customTexture == null) {
            customTexture = new CustomCubeMapTexture(CUSTOM_PANORAMA_ID);
        }
        if (customCubeMap == null) {
            customCubeMap = new CubeMap(CUSTOM_PANORAMA_ID);
        }
        registerTextures(client.getTextureManager());
        if (PanoramaStorage.load(customTexture)) {
            capturedView = null;
            showCaptureAngle = false;
        }
    }

    public static synchronized void registerTextures(TextureManager textureManager) {
        if (customTexture != null) {
            textureManager.register(CUSTOM_PANORAMA_ID, customTexture);
        }
    }

    public static synchronized boolean hasCustomPanorama() {
        return customTexture != null && customTexture.isLoaded();
    }

    public static synchronized void render(float rotX, float rotY) {
        if (customCubeMap != null && hasCustomPanorama()) {
            customCubeMap.render(rotX, showCaptureAngle ? 0.0F : rotY);
            if (showCaptureAngle) {
                showCaptureAngle = false;
                ((PanoramaAccessor) Minecraft.getInstance().gameRenderer.panorama()).setSpin(0.0F);
            }
        }
    }

    public static float getPanoramaFov(CubeMap cubeMap, float defaultFov) {
        CapturedView view = capturedView;
        if (cubeMap != customCubeMap || view == null || !(view.fov() > 0.0F && view.fov() < 180.0F)) {
            return defaultFov;
        }

        return view.fov();
    }

    static synchronized void uploadCapture(GpuBufferSlice readback, int faceSize, String worldKey, float yaw, float pitch, float fov) {
        if (customTexture == null) {
            customTexture = new CustomCubeMapTexture(CUSTOM_PANORAMA_ID);
        }
        if (customCubeMap == null) {
            customCubeMap = new CubeMap(CUSTOM_PANORAMA_ID);
        }
        registerTextures(Minecraft.getInstance().getTextureManager());
        customTexture.uploadReadback(readback, faceSize);
        capturedView = new CapturedView(worldKey, yaw, Mth.clamp(pitch, -90.0F, 90.0F), fov);
        showCaptureAngle = true;
        ModLogger.info("Uploaded custom panorama faces directly to GPU memory");
    }

    public static synchronized void applyPanoramaRotation(Minecraft client) {
        CapturedView view = capturedView;
        if (client.player == null || !ModConfig.getInstance().alignPlayerRotation || !hasCustomPanorama()
            || view == null || view.worldKey() == null || !view.worldKey().equals(getWorldKey(client))) {
            return;
        }

        float currentSpin = 0.0F;
        if (client.gameRenderer != null && client.gameRenderer.panorama() != null) {
            currentSpin = ((PanoramaAccessor) client.gameRenderer.panorama()).getSpin();
        }

        float targetYaw = Mth.wrapDegrees(view.yaw() + currentSpin);
        client.player.setYRot(targetYaw);
        client.player.yRotO = targetYaw;
        client.player.setYHeadRot(targetYaw);
        client.player.yHeadRotO = targetYaw;
        client.player.setYBodyRot(targetYaw);
        client.player.yBodyRotO = targetYaw;
        client.player.setXRot(view.pitch());
        client.player.xRotO = view.pitch();
        ModLogger.info("Aligned player with panorama at yaw {}° and pitch {}°", targetYaw, view.pitch());
    }

    public static synchronized void close() {
        capturedView = null;
        showCaptureAngle = false;
        if (customCubeMap != null) {
            customCubeMap.close();
            customCubeMap = null;
        }
        if (customTexture != null) {
            customTexture.close();
            customTexture = null;
        }
    }

    private record CapturedView(String worldKey, float yaw, float pitch, float fov) {}
}
