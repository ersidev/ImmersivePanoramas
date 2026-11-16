package xyz.ersi.immersivepanoramas.mixin.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.PanoramaRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Screen.class)
public interface ScreenAccessor {
    @Accessor("PANORAMA")
    static PanoramaRenderer getPanorama() {
        throw new AssertionError();
    }
}
