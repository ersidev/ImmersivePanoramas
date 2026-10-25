package xyz.ersi.immersivepanoramas.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.ersi.immersivepanoramas.client.CustomPanoramaManager;
import xyz.ersi.immersivepanoramas.client.FrozenWorldRenderer;
import xyz.ersi.immersivepanoramas.client.config.ModConfig;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Unique
    private boolean immersivepanoramas$initialPositionReceived = false;

    @Inject(
        method = "handleMovePlayer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundPlayerPositionPacket;id()I")
    )
    private void onHandleMovePlayerPosition(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        if (!this.immersivepanoramas$initialPositionReceived) {
            this.immersivepanoramas$initialPositionReceived = true;
            FrozenWorldRenderer.clear();
            if (ModConfig.getInstance().isAllowed(Minecraft.getInstance())) {
                CustomPanoramaManager.applyPanoramaRotation(Minecraft.getInstance());
            }
        }
    }
}
