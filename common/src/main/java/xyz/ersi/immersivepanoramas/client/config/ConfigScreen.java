package xyz.ersi.immersivepanoramas.client.config;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import xyz.ersi.immersivepanoramas.client.ModLogger;

import java.util.List;

public class ConfigScreen extends Screen {
    private static final int CONTENT_WIDTH = 310;
    private static final int TOTAL_HEIGHT = 280;
    private static final int MAX_LIST_HEIGHT = 112;
    private static final int LIST_ROW_HEIGHT = 28;

    private final Screen parent;
    private final ModConfig config;
    private FilterSelectionList list;
    private CycleButton<ModConfig.FilterMode> modeButton;

    private int startY;
    private int titleY;
    private int hintY;

    public ConfigScreen(Screen parent) {
        super(Component.literal("Immersive Panoramas Settings"));
        this.parent = parent;
        this.config = ModConfig.getInstance();
    }

    @Override
    protected void init() {
        int halfWidth = 150;
        int gap = 10;
        int leftX = (this.width - CONTENT_WIDTH) / 2;
        int rightX = leftX + halfWidth + gap;

        this.startY = Math.max(12, (this.height - TOTAL_HEIGHT) / 2);
        this.titleY = this.startY;
        int modY = this.titleY + 18;
        int resolutionY = modY + 24;
        int captureTogglesY = resolutionY + 24;
        int modeY = captureTogglesY + 24;
        this.hintY = modeY + 24;
        int listY = this.hintY + 14;
        int listHeight = Mth.clamp(this.height - listY - 44, LIST_ROW_HEIGHT, MAX_LIST_HEIGHT);
        int doneY = listY + listHeight + 12;

        CycleButton<Boolean> modBtn = CycleButton.booleanBuilder(
                Component.translatable("options.on").withStyle(ChatFormatting.GREEN),
                Component.translatable("options.off").withStyle(ChatFormatting.RED)
            )
            .withInitialValue(this.config.enabled)
            .create(leftX, modY, halfWidth, 20, Component.literal("Mod"), (btn, val) -> {
                this.config.enabled = val;
                this.config.save();
            });
        modBtn.setTooltip(Tooltip.create(Component.literal("Master toggle for Immersive Panoramas.\nWhen OFF, default vanilla panoramas are used.")));
        this.addRenderableWidget(modBtn);

        CycleButton<Boolean> alignBtn = CycleButton.booleanBuilder(
                Component.translatable("options.on").withStyle(ChatFormatting.GREEN),
                Component.translatable("options.off").withStyle(ChatFormatting.RED)
            )
            .withInitialValue(this.config.alignPlayerRotation)
            .create(rightX, modY, halfWidth, 20, Component.literal("Align View"), (btn, val) -> {
                this.config.alignPlayerRotation = val;
                this.config.save();
            });
        alignBtn.setTooltip(Tooltip.create(Component.literal("Aligns player yaw and pitch when joining a world\nto match the captured panorama's view angle.")));
        this.addRenderableWidget(alignBtn);

        CycleButton<ModConfig.CaptureResolution> resolutionBtn = CycleButton.<ModConfig.CaptureResolution>builder(
                resolution -> Component.literal(resolution == ModConfig.CaptureResolution.AUTO
                    ? "Auto" : resolution.getFaceSize() + " px")
            )
            .withInitialValue(this.config.captureResolution)
            .withValues(ModConfig.CaptureResolution.values())
            .withTooltip(resolution -> Tooltip.create(Component.literal(switch (resolution) {
                case AUTO -> "Picks 1024–1536 px per face based on screen height and field of view.\n"
                    + "Uses 24–54 MiB for GPU readback. Larger captures take longer, especially with shaders.";
                case STANDARD -> "1024 × 1024 per face. Lowest GPU cost and 24 MiB for readback.";
                case HIGH -> "1536 × 1536 per face. Renders 2.25× as many pixels as 1024 px and uses 54 MiB for GPU readback.";
                case ULTRA -> "2048 × 2048 per face. Renders 4× as many pixels as 1024 px and uses 96 MiB for GPU readback.\n"
                    + "Can noticeably slow exits with shaders.";
            })))
            .create(leftX, resolutionY, CONTENT_WIDTH, 20, Component.literal("Resolution"), (btn, val) -> {
                this.config.captureResolution = val;
                this.config.save();
            });
        this.addRenderableWidget(resolutionBtn);

        CycleButton<Boolean> spBtn = CycleButton.booleanBuilder(
                Component.translatable("options.on").withStyle(ChatFormatting.GREEN),
                Component.translatable("options.off").withStyle(ChatFormatting.RED)
            )
            .withInitialValue(this.config.enableSingleplayer)
            .create(leftX, captureTogglesY, halfWidth, 20, Component.literal("Singleplayer"), (btn, val) -> {
                this.config.enableSingleplayer = val;
                this.config.save();
            });
        spBtn.setTooltip(Tooltip.create(Component.literal("Capture custom panoramas when leaving singleplayer worlds.")));
        this.addRenderableWidget(spBtn);

        CycleButton<Boolean> mpBtn = CycleButton.booleanBuilder(
                Component.translatable("options.on").withStyle(ChatFormatting.GREEN),
                Component.translatable("options.off").withStyle(ChatFormatting.RED)
            )
            .withInitialValue(this.config.enableMultiplayer)
            .create(rightX, captureTogglesY, halfWidth, 20, Component.literal("Multiplayer"), (btn, val) -> {
                this.config.enableMultiplayer = val;
                this.config.save();
            });
        mpBtn.setTooltip(Tooltip.create(Component.literal("Capture custom panoramas when disconnecting from multiplayer servers.")));
        this.addRenderableWidget(mpBtn);

        this.modeButton = CycleButton.<ModConfig.FilterMode>builder(
                mode -> switch (mode) {
                    case ALLOW_LIST -> Component.literal(mode.getDisplayName()).withStyle(ChatFormatting.GREEN);
                    case BLOCK_LIST -> Component.literal(mode.getDisplayName()).withStyle(ChatFormatting.RED);
                }
            )
            .withInitialValue(this.config.filterMode)
            .withValues(ModConfig.FilterMode.values())
            .withTooltip(mode -> Tooltip.create(switch (mode) {
                case BLOCK_LIST -> Component.literal("Block List:\nChecked worlds and servers will be excluded from panorama capture.");
                case ALLOW_LIST -> Component.literal("Allow List:\nONLY checked worlds and servers will have panoramas captured.");
            }))
            .create(leftX, modeY, CONTENT_WIDTH, 20, Component.literal("Mode"), (btn, val) -> {
                this.config.filterMode = val;
                this.config.save();
            });
        this.addRenderableWidget(this.modeButton);

        this.list = new FilterSelectionList(this.minecraft, CONTENT_WIDTH, listHeight, listY, LIST_ROW_HEIGHT);
        this.list.setX(leftX);
        this.addRenderableWidget(this.list);
        this.populateEntries();

        Button doneBtn = Button.builder(CommonComponents.GUI_DONE, btn -> this.onClose())
            .bounds((this.width - 150) / 2, doneY, 150, 20)
            .tooltip(Tooltip.create(Component.literal("Save and return to the previous menu.")))
            .build();
        this.addRenderableWidget(doneBtn);
    }

