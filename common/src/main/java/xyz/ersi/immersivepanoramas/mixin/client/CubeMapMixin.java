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
        at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;setPerspective(FFFF)Lorg/joml/Matrix4f;", remap = false),
        index = 0
    )
    private float matchFrozenWorldFov(float fov) {
        return (float) Math.toRadians(CustomPanoramaManager.getPanoramaFov((CubeMap) (Object) this, (float) Math.toDegrees(fov)));
    }
}
