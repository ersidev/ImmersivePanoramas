package xyz.ersi.immersivepanoramas.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.ersi.immersivepanoramas.client.PanoramaCapture;

@Pseudo
@Mixin(targets = "org.tastytrash.spatialGUI.client.SpatialGUIClient", remap = false)
public class SpatialGuiClientMixin {
    // spatial gui draws its panel and deferred player inside the legacy world-render pass
    @Inject(method = "isEnabled", at = @At("HEAD"), cancellable = true, require = 0)
    private static void hideSpatialGuiDuringCapture(CallbackInfoReturnable<Boolean> cir) {
        if (PanoramaCapture.isCapturing()) {
            cir.setReturnValue(false);
        }
    }
}
