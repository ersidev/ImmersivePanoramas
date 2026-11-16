package xyz.ersi.immersivepanoramas.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.lwjgl.opengl.GL30;
import xyz.ersi.immersivepanoramas.client.PanoramaCapture.CameraView;
import xyz.ersi.immersivepanoramas.client.config.ModConfig;
import xyz.ersi.immersivepanoramas.mixin.client.GameRendererAccessor;

public final class FrozenWorldRenderer {
    private static RenderTarget frozenFrame;
    private static ClientLevel capturedLevel;
    private static CameraView capturedView;
    private static Screen disconnectScreen;
    private static boolean exiting;

    private FrozenWorldRenderer() {}

    public static void beforeGui(Minecraft client, boolean renderedWorld, float partialTick) {
        Screen screen = client.screen;
        if (!exiting && !showsFrozenWorld(screen)) {
            if (renderedWorld && client.level != null && screen instanceof PauseScreen
                && ModConfig.getInstance().isAllowed(client)) {
                Camera camera = client.gameRenderer.getMainCamera();
                capturedLevel = client.level;
                capturedView = camera.isInitialized()
                    ? new CameraView(camera.getPosition(), camera.getYRot(), camera.getXRot(),
                        (float) ((GameRendererAccessor) client.gameRenderer).captureFov(camera, partialTick, true))
                    : null;
            } else if (!(screen instanceof PauseScreen) || capturedLevel != client.level) {
                clear();
            }
        }
    }

    public static boolean renderBackground(Screen screen) {
        return showsFrozenWorld(screen) && blit(Minecraft.getInstance().getMainRenderTarget());
    }

    static void freeze(RenderTarget target) {
        try {
            if (frozenFrame == null || frozenFrame.width != target.width || frozenFrame.height != target.height) {
                closeTexture();
                frozenFrame = new TextureTarget(target.width, target.height, false, Minecraft.ON_OSX);
            }

            copyColor(target, frozenFrame);
        } catch (Exception e) {
            ModLogger.error("Failed to copy frozen world frame", e);
            clear();
        } finally {
            target.bindWrite(false);
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
        if (!PanoramaCapture.hasPendingCapture()) {
            clear();
        }
    }

    public static boolean shouldSkipMultiplayerProgressFrame(Minecraft client) {
        Screen screen = client.screen;
        return screen != null && screen.getClass() == ProgressScreen.class && !client.isLocalServer()
            && PanoramaCapture.hasPendingCapture() && showsFrozenWorld(screen);
    }

    public static boolean showsFrozenWorld(Screen screen) {
        if (frozenFrame == null || screen == null) {
            return false;
        }
        if (exiting) {
            return screen == disconnectScreen;
        }
        return PanoramaCapture.hasPendingCapture() && (screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen);
    }

    static CameraView getCapturedView(Minecraft client) {
        return capturedLevel == client.level ? capturedView : null;
    }

    private static boolean blit(RenderTarget target) {
        try {
            copyColor(frozenFrame, target);
            return true;
        } catch (Exception e) {
            ModLogger.error("Failed to draw frozen world frame", e);
            clear();
            return false;
        } finally {
            target.bindWrite(false);
        }
    }

    private static void copyColor(RenderTarget source, RenderTarget destination) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, destination.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, source.width, source.height,
            0, 0, destination.width, destination.height, GL30.GL_COLOR_BUFFER_BIT, GL30.GL_NEAREST);
    }

    public static void clear() {
        capturedLevel = null;
        capturedView = null;
        closeTexture();
    }

    private static void closeTexture() {
        if (frozenFrame != null) {
            // destroying the frozen target unbinds the current framebuffer
            int readFramebuffer = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int drawFramebuffer = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int frozenFramebuffer = frozenFrame.frameBufferId;
            frozenFrame.destroyBuffers();
            frozenFrame = null;
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer == frozenFramebuffer ? 0 : readFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer == frozenFramebuffer ? 0 : drawFramebuffer);
        }
    }
}
