package xyz.ersi.immersivepanoramas.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.LevelResource;
import xyz.ersi.immersivepanoramas.client.config.ModConfig;
import xyz.ersi.immersivepanoramas.mixin.client.PanoramaAccessor;
import xyz.ersi.immersivepanoramas.mixin.client.ScreenAccessor;

import java.util.Locale;

public class CustomPanoramaManager {
    public static final ResourceLocation CUSTOM_PANORAMA_ID = ResourceLocation.fromNamespaceAndPath("immersivepanoramas", "panorama");

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
        return world + "|" + client.level.dimension().location();
    }

    public static synchronized void init(Minecraft client) {
        if (customTexture == null) {
            customTexture = new CustomCubeMapTexture(CUSTOM_PANORAMA_ID, client.getTextureManager());
        }
        if (customCubeMap == null) {
            customCubeMap = new CubeMap(CUSTOM_PANORAMA_ID);
        }
        capturedView = PanoramaStorage.load(customTexture);
        showCaptureAngle = false;
    }

    public static synchronized boolean hasCustomPanorama() {
        return customTexture != null && customTexture.isLoaded();
    }

    public static synchronized void render(Minecraft client, float rotX, float rotY, float alpha) {
        if (customCubeMap != null && hasCustomPanorama()) {
            customCubeMap.render(client, rotX, showCaptureAngle ? 0.0F : rotY, alpha);
            if (showCaptureAngle) {
                showCaptureAngle = false;
                ((PanoramaAccessor) ScreenAccessor.getPanorama()).setSpin(0.0F);
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

    static synchronized void uploadCapture(Minecraft client, NativeImage[] faces, CapturedView view) {
        if (customTexture == null) {
            customTexture = new CustomCubeMapTexture(CUSTOM_PANORAMA_ID, client.getTextureManager());
        }
        if (customCubeMap == null) {
            customCubeMap = new CubeMap(CUSTOM_PANORAMA_ID);
        }
        customTexture.uploadFaces(faces);
        capturedView = view;
        showCaptureAngle = true;
        ModLogger.info("Uploaded custom panorama faces");
    }

    public static synchronized void applyPanoramaRotation(Minecraft client) {
        CapturedView view = capturedView;
        if (client.player == null || !ModConfig.getInstance().alignPlayerRotation || !hasCustomPanorama()
            || view == null || view.worldKey() == null || !view.worldKey().equals(getWorldKey(client))) {
            return;
        }

        float currentSpin = ((PanoramaAccessor) ScreenAccessor.getPanorama()).getSpin();

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

    record CapturedView(String worldKey, float yaw, float pitch, float fov) {}
}