    private void populateEntries() {
        this.list.clear();
        if (this.minecraft == null) return;

        try {
            LevelStorageSource source = this.minecraft.getLevelSource();
            LevelStorageSource.LevelCandidates candidates = source.findLevelCandidates();
            List<LevelSummary> summaries = null;
            try {
                summaries = source.loadLevelSummaries(candidates).join();
            } catch (Exception ignored) {
            }

            if (summaries != null && !summaries.isEmpty()) {
                for (LevelSummary summary : summaries) {
                    this.list.add(new FilterEntry(
                        TargetType.SINGLEPLAYER,
                        summary.getLevelName(),
                        "Folder: " + summary.getLevelId(),
                        summary.getLevelId()
                    ));
                }
            } else {
                for (LevelStorageSource.LevelDirectory dir : candidates.levels()) {
                    String folderName = dir.directoryName();
                    this.list.add(new FilterEntry(
                        TargetType.SINGLEPLAYER,
                        folderName,
                        "Folder: " + folderName,
                        folderName
                    ));
                }
            }
        } catch (Exception e) {
            ModLogger.error("Failed to load singleplayer worlds", e);
        }

        try {
            ServerList serverList = new ServerList(this.minecraft);
            serverList.load();
            for (int i = 0; i < serverList.size(); i++) {
                ServerData server = serverList.get(i);
                this.list.add(new FilterEntry(
                    TargetType.MULTIPLAYER,
                    server.name,
                    "IP: " + server.ip,
                    server.ip
                ));
            }
        } catch (Exception e) {
            ModLogger.error("Failed to load multiplayer servers", e);
        }
    }

    @Override
    public void onClose() {
        this.config.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        this.renderBackground(graphics, mouseX, mouseY, a);
        super.render(graphics, mouseX, mouseY, a);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.titleY, 0xFFFFFFFF);

