package xyz.ersi.immersivepanoramas.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.ersi.immersivepanoramas.client.LegacyMenuBackground;

@Mixin(AbstractSelectionList.class)
public abstract class LegacySelectionListMixin {
    @Shadow protected abstract int getScrollbarPosition();
    @Shadow protected abstract int getMaxPosition();
    @Shadow public abstract int getMaxScroll();
    @Shadow public abstract double getScrollAmount();

    @ModifyExpressionValue(method = "renderWidget", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/gui/components/AbstractSelectionList;renderBackground:Z"))
    private boolean useTransparentBackground(boolean original) {
        return original && LegacyMenuBackground.usesVanillaBackground(Minecraft.getInstance().screen);
    }

    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void renderListBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!LegacyMenuBackground.usesVanillaBackground(Minecraft.getInstance().screen)) {
            AbstractSelectionList<?> list = (AbstractSelectionList<?>) (Object) this;
            renderPanel(graphics, list.getX(), list.getY(), list.getRight(), list.getBottom());
        }
    }

    private void renderPanel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        LegacyMenuBackground.renderTexture(graphics, "menu_list_background", left, top, right - left, bottom - top,
            right, bottom + (int) getScrollAmount(), 32, 32);
    }

    @WrapWithCondition(method = "renderWidget", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
    private boolean useVanillaScrollbar(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        return LegacyMenuBackground.usesVanillaBackground(Minecraft.getInstance().screen);
    }

    @Inject(method = "renderWidget", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/components/AbstractSelectionList;renderDecorations(Lnet/minecraft/client/gui/GuiGraphics;II)V"))
    private void renderListDecorations(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!LegacyMenuBackground.usesVanillaBackground(Minecraft.getInstance().screen)) {
            AbstractSelectionList<?> list = (AbstractSelectionList<?>) (Object) this;
            renderDecorations(graphics, list.getX(), list.getY(), list.getRight(), list.getBottom());
        }
    }

    private void renderDecorations(GuiGraphics graphics, int left, int top, int right, int bottom) {
        LegacyMenuBackground.renderTexture(graphics, "header_separator", left, top - 2, right - left, 2, 0, 0, 32, 2);
        LegacyMenuBackground.renderTexture(graphics, "footer_separator", left, bottom, right - left, 2, 0, 0, 32, 2);
        if (getMaxScroll() > 0) {
            int height = bottom - top;
            int thumbHeight = Mth.clamp(height * height / getMaxPosition(), 32, height - 8);
            int thumbY = Math.max(top, top + (int) getScrollAmount() * (height - thumbHeight) / getMaxScroll());
            RenderSystem.enableBlend();
            renderScrollbar(graphics, "scroller_background", getScrollbarPosition(), top, height);
            renderScrollbar(graphics, "scroller", getScrollbarPosition(), thumbY, thumbHeight);
            RenderSystem.disableBlend();
        }
    }

    private void renderScrollbar(GuiGraphics graphics, String name, int x, int y, int height) {
        ResourceLocation texture = new ResourceLocation("immersivepanoramas", "textures/gui/sprites/widget/" + name + ".png");
        graphics.blit(texture, x, y, 0, 0, 6, 1, 6, 32);
        graphics.blit(texture, x, y + 1, 6, height - 2, 0, 1, 6, 30, 6, 32);
        graphics.blit(texture, x, y + height - 1, 0, 31, 6, 1, 6, 32);
    }
}
