package xyz.ersi.immersivepanoramas.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import xyz.ersi.immersivepanoramas.mixin.client.GuiGraphicsExtractorInvoker;

public class FrozenWorldRenderer {
    private static GpuTexture texture;
    private static GpuTextureView textureView;
    private static GpuSampler sampler;
    private static boolean hasFrozenFrame = false;
    private static boolean allowedForSavingScreen = false;

    public static synchronized void setAllowedForSavingScreen(boolean allowed) {
        allowedForSavingScreen = allowed;
    }

    public static synchronized boolean isAllowedForSavingScreen() {
        return allowedForSavingScreen && hasFrozenFrame();
    }

    public static synchronized void capture(Minecraft client) {
        try {
            RenderTarget target = client.gameRenderer.mainRenderTarget();
            GpuTexture source = target.getColorTexture();
            if (source == null) {
                return;
            }

            int width = source.getWidth(0);
            int height = source.getHeight(0);
            GpuDevice device = RenderSystem.getDevice();

            if (texture == null || texture.getWidth(0) != width || texture.getHeight(0) != height) {
                clear();
                int usage = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST;
                texture = device.createTexture(() -> "immersivepanoramas_frozen_world", usage, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
                textureView = device.createTextureView(texture);
                sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
            }

            CommandEncoder encoder = device.createCommandEncoder();
            encoder.copyTextureToTexture(source, texture, 0, 0, 0, 0, 0, width, height);
            encoder.submit();
            hasFrozenFrame = true;
            allowedForSavingScreen = false;
        } catch (Exception e) {
            ModLogger.error("Failed to capture frozen world frame", e);
            clear();
        }
    }

    public static synchronized boolean hasFrozenFrame() {
        return hasFrozenFrame && textureView != null;
    }

    public static synchronized void render(GuiGraphicsExtractor graphics, int width, int height) {
        if (hasFrozenFrame()) {
            ((GuiGraphicsExtractorInvoker) graphics).invokeInnerBlit(
                RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND,
                textureView,
                sampler,
                0, 0, width, height,
                0.0F, 1.0F, 1.0F, 0.0F,
                -1
            );
        }
    }

    public static synchronized void clear() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
        if (textureView != null) {
            textureView.close();
            textureView = null;
        }
        hasFrozenFrame = false;
        allowedForSavingScreen = false;
    }
}
