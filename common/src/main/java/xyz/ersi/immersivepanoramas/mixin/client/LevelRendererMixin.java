package xyz.ersi.immersivepanoramas.mixin.client;

import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @ModifyArg(
        method = "lambda$render$0",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;clearColorAndDepthTextures(Lcom/mojang/renderpearl/api/textures/GpuTexture;Lorg/joml/Vector4fc;Lcom/mojang/renderpearl/api/textures/GpuTexture;D)V"
        ),
        index = 1
    )
    private Vector4fc makeClearColorOpaque(Vector4fc clearColor) {
        return new Vector4f(clearColor.x(), clearColor.y(), clearColor.z(), 1.0F);
    }
}
