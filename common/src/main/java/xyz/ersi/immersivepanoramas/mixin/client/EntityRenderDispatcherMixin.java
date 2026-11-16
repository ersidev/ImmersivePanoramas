package xyz.ersi.immersivepanoramas.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.ersi.immersivepanoramas.client.IrisCompat;
import xyz.ersi.immersivepanoramas.client.PanoramaCapture;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private static void hideLocalPlayerShadowDuringCapture(PoseStack pose, MultiBufferSource buffers, Entity entity,
                                                          float strength, float partialTicks, LevelReader level,
                                                          float radius, CallbackInfo ci) {
        if (PanoramaCapture.isCapturing() && entity == Minecraft.getInstance().player) {
            ci.cancel();
        }
    }

    // iris uses the legacy entity renderer directly to draw its shadow map
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void hideLocalPlayerIrisShadowDuringCapture(Entity entity, double x, double y, double z, float yaw,
                                                        float partialTicks, PoseStack pose, MultiBufferSource buffers,
                                                        int light, CallbackInfo ci) {
        if (PanoramaCapture.isCapturing() && entity == Minecraft.getInstance().player && IrisCompat.isRenderingShadowPass()) {
            ci.cancel();
        }
    }
}
