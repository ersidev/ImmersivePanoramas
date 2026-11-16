package xyz.ersi.immersivepanoramas.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import xyz.ersi.immersivepanoramas.client.IrisCompat;

import java.util.Optional;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.shaderpack.programs.ProgramSource", remap = false)
public class IrisProgramSourceMixin {
    @ModifyReturnValue(method = "getFragmentSource", at = @At("RETURN"), require = 0)
    private Optional<String> skipPhotonVignetteDuringCapture(Optional<String> source) {
        return source.map(IrisCompat::patchPhotonVignette);
    }
}
