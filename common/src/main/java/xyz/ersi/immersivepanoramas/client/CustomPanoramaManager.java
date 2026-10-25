package xyz.ersi.immersivepanoramas.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import xyz.ersi.immersivepanoramas.client.config.ModConfig;
import xyz.ersi.immersivepanoramas.mixin.client.PanoramaAccessor;
import xyz.ersi.immersivepanoramas.platform.Services;

import java.nio.file.Path;

public class CustomPanoramaManager {
    public static final Identifier CUSTOM_PANORAMA_ID = Identifier.fromNamespaceAndPath("immersivepanoramas", "panorama");

    private static CubeMap customCubeMap;
    private static CustomCubeMapTexture customTexture;
    private static float savedPitch = 10.0F;

    public static float getSavedPitch() {
        return savedPitch;
    }

    public static void setSavedPitch(float pitch) {
        savedPitch = Mth.clamp(pitch, -90.0F, 90.0F);
    }

    public static Path getPanoramaDir() {
        return Services.PLATFORM.getConfigDir().resolve("immersivepanoramas").resolve("panorama");
    }

    public static synchronized void init(Minecraft client) {
        if (customTexture == null) {
            customTexture = new CustomCubeMapTexture(CUSTOM_PANORAMA_ID);
        }
        if (customCubeMap == null) {
            customCubeMap = new CubeMap(CUSTOM_PANORAMA_ID);
        }
        registerTextures(client.getTextureManager());
        loadFromDisk();
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
            customCubeMap.render(rotX, rotY);
        }
    }

    public static synchronized void uploadFaces(NativeImage[] faces) {
        if (customTexture == null) {
            customTexture = new CustomCubeMapTexture(CUSTOM_PANORAMA_ID);
        }
        if (customCubeMap == null) {
            customCubeMap = new CubeMap(CUSTOM_PANORAMA_ID);
        }
        registerTextures(Minecraft.getInstance().getTextureManager());
        customTexture.uploadFaces(faces);
        ModLogger.info("Uploaded custom panorama faces directly to GPU memory");
    }

    public static synchronized void loadFromDisk() {
        Path panoramaDir = getPanoramaDir();
        if (customTexture != null) {
            boolean success = customTexture.loadFromDirectory(panoramaDir);
            if (success) {
                ModLogger.info("Successfully loaded custom world panorama from {}", panoramaDir);
            }
        }
    }

    public static synchronized void applyPanoramaRotation(Minecraft client) {
        if (client.player == null || !ModConfig.getInstance().alignPlayerRotation) {
            return;
        }

        float currentSpin = 0.0F;
        if (client.gameRenderer != null && client.gameRenderer.panorama() != null) {
            currentSpin = ((PanoramaAccessor) client.gameRenderer.panorama()).getSpin();
        }

        if (Math.abs(currentSpin) > 0.001F) {
            float targetYaw = Mth.wrapDegrees(client.player.getYRot() + currentSpin);
            client.player.setYRot(targetYaw);
            client.player.yRotO = targetYaw;
            client.player.setYHeadRot(targetYaw);
            client.player.yHeadRotO = targetYaw;
            client.player.setYBodyRot(targetYaw);
            client.player.yBodyRotO = targetYaw;
            ModLogger.info("Seamlessly aligned player yaw with panorama: {}° (offset by {}°)", targetYaw, currentSpin);
        }

        if (hasCustomPanorama()) {
            float targetPitch = getSavedPitch();
            client.player.setXRot(targetPitch);
            client.player.xRotO = targetPitch;
            ModLogger.info("Seamlessly aligned player pitch with panorama: {}°", targetPitch);
        }
    }

    public static synchronized void close() {
        if (customCubeMap != null) {
            customCubeMap.close();
            customCubeMap = null;
        }
        if (customTexture != null) {
            customTexture.close();
            customTexture = null;
        }
    }
}