        int leftX = (this.width - CONTENT_WIDTH) / 2;

        String hint = this.config.filterMode == ModConfig.FilterMode.BLOCK_LIST
            ? "Checked worlds and servers will be excluded:"
            : "ONLY checked worlds and servers will have panoramas:";
        graphics.drawCenteredString(this.font, Component.literal(hint), this.width / 2, this.hintY, 0xFFAAAAAA);

        if (this.list != null) {
            int listY = this.list.getY();
            int listHeight = this.list.getHeight();
            RenderSystem.enableBlend();
            graphics.fill(leftX - 1, listY - 1, leftX + CONTENT_WIDTH + 1, listY + listHeight + 1, 0x33FFFFFF);
            if (this.list.children().isEmpty()) {
                graphics.drawCenteredString(this.font, Component.literal("No worlds or servers found."), this.width / 2, listY + listHeight / 2 - 4, 0xFFAAAAAA);
            }
        }
    }

    public enum TargetType {
        SINGLEPLAYER("[World]", 0xFF55FF55),
        MULTIPLAYER("[Server]", 0xFF55FFFF);

        final String tag;
        final int color;

        TargetType(String tag, int color) {
            this.tag = tag;
            this.color = color;
        }
    }

    public class FilterSelectionList extends ContainerObjectSelectionList<FilterEntry> {
        public FilterSelectionList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return 290;
        }

        public void clear() {
            this.clearEntries();
        }

        public void add(FilterEntry entry) {
            this.addEntry(entry);
        }
    }

    public class FilterEntry extends ContainerObjectSelectionList.Entry<FilterEntry> {
        private final TargetType type;
        private final String title;
        private final String detail;
        private final String identifier;
        private final Checkbox checkbox;

        public FilterEntry(TargetType type, String title, String detail, String identifier) {
            this.type = type;
            this.title = title;
            this.detail = detail;
            this.identifier = identifier;

            boolean isIncluded = config.filteredWorlds.contains(identifier);

            Font font = ConfigScreen.this.font;
            int boxSize = Checkbox.getBoxSize(font);

            this.checkbox = Checkbox.builder(CommonComponents.EMPTY, font)
                .selected(isIncluded)
                .onValueChange((cb, selected) -> {
                    if (selected) {
                        config.filteredWorlds.add(identifier);
                    } else {
                        config.filteredWorlds.remove(identifier);
                    }
                    config.save();
                })
                .build();
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(this.checkbox);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(this.checkbox);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.checkbox.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (this.isMouseOver(mouseX, mouseY)) {
                this.checkbox.playDownSound(Minecraft.getInstance().getSoundManager());
                this.checkbox.onPress();
                return true;
            }
            return false;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
                           int mouseX, int mouseY, boolean isHovered, float tickProgress) {
            Font font = ConfigScreen.this.font;

            RenderSystem.enableBlend();
            graphics.fill(x, y, x + width, y + height, isHovered ? 0x22FFFFFF : 0x0EFFFFFF);

            int textLeft = x + 4;
            graphics.drawString(font, this.type.tag, textLeft, y + 4, this.type.color);
            int tagWidth = font.width(this.type.tag);

            int maxTextWidth = width - 88;
            String truncatedTitle = font.plainSubstrByWidth(this.title, Math.max(20, maxTextWidth - tagWidth - 4));
            graphics.drawString(font, truncatedTitle, textLeft + tagWidth + 4, y + 4, 0xFFFFFFFF);

            String truncatedDetail = font.plainSubstrByWidth(this.detail, Math.max(20, maxTextWidth));
            graphics.drawString(font, truncatedDetail, textLeft, y + 16, 0xFFAAAAAA);

            int boxSize = Checkbox.getBoxSize(font);
            int cbX = x + width - boxSize - 4;
            int cbY = y + (height - boxSize) / 2;
            this.checkbox.setWidth(boxSize);
            this.checkbox.setX(cbX);
            this.checkbox.setY(cbY);
            this.checkbox.render(graphics, mouseX, mouseY, tickProgress);

            if (this.checkbox.selected()) {
                boolean isBlock = config.filterMode == ModConfig.FilterMode.BLOCK_LIST;
                Component status = isBlock
                    ? Component.literal("Blocked").withStyle(ChatFormatting.RED)
                    : Component.literal("Allowed").withStyle(ChatFormatting.GREEN);
                int statusColor = isBlock ? 0xFFFF5555 : 0xFF55FF55;
                int statusWidth = font.width(status);
                graphics.drawString(font, status, cbX - statusWidth - 4, y + (height - 9) / 2, statusColor);
            }
        }
    }
}
