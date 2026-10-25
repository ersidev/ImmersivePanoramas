package xyz.ersi.immersivepanoramas.client.config;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import xyz.ersi.immersivepanoramas.client.ModLogger;

import java.util.List;

public class ConfigScreen extends Screen {
    private static final int CONTENT_WIDTH = 310;
    private static final int TOTAL_HEIGHT = 246;
    private static final int LIST_HEIGHT = 112;

    private final Screen parent;
    private final ModConfig config;
    private FilterSelectionList list;
    private CycleButton<ModConfig.FilterMode> modeButton;

    private int startY;
    private int titleY;
    private int hintY;
    private int listY;

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
        int row1Y = this.titleY + 18;
        int row2Y = row1Y + 24;
        int row3Y = row2Y + 26;
        this.hintY = row3Y + 24;
        this.listY = this.hintY + 14;
        int doneY = this.listY + LIST_HEIGHT + 12;

        CycleButton<Boolean> modBtn = CycleButton.booleanBuilder(
                Component.translatable("options.on").withStyle(ChatFormatting.GREEN),
                Component.translatable("options.off").withStyle(ChatFormatting.RED),
                this.config.enabled
            )
            .create(leftX, row1Y, halfWidth, 20, Component.literal("Mod"), (btn, val) -> {
                this.config.enabled = val;
                this.config.save();
            });
        modBtn.setTooltip(Tooltip.create(Component.literal("Master toggle for Immersive Panoramas.\nWhen OFF, default vanilla panoramas are used.")));
        this.addRenderableWidget(modBtn);

        CycleButton<Boolean> alignBtn = CycleButton.booleanBuilder(
                Component.translatable("options.on").withStyle(ChatFormatting.GREEN),
                Component.translatable("options.off").withStyle(ChatFormatting.RED),
                this.config.alignPlayerRotation
            )
            .create(rightX, row1Y, halfWidth, 20, Component.literal("Align View"), (btn, val) -> {
                this.config.alignPlayerRotation = val;
                this.config.save();
            });
        alignBtn.setTooltip(Tooltip.create(Component.literal("Aligns player yaw and pitch when joining a world\nto match the captured panorama's view angle.")));
        this.addRenderableWidget(alignBtn);

        CycleButton<Boolean> spBtn = CycleButton.booleanBuilder(
                Component.translatable("options.on").withStyle(ChatFormatting.GREEN),
                Component.translatable("options.off").withStyle(ChatFormatting.RED),
                this.config.enableSingleplayer
            )
            .create(leftX, row2Y, halfWidth, 20, Component.literal("Singleplayer"), (btn, val) -> {
                this.config.enableSingleplayer = val;
                this.config.save();
            });
        spBtn.setTooltip(Tooltip.create(Component.literal("Capture custom panoramas when leaving singleplayer worlds.")));
        this.addRenderableWidget(spBtn);

        CycleButton<Boolean> mpBtn = CycleButton.booleanBuilder(
                Component.translatable("options.on").withStyle(ChatFormatting.GREEN),
                Component.translatable("options.off").withStyle(ChatFormatting.RED),
                this.config.enableMultiplayer
            )
            .create(rightX, row2Y, halfWidth, 20, Component.literal("Multiplayer"), (btn, val) -> {
                this.config.enableMultiplayer = val;
                this.config.save();
            });
        mpBtn.setTooltip(Tooltip.create(Component.literal("Capture custom panoramas when disconnecting from multiplayer servers.")));
        this.addRenderableWidget(mpBtn);

