package xyz.ersi.immersivepanoramas.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.ersi.immersivepanoramas.client.FrozenWorldRenderer;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Inject(method = "renderPanorama", at = @At("HEAD"), cancellable = true)
    private void useFrozenBackground(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
        if (FrozenWorldRenderer.renderBackground((Screen) (Object) this)) {
            ci.cancel();
        }
    }
}
