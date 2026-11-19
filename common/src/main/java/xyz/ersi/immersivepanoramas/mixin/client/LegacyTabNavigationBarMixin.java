package xyz.ersi.immersivepanoramas.mixin.client;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.layouts.GridLayout;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.ersi.immersivepanoramas.client.LegacyMenuBackground;

@Mixin(TabNavigationBar.class)
public class LegacyTabNavigationBarMixin {
    @Shadow @Final private GridLayout layout;
    @Shadow @Final private ImmutableList<TabButton> tabButtons;
    @Shadow private int width;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        int y = this.layout.getY() + this.layout.getHeight() - 2;
        LegacyMenuBackground.renderTexture(graphics, "header_separator", 0, y, this.tabButtons.get(0).getX(), 2, 0, 0, 32, 2);
        TabButton last = this.tabButtons.get(this.tabButtons.size() - 1);
        int right = last.getX() + last.getWidth();
        LegacyMenuBackground.renderTexture(graphics, "header_separator", right, y, this.width - right, 2, 0, 0, 32, 2);
        for (TabButton tab : this.tabButtons) {
            tab.render(graphics, mouseX, mouseY, partialTick);
        }
        ci.cancel();
    }
}
