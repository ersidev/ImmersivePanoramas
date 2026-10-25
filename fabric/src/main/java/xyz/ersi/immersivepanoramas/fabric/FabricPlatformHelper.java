package xyz.ersi.immersivepanoramas.fabric;

import net.fabricmc.loader.api.FabricLoader;
import xyz.ersi.immersivepanoramas.platform.IPlatformHelper;

import java.nio.file.Path;

public class FabricPlatformHelper implements IPlatformHelper {
    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }
}
