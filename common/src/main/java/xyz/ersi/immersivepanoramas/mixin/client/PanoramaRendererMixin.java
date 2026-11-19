package xyz.ersi.immersivepanoramas.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.PanoramaRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.ersi.immersivepanoramas.client.CustomPanoramaManager;
import xyz.ersi.immersivepanoramas.client.config.ModConfig;

@Mixin(PanoramaRenderer.class)
public class PanoramaRendererMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void trackPanorama(CallbackInfo ci) {
        CustomPanoramaManager.trackPanorama((PanoramaRenderer) (Object) this);
    }

    @WrapOperation(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CubeMap;render(Lnet/minecraft/client/Minecraft;FFF)V")
    )
    private void wrapPanoramaRender(CubeMap cubeMap, Minecraft client, float rotX, float rotY, float alpha, Operation<Void> original) {
        if (ModConfig.getInstance().enabled && CustomPanoramaManager.hasCustomPanorama()) {
            CustomPanoramaManager.render(client, CustomPanoramaManager.getSavedPitch(), rotY, alpha);
        } else {
            original.call(cubeMap, client, rotX, rotY, alpha);
        }
    }
}
