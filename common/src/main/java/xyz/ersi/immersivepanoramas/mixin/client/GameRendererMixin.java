package xyz.ersi.immersivepanoramas.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.ersi.immersivepanoramas.client.FrozenWorldRenderer;
import xyz.ersi.immersivepanoramas.client.PanoramaCapture;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "render", at = @At(value = "INVOKE", ordinal = 0, target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"))
    private void onBeforeGuiRender(DeltaTracker deltaTracker, boolean renderedWorld, CallbackInfo ci) {
        FrozenWorldRenderer.beforeGui(this.minecraft, renderedWorld && this.minecraft.isGameLoadFinished(), deltaTracker.getGameTimeDeltaPartialTick(true));
    }

    // apply the capture view after camera mods finish alignment and before vanilla builds the frustum
    @Inject(method = "renderLevel", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
        shift = At.Shift.AFTER
    ))
    private void applyCaptureView(DeltaTracker deltaTracker, CallbackInfo ci) {
        Camera camera = this.minecraft.gameRenderer.getMainCamera();
        PanoramaCapture.CameraView view = PanoramaCapture.getCaptureView(camera);
        if (view != null) {
            CameraAccessor accessor = (CameraAccessor) camera;
            accessor.invokeSetPosition(view.position() == null ? camera.getPosition() : view.position());
            accessor.invokeSetRotation(view.yaw(), view.pitch());
        }
    }

    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    private void keepCaptureFov(Camera camera, float partialTick, boolean useConfiguredFov, CallbackInfoReturnable<Double> cir) {
        PanoramaCapture.CameraView view = PanoramaCapture.getCaptureView(camera);
        if (view != null && useConfiguredFov) {
            cir.setReturnValue((double) view.fov());
        }
    }
}
