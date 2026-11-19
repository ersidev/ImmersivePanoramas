package xyz.ersi.immersivepanoramas.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.ersi.immersivepanoramas.client.LegacyMenuBackground;

@Mixin(TabButton.class)
public class LegacyTabButtonMixin {
    @Inject(method = "renderWidget", at = @At("HEAD"))
    private void enableTabTransparency(CallbackInfo ci) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    @ModifyArg(method = "renderWidget", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"), index = 0)
    private ResourceLocation useBackportedTabTexture(ResourceLocation texture) {
        return new ResourceLocation("immersivepanoramas", texture.getPath());
    }

    @Inject(method = "renderString", at = @At("HEAD"))
    private void renderSelectedBackground(GuiGraphics graphics, Font font, int color, CallbackInfo ci) {
        TabButton tab = (TabButton) (Object) this;
        if (tab.isSelected()) {
            LegacyMenuBackground.renderTexture(graphics, "menu_background", tab.getX() + 2, tab.getY() + 2,
                tab.getWidth() - 4, tab.getHeight() - 2, 0, 0, 32, 32);
        }
    }

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void disableTabTransparency(CallbackInfo ci) {
        RenderSystem.disableBlend();
    }
}
