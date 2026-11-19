package xyz.ersi.immersivepanoramas.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import xyz.ersi.immersivepanoramas.client.config.ConfigScreen;

@Mod("immersivepanoramas")
public class ImmersivePanoramasForge {
    public ImmersivePanoramasForge() {
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> Client::registerConfigScreen);
    }

    private static final class Client {
        private static void registerConfigScreen() {
            ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((client, screen) -> new ConfigScreen(screen))
            );
        }
    }
}
