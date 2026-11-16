package xyz.ersi.immersivepanoramas.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.PanoramaRenderState;
import xyz.ersi.immersivepanoramas.client.PanoramaCapture.CameraView;
import xyz.ersi.immersivepanoramas.client.config.ModConfig;
import xyz.ersi.immersivepanoramas.mixin.client.PanoramaAccessor;

import java.util.Optional;

public final class FrozenWorldRenderer {
    private static GpuTexture texture;
    private static GpuTextureView textureView;
    private static ClientLevel capturedLevel;
    private static CameraView capturedView;
    private static Screen disconnectScreen;
    private static Screen savingScreen;
    private static boolean exiting;

    private FrozenWorldRenderer() {}

    public static void beforeGui(Minecraft client, boolean renderedWorld) {
        Screen screen = client.gui.screen();
        if (showsFrozenWorld(screen)) {
            if (!blit(client.gameRenderer.mainRenderTarget())) {
                // screen extraction skipped the panorama, restore its draw before the GUI renders
                float spin = ((PanoramaAccessor) client.gameRenderer.panorama()).getSpin();
                client.gameRenderer.gameRenderState().guiRenderState.panoramaRenderState = new PanoramaRenderState(-spin);
            }
        } else if (!exiting) {
            if (renderedWorld && client.level != null && screen instanceof PauseScreen
                && ModConfig.getInstance().isAllowed(client)) {
                Camera camera = client.gameRenderer.mainCamera();
                capturedLevel = client.level;
                capturedView = camera.isInitialized()
                    ? new CameraView(camera.position(), camera.yRot(), camera.xRot(), camera.getFov())
                    : null;
            } else if (!(screen instanceof PauseScreen) || capturedLevel != client.level) {
                clear();
            }
        }
    }

    static void freeze(RenderTarget target) {
        GpuTexture source = target.getColorTexture();
        if (source == null) {
            clear();
            return;
        }

        try {
            int width = source.getWidth(0);
            int height = source.getHeight(0);
            GpuDevice device = RenderSystem.getDevice();
            if (texture == null || texture.getWidth(0) != width || texture.getHeight(0) != height
                || texture.getFormat() != source.getFormat()) {
                closeTexture();
                int usage = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST;
                texture = device.createTexture(() -> "Frozen world frame", usage, source.getFormat(), width, height, 1, 1);
                textureView = device.createTextureView(texture);
            }

            CommandEncoder encoder = device.createCommandEncoder();
            encoder.copyTextureToTexture(source, texture, 0, 0, 0, 0, 0, width, height);
            encoder.submit();
        } catch (Exception e) {
            ModLogger.error("Failed to copy frozen world frame", e);
            clear();
        }
    }

    public static boolean beginExit(Minecraft client, Screen screen) {
        if (exiting) {
            return false;
        }
        if (capturedLevel == null || capturedLevel != client.level || !ModConfig.getInstance().isAllowed(client)) {
            clear();
        }

        exiting = true;
        if (screen instanceof GenericMessageScreen || screen instanceof ProgressScreen) {
            disconnectScreen = screen;
        }
        return true;
    }

    public static void endExit() {
        exiting = false;
        disconnectScreen = null;
        savingScreen = null;
        if (!PanoramaCapture.hasPendingCapture()) {
            clear();
        }
    }

    public static boolean showsFrozenWorld(Screen screen) {
        if (textureView == null || screen == null) {
            return false;
        }
        if (exiting) {
            // singleplayer renders the supplied disconnect screen once after its server-saving screen
            return screen == savingScreen || screen == disconnectScreen;
        }
        return PanoramaCapture.hasPendingCapture() && (screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen);
    }

    static CameraView getCapturedView(Minecraft client) {
        return capturedLevel == client.level ? capturedView : null;
    }

    public static void setSavingScreen(Screen screen) {
        if (exiting && screen instanceof GenericMessageScreen) {
            savingScreen = screen;
        }
    }

    private static boolean blit(RenderTarget target) {
        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            // this vanilla pass copies RGB even when the world texture has zero alpha
            try (RenderPass pass = encoder.createRenderPass(() -> "Frozen world background",
                target.getColorTextureView(), Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.TRACY_BLIT));
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("InSampler", textureView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.draw(3, 1, 0, 0);
            }
            encoder.submit();
            return true;
        } catch (Exception e) {
            ModLogger.error("Failed to draw frozen world frame", e);
            clear();
            return false;
        }
    }

    public static void clear() {
        capturedLevel = null;
        capturedView = null;
        closeTexture();
    }

    private static void closeTexture() {
        if (textureView != null) {
            textureView.close();
            textureView = null;
        }
        if (texture != null) {
            texture.close();
            texture = null;
        }
    }
}
