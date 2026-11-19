package xyz.ersi.immersivepanoramas.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CreateWorldScreen.class)
public class LegacyCreateWorldScreenMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIFFIIII)V"))
    private void renderFooterSeparator(GuiGraphics graphics, ResourceLocation texture, int x, int y, float u, float v,
                                       int width, int height, int textureWidth, int textureHeight, Operation<Void> original) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        original.call(graphics, texture, x, y, u, v, width, height, textureWidth, textureHeight);
        RenderSystem.disableBlend();
    }
}
