package xyz.ersi.immersivepanoramas.mixin.client;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import xyz.ersi.immersivepanoramas.client.PanoramaCapture;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.shadows.ShadowRenderer", remap = false)
public class IrisShadowRendererMixin {
    @WrapWithCondition(method = "renderEntities", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;" +
            "Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"
    ), require = 0)
    private boolean renderShadowCaster(EntityRenderDispatcher dispatcher, EntityRenderState state, CameraRenderState camera,
                                       double x, double y, double z, PoseStack pose, SubmitNodeCollector collector) {
        if (!PanoramaCapture.isCapturing() || !(state instanceof AvatarRenderState avatar)) {
            return true;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        return player == null || avatar.id != player.getId();
    }
}
