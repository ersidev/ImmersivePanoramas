package xyz.ersi.immersivepanoramas.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.ersi.immersivepanoramas.client.CustomPanoramaManager;
import xyz.ersi.immersivepanoramas.client.config.ModConfig;

@Mixin(GuiRenderer.class)
public class GuiRendererMixin {

    @WrapOperation(
        method = "render()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CubeMap;render(FF)V")
    )
    private void wrapPanoramaRender(CubeMap cubeMap, float rotX, float rotY, Operation<Void> original) {
        if (ModConfig.getInstance().enabled && CustomPanoramaManager.hasCustomPanorama()) {
            CustomPanoramaManager.render(CustomPanoramaManager.getSavedPitch(), rotY);
        } else {
            original.call(cubeMap, rotX, rotY);
        }
    }

    @Inject(method = "registerPanoramaTextures", at = @At("RETURN"))
    private void onRegisterPanoramaTextures(TextureManager textureManager, CallbackInfo ci) {
        CustomPanoramaManager.registerTextures(textureManager);
    }

    @Inject(method = "close", at = @At("RETURN"))
    private void onClose(CallbackInfo ci) {
        CustomPanoramaManager.close();
    }
}
