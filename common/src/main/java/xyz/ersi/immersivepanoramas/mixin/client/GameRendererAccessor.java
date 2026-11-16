package xyz.ersi.immersivepanoramas.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("renderBlockOutline")
    boolean isRenderBlockOutline();

    @Invoker("getFov")
    double captureFov(Camera camera, float partialTick, boolean useConfiguredFov);
}
