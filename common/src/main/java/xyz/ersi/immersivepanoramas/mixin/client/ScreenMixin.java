package xyz.ersi.immersivepanoramas.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.ersi.immersivepanoramas.client.FrozenWorldRenderer;

@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
    private void useFrozenBackground(GuiGraphicsExtractor graphics, float partialTick, CallbackInfo ci) {
        if (FrozenWorldRenderer.showsFrozenWorld((Screen) (Object) this)) {
            ci.cancel();
        }
    }
}
