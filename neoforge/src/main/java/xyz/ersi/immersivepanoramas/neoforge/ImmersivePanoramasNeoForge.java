package xyz.ersi.immersivepanoramas.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import xyz.ersi.immersivepanoramas.client.config.ConfigScreen;

@Mod(value = "immersivepanoramas", dist = Dist.CLIENT)
public class ImmersivePanoramasNeoForge {
    public ImmersivePanoramasNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(
            IConfigScreenFactory.class,
            (container, screen) -> new ConfigScreen(screen)
        );
    }
}
