package xyz.ersi.immersivepanoramas.mixin.client;

import net.minecraft.client.Camera;
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

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void useFrozenWorldPosition(float partialTicks, CallbackInfo ci) {
        Vec3 position = PanoramaCapture.getCaptureCameraPosition((Camera) (Object) this);
        if (position != null) {
            this.setPosition(position);
        }
    }
}
