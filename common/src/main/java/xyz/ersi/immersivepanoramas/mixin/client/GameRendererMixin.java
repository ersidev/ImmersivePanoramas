package xyz.ersi.immersivepanoramas.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.ersi.immersivepanoramas.client.FrozenWorldRenderer;
import xyz.ersi.immersivepanoramas.client.config.ModConfig;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private GameRenderState gameRenderState;

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
    private void onBeforeGuiRender(CallbackInfo ci) {
        if (this.gameRenderState.shouldRenderLevel && this.minecraft.gui.screen() instanceof PauseScreen) {
            boolean isSingleplayer = this.minecraft.isLocalServer() || (this.minecraft.getSingleplayerServer() != null);
            ModConfig config = ModConfig.getInstance();
            if (config.enabled && (!isSingleplayer || config.enableSingleplayer) && config.isAllowed(this.minecraft)) {
                FrozenWorldRenderer.capture(this.minecraft);
            } else {
                FrozenWorldRenderer.clear();
            }
        }
    }
}
