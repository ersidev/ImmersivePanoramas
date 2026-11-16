package xyz.ersi.immersivepanoramas.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.ersi.immersivepanoramas.client.CustomPanoramaManager;
import xyz.ersi.immersivepanoramas.client.config.ModConfig;

@Pseudo
@Mixin(targets = "de.miraculixx.showmyworld.client.ui.panorama.WorldPanorama", remap = false)
public class ShowMyWorldPanoramaMixin {
    @Inject(method = "render(FF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void skipSecondTitlePanorama(float pitch, float spin, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof TitleScreen
            && ModConfig.getInstance().enabled
            && CustomPanoramaManager.hasCustomPanorama()) {
            ci.cancel();
        }
    }
}
