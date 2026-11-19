package xyz.ersi.immersivepanoramas.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.ersi.immersivepanoramas.client.LegacyMenuBackground;

@Mixin(SelectWorldScreen.class)
public class LegacyWorldSelectionScreenMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void renderMenuBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        LegacyMenuBackground.render((Screen) (Object) this, graphics, partialTick);
    }
}
