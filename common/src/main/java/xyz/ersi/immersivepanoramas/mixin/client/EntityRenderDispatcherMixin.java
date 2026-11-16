package xyz.ersi.immersivepanoramas.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.ersi.immersivepanoramas.client.PanoramaCapture;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void hideLocalPlayerShadowDuringCapture(Entity entity, float partialTicks, CallbackInfoReturnable<EntityRenderState> cir) {
        if (PanoramaCapture.isCapturing() && entity == Minecraft.getInstance().player) {
            cir.getReturnValue().shadowPieces.clear();
        }
    }
}
