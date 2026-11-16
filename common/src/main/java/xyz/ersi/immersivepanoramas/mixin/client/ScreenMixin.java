package xyz.ersi.immersivepanoramas.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.ersi.immersivepanoramas.client.FrozenWorldRenderer;

@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(method = "renderBackground", at = @At("HEAD"))
    private void useFrozenBackgroundBeforeLevelClears(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        // vanilla skips panorama rendering until the level is cleared
        if (Minecraft.getInstance().level != null) {
            FrozenWorldRenderer.renderBackground((Screen) (Object) this);
        }
    }

    @Inject(method = "renderPanorama", at = @At("HEAD"), cancellable = true)
    private void useFrozenBackground(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
        if (FrozenWorldRenderer.renderBackground((Screen) (Object) this)) {
            ci.cancel();
        }
    }
}
