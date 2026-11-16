package xyz.ersi.immersivepanoramas.mixin.client;

import net.minecraft.client.renderer.CubeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import xyz.ersi.immersivepanoramas.client.CustomPanoramaManager;

@Mixin(CubeMap.class)
public class CubeMapMixin {
    @ModifyArg(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Projection;setupPerspective(FFFFF)V"),
        index = 2
    )
    private float matchFrozenWorldFov(float fov) {
        return CustomPanoramaManager.getPanoramaFov((CubeMap) (Object) this, fov);
    }
}
