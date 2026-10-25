package xyz.ersi.immersivepanoramas.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.storage.LevelResource;
import xyz.ersi.immersivepanoramas.client.ModLogger;
import xyz.ersi.immersivepanoramas.platform.Services;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig INSTANCE;

    public boolean enabled = true;
    public boolean alignPlayerRotation = true;
    public boolean enableSingleplayer = true;
    public boolean enableMultiplayer = true;
    public FilterMode filterMode = FilterMode.BLOCK_LIST;
    public Set<String> filteredWorlds = new LinkedHashSet<>();

    public enum FilterMode {
        @SerializedName(value = "BLOCK_LIST", alternate = {"DENY_LIST", "ALLOW_ALL"})
        BLOCK_LIST("Block List"),
        @SerializedName("ALLOW_LIST")
        ALLOW_LIST("Allow List");

        private final String displayName;

        FilterMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static synchronized ModConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ModConfig();
            INSTANCE.load();
        }
        return INSTANCE;
    }

    public static Path getConfigPath() {
        return Services.PLATFORM.getConfigDir().resolve("immersivepanoramas").resolve("config.json");
    }

    public synchronized void load() {
        Path path = getConfigPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) {
                    this.enabled = loaded.enabled;
                    this.alignPlayerRotation = loaded.alignPlayerRotation;
                    this.enableSingleplayer = loaded.enableSingleplayer;
                    this.enableMultiplayer = loaded.enableMultiplayer;
                    this.filterMode = loaded.filterMode != null ? loaded.filterMode : FilterMode.BLOCK_LIST;
                    this.filteredWorlds = loaded.filteredWorlds != null ? loaded.filteredWorlds : new LinkedHashSet<>();
                    return;
                }
            } catch (Exception e) {
                ModLogger.error("Failed to load ImmersivePanoramas config", e);
            }
        }
        save();
    }

    public synchronized void save() {
        Path path = getConfigPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            ModLogger.error("Failed to save ImmersivePanoramas config", e);
        }
    }

    public boolean isAllowed(Minecraft client) {
        if (!enabled) {
            return false;
        }

        boolean isSingleplayer = client.isLocalServer() || (client.getSingleplayerServer() != null);
        boolean isMultiplayer = client.getCurrentServer() != null;

        if (isSingleplayer && !enableSingleplayer) {
            return false;
        }
        if (isMultiplayer && !enableMultiplayer) {
            return false;
        }

        if (isSingleplayer) {
            String levelName = null;
            String levelId = null;
            if (client.getSingleplayerServer() != null) {
                if (client.getSingleplayerServer().getWorldData() != null) {
                    levelName = client.getSingleplayerServer().getWorldData().getLevelName();
                }
                try {
                    Path rootPath = client.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
                    if (rootPath != null && rootPath.getFileName() != null) {
                        levelId = rootPath.getFileName().toString();
                    }
                } catch (Exception ignored) {
                }
            }

            boolean matched = isWorldMatched(levelName, levelId);
            return filterMode == FilterMode.BLOCK_LIST ? !matched : matched;
        }

        if (isMultiplayer) {
            ServerData serverData = client.getCurrentServer();
            String ip = serverData.ip;
            String name = serverData.name;

            boolean matched = isServerMatched(ip, name);
            return filterMode == FilterMode.BLOCK_LIST ? !matched : matched;
        }

        return true;
    }

    private boolean isWorldMatched(String levelName, String levelId) {
        if (levelId != null) {
            for (String entry : filteredWorlds) {
                if (levelId.equalsIgnoreCase(entry)) {
                    return true;
                }
            }
            return false;
        }
        if (levelName != null) {
            for (String entry : filteredWorlds) {
                if (levelName.equalsIgnoreCase(entry)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isServerMatched(String ip, String name) {
        if (ip != null) {
            for (String entry : filteredWorlds) {
                if (ip.equalsIgnoreCase(entry)) {
                    return true;
                }
            }
            return false;
        }
        if (name != null) {
            for (String entry : filteredWorlds) {
                if (name.equalsIgnoreCase(entry)) {
                    return true;
                }
            }
        }
        return false;
    }
}
