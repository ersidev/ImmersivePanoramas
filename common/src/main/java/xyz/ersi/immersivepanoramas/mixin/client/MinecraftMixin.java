package xyz.ersi.immersivepanoramas.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.ersi.immersivepanoramas.client.CustomPanoramaManager;
import xyz.ersi.immersivepanoramas.client.DisconnectWatchdog;
import xyz.ersi.immersivepanoramas.client.FrozenWorldRenderer;
import xyz.ersi.immersivepanoramas.client.ModLogger;
import xyz.ersi.immersivepanoramas.client.PanoramaCapture;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(GameConfig gameConfig, CallbackInfo ci) {
        CustomPanoramaManager.init((Minecraft) (Object) this);
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void onRunTick(boolean advanceGameTime, CallbackInfo ci) {
        if (advanceGameTime) {
            PanoramaCapture.processPendingCapture((Minecraft) (Object) this);
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        PanoramaCapture.close();
        FrozenWorldRenderer.endExit();
        CustomPanoramaManager.close();
    }

    @WrapMethod(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V")
    private void duringDisconnect(Screen screen, boolean transferring, Operation<Void> original) {
        Minecraft client = (Minecraft) (Object) this;
        boolean startedExit = FrozenWorldRenderer.beginExit(client, screen);
        try (DisconnectWatchdog watchdog = startedExit ? DisconnectWatchdog.start(client) : null) {
            if (startedExit) {
                try {
                    PanoramaCapture.captureOnDisconnect(client);
                } catch (RuntimeException e) {
                    ModLogger.error("Could not start panorama capture", e);
                }
                if (watchdog != null) {
                    watchdog.finishingDisconnect();
                }
            }
            original.call(screen, transferring);
        } finally {
            if (startedExit) {
                FrozenWorldRenderer.endExit();
            }
        }
    }

    @WrapOperation(
        method = "updateScreenAndTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTick(Z)V")
    )
    private void skipMultiplayerProgressFrame(Minecraft client, boolean advanceGameTime, Operation<Void> original) {
        // keep the pause menu visible until the multiplayer menu is ready
        if (!FrozenWorldRenderer.shouldSkipMultiplayerProgressFrame(client)) {
            original.call(client, advanceGameTime);
        }
    }
}