        this.modeButton = CycleButton.<ModConfig.FilterMode>builder(
                mode -> switch (mode) {
                    case ALLOW_LIST -> Component.literal(mode.getDisplayName()).withStyle(ChatFormatting.GREEN);
                    case BLOCK_LIST -> Component.literal(mode.getDisplayName()).withStyle(ChatFormatting.RED);
                },
                () -> this.config.filterMode
            )
            .withValues(ModConfig.FilterMode.values())
            .withTooltip(mode -> Tooltip.create(switch (mode) {
                case BLOCK_LIST -> Component.literal("Block List:\nChecked worlds and servers will be excluded from panorama capture.");
                case ALLOW_LIST -> Component.literal("Allow List:\nONLY checked worlds and servers will have panoramas captured.");
            }))
            .create(leftX, row3Y, CONTENT_WIDTH, 20, Component.literal("Mode"), (btn, val) -> {
                this.config.filterMode = val;
                this.config.save();
            });
        this.addRenderableWidget(this.modeButton);

        this.list = new FilterSelectionList(this.minecraft, CONTENT_WIDTH, LIST_HEIGHT, this.listY, 28);
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
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);

        graphics.centeredText(this.font, this.title, this.width / 2, this.titleY, 0xFFFFFFFF);

        int leftX = (this.width - CONTENT_WIDTH) / 2;

        String hint = this.config.filterMode == ModConfig.FilterMode.BLOCK_LIST
            ? "Checked worlds and servers will be excluded:"
            : "ONLY checked worlds and servers will have panoramas:";
        graphics.centeredText(this.font, Component.literal(hint), this.width / 2, this.hintY, 0xFFAAAAAA);

        graphics.fill(leftX - 1, this.listY - 1, leftX + CONTENT_WIDTH + 1, this.listY + LIST_HEIGHT + 1, 0x33FFFFFF);

        if (this.list != null && this.list.children().isEmpty()) {
            graphics.centeredText(this.font, Component.literal("No worlds or servers found."), this.width / 2, this.listY + LIST_HEIGHT / 2 - 4, 0xFFAAAAAA);
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
                .maxWidth(boxSize)
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
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (this.checkbox.mouseClicked(event, doubleClick)) {
                return true;
            }
            if (this.isMouseOver(event.x(), event.y())) {
                this.checkbox.playDownSound(Minecraft.getInstance().getSoundManager());
                this.checkbox.onPress(event);
                return true;
            }
            return false;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean isHovered, float tickProgress) {
            Font font = ConfigScreen.this.font;
            int x = this.getX();
            int y = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();

            graphics.fill(x, y, x + width, y + height, isHovered ? 0x22FFFFFF : 0x0EFFFFFF);

            int textLeft = x + 4;
            graphics.text(font, this.type.tag, textLeft, y + 4, this.type.color);
            int tagWidth = font.width(this.type.tag);

            int maxTextWidth = width - 88;
            String truncatedTitle = font.plainSubstrByWidth(this.title, Math.max(20, maxTextWidth - tagWidth - 4));
            graphics.text(font, truncatedTitle, textLeft + tagWidth + 4, y + 4, 0xFFFFFFFF);

            String truncatedDetail = font.plainSubstrByWidth(this.detail, Math.max(20, maxTextWidth));
            graphics.text(font, truncatedDetail, textLeft, y + 16, 0xFFAAAAAA);

            int boxSize = Checkbox.getBoxSize(font);
            int cbX = x + width - boxSize - 4;
            int cbY = y + (height - boxSize) / 2;
            this.checkbox.setX(cbX);
            this.checkbox.setY(cbY);
            this.checkbox.extractRenderState(graphics, mouseX, mouseY, tickProgress);

            if (this.checkbox.selected()) {
                boolean isBlock = config.filterMode == ModConfig.FilterMode.BLOCK_LIST;
                Component status = isBlock
                    ? Component.literal("Blocked").withStyle(ChatFormatting.RED)
                    : Component.literal("Allowed").withStyle(ChatFormatting.GREEN);
                int statusColor = isBlock ? 0xFFFF5555 : 0xFF55FF55;
                int statusWidth = font.width(status);
                graphics.text(font, status, cbX - statusWidth - 4, y + (height - 9) / 2, statusColor);
            }
        }
    }
}
