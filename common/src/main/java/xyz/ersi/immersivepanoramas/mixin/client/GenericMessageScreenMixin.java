package xyz.ersi.immersivepanoramas.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xyz.ersi.immersivepanoramas.client.FrozenWorldRenderer;
import xyz.ersi.immersivepanoramas.client.config.ModConfig;

@Mixin(GenericMessageScreen.class)
public class GenericMessageScreenMixin {

    @WrapOperation(
        method = "extractBackground",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/GenericMessageScreen;extractPanorama(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V")
    )
    private void wrapExtractPanorama(GenericMessageScreen screen, GuiGraphicsExtractor graphics, float a, Operation<Void> original) {
        ModConfig config = ModConfig.getInstance();
        if (config.enabled && config.enableSingleplayer && FrozenWorldRenderer.isAllowedForSavingScreen()) {
            FrozenWorldRenderer.render(graphics, screen.width, screen.height);
            graphics.nextStratum();
        } else {
            original.call(screen, graphics, a);
        }
    }
}
