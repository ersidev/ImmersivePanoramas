package xyz.ersi.immersivepanoramas.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.ersi.immersivepanoramas.client.PanoramaCapture;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    // apply the capture view after camera mods finish alignment and before vanilla builds the frustum
    @Inject(method = "update", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V",
        shift = At.Shift.AFTER
    ))
    private void applyCaptureView(DeltaTracker deltaTracker, CallbackInfo ci) {
        Camera camera = (Camera) (Object) this;
        PanoramaCapture.CameraView view = PanoramaCapture.getCaptureView(camera);
        if (view != null) {
            this.setPosition(view.position() == null ? camera.position() : view.position());
            this.setRotation(view.yaw(), view.pitch());
        }
    }

    @ModifyExpressionValue(method = "update", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/client/Camera;calculateFov(F)F"
    ))
    private float keepCaptureFov(float fov) {
        PanoramaCapture.CameraView view = PanoramaCapture.getCaptureView((Camera) (Object) this);
        return view == null ? fov : view.fov();
    }
}
