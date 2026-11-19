package xyz.ersi.immersivepanoramas.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;

public final class LegacyMenuBackground {
    private static final Field SPATIAL_SCREEN_EXTRACTION = findSpatialScreenExtraction();

    private static PanoramaRenderer fallbackPanorama;
    private static PostChain blur;
    private static boolean blurFailed;
    private static int blurWidth;
    private static int blurHeight;

    private LegacyMenuBackground() {}

    public static boolean usesVanillaBackground(Screen screen) {
        return screen != null && (screen.getClass().getName().startsWith("com.terraformersmc.modmenu.")
            || isSpatialScreenExtraction());
    }

    private static Field findSpatialScreenExtraction() {
        try {
            Class<?> renderer = Class.forName("org.tastytrash.spatialGUI.render.SpatialGUIRenderer", false,
                LegacyMenuBackground.class.getClassLoader());
            return renderer.getField("isExtractingScreen");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean isSpatialScreenExtraction() {
        try {
            // let spatial gui finish drawing into its own framebuffer
            return SPATIAL_SCREEN_EXTRACTION != null && SPATIAL_SCREEN_EXTRACTION.getBoolean(null);
        } catch (IllegalAccessException ignored) {
            return false;
        }
    }

    public static void render(Screen screen, GuiGraphics graphics, float partialTick) {
        graphics.flush();
        Minecraft client = Minecraft.getInstance();
        if (!FrozenWorldRenderer.renderBackground(screen)
            && (client.level == null || FrozenWorldRenderer.isSavingScreen(screen))) {
            PanoramaRenderer panorama = CustomPanoramaManager.getActivePanorama();
            if (panorama == null) {
                if (fallbackPanorama == null) {
                    fallbackPanorama = new PanoramaRenderer(new CubeMap(new ResourceLocation("textures/gui/title/background/panorama")));
                }
                panorama = fallbackPanorama;
            }
            panorama.render(client.getDeltaFrameTime(), 1.0F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.blit(new ResourceLocation("textures/gui/title/background/panorama_overlay.png"),
                0, 0, screen.width, screen.height, 0, 0, 16, 128, 16, 128);
            graphics.flush();
        }

        renderBlur(client, partialTick);
        int headerHeight = screen instanceof CreateWorldScreen ? 24 : 0;
        if (headerHeight > 0) {
            graphics.blit(new ResourceLocation("immersivepanoramas", "textures/gui/tab_header_background.png"),
                0, 0, 0, 0, screen.width, headerHeight, 16, 16);
        }
        renderTexture(graphics, "menu_background", 0, headerHeight, screen.width, screen.height - headerHeight, 0, 0, 32, 32);
        graphics.flush();
    }

    public static void renderTexture(GuiGraphics graphics, String texture, int x, int y, int width, int height,
                                     float u, float v, int textureWidth, int textureHeight) {
        String prefix = Minecraft.getInstance().level == null ? "" : "inworld_";
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(new ResourceLocation("immersivepanoramas", "textures/gui/" + prefix + texture + ".png"),
            x, y, u, v, width, height, textureWidth, textureHeight);
        RenderSystem.disableBlend();
    }

    private static void renderBlur(Minecraft client, float partialTick) {
        if (blurFailed) {
            return;
        }
        var target = client.getMainRenderTarget();
        try {
            if (blur == null) {
                blur = new PostChain(client.getTextureManager(), client.getResourceManager(), target,
                    new ResourceLocation("shaders/post/immersivepanoramas_menu_blur.json"));
                blurWidth = blurHeight = 0;
            }
            if (blurWidth != target.width || blurHeight != target.height) {
                blur.resize(target.width, target.height);
                blurWidth = target.width;
                blurHeight = target.height;
            }
            target.setFilterMode(9729);
            blur.getTempTarget("swap").setFilterMode(9729);
            RenderSystem.enableBlend();
            // keep the blur quad out of the depth buffer so widgets can draw over it
            RenderSystem.disableDepthTest();
            blur.process(partialTick);
        } catch (Exception e) {
            closeBlur();
            blurFailed = true;
            ModLogger.error("Failed to render menu blur", e);
        } finally {
            target.setFilterMode(9728);
            target.bindWrite(false);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    public static void closeBlur() {
        if (blur != null) {
            blur.close();
            blur = null;
        }
        blurFailed = false;
    }
}
