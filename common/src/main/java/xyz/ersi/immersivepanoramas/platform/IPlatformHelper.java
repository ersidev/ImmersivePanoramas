package xyz.ersi.immersivepanoramas.platform;

import java.nio.file.Path;

public interface IPlatformHelper {
    Path getConfigDir();
    boolean isDevelopmentEnvironment();
}
