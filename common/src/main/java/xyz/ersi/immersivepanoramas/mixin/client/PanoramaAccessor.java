package xyz.ersi.immersivepanoramas.mixin.client;

import net.minecraft.client.renderer.PanoramaRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PanoramaRenderer.class)
public interface PanoramaAccessor {
    @Accessor("spin")
    float getSpin();

    @Accessor("spin")
    void setSpin(float spin);
}
