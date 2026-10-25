package xyz.ersi.immersivepanoramas.mixin.client;

import net.minecraft.client.renderer.Panorama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Panorama.class)
public interface PanoramaAccessor {
    @Accessor("spin")
    float getSpin();

    @Accessor("spin")
    void setSpin(float spin);
}
