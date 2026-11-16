package xyz.ersi.immersivepanoramas.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.ersi.immersivepanoramas.client.IrisCompat;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.IrisExclusiveUniforms", remap = false)
public class IrisExclusiveUniformsMixin {
    @Inject(method = "addIrisExclusiveUniforms", at = @At("TAIL"), require = 0)
    private static void addPanoramaCaptureUniform(@Coerce Object uniforms, CallbackInfo ci) {
        IrisCompat.registerCaptureUniform(uniforms);
    }
}
