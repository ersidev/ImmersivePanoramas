package xyz.ersi.immersivepanoramas.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.ersi.immersivepanoramas.client.LegacyMenuBackground;

@Mixin(GameRenderer.class)
public class LegacyMenuBlurMixin {
    @Inject(method = {"reloadShaders", "close"}, at = @At("HEAD"))
    private void closeMenuBlur(CallbackInfo ci) {
        LegacyMenuBackground.closeBlur();
    }
}
