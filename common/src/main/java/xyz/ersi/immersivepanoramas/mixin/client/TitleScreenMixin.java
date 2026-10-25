package xyz.ersi.immersivepanoramas.mixin.client;

import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.ersi.immersivepanoramas.client.FrozenWorldRenderer;

@Mixin({TitleScreen.class, JoinMultiplayerScreen.class, DisconnectedScreen.class, SelectWorldScreen.class})
public class TitleScreenMixin {

    @Inject(method = "init", at = @At("HEAD"))
    private void onInit(CallbackInfo ci) {
        FrozenWorldRenderer.clear();
    }
}
