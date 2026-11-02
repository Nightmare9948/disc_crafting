package cn.nightmare9948.disccrafting.client.screen;

import cn.nightmare9948.disccrafting.Lang;
import cn.nightmare9948.disccrafting.client.Settings;
import cn.nightmare9948.disccrafting.client.gui.Gui;
import cn.nightmare9948.disccrafting.client.gui.PercentSlider;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 水中音效的详细设置：一个总开关，一个 0~300% 的强度滑块。
 *
 * 100% 就是原本的默认闷感；调低会保留更多高频（没那么闷），调到 0% 等于不处理；
 * 往上调更闷，300% 是上限。开关和强度都会立刻作用到正在播放的声音上。
 */
public class UnderwaterSettingsScreen extends Screen {
    private static final int PANEL_W = 320;
    private static final int PANEL_H = 206;

    private final Screen parent;
    private int left;
    private int top;
    private ButtonWidget toggleButton;

    public UnderwaterSettingsScreen(Screen parent) {
        super(Lang.t("screen.disc_crafting.underwater"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.left = Math.max(6, (this.width - PANEL_W) / 2);
        this.top = Math.max(6, (this.height - PANEL_H) / 2);

        // 总开关：关掉之后水里也按原声播放
        this.toggleButton = ButtonWidget.builder(underwaterText(), button -> {
            Settings.setUnderwaterSound(!Settings.underwaterSound());
            button.setMessage(underwaterText());
        }).dimensions(this.left + 16, this.top + 40, 140, 20).build();
        this.addDrawableChild(this.toggleButton);

        // 一键回到默认的 100%
        this.addDrawableChild(ButtonWidget.builder(Lang.t("button.disc_crafting.underwater_default"),
                button -> Settings.setUnderwaterStrength(Settings.DEFAULT_UNDERWATER_STRENGTH))
            .dimensions(this.left + 164, this.top + 40, 140, 20).build());

        // 强度滑块：0~300%，每 5% 一挡
        this.addDrawableChild(new PercentSlider(this.left + 16, this.top + 84, PANEL_W - 32, 18,
            Settings.MAX_UNDERWATER_STRENGTH, 5,
            Lang.t("slider.disc_crafting.underwater"),
            Settings::underwaterStrength,
            Settings::setUnderwaterStrength));

        this.addDrawableChild(ButtonWidget.builder(Lang.t("button.disc_crafting.back"), button -> back())
            .dimensions(this.left + PANEL_W / 2 - 50, this.top + PANEL_H - 30, 100, 20).build());
    }

    private Text underwaterText() {
        return Lang.t(Settings.underwaterSound()
            ? "button.disc_crafting.underwater_on" : "button.disc_crafting.underwater_off");
    }

    private void back() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        Gui.panel(context, this.left, this.top, PANEL_W, PANEL_H);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
            this.left + PANEL_W / 2, this.top + 10, 0xFFFFFF);

        // 强度那一行的读数：现在调到多少
        context.drawTextWithShadow(this.textRenderer,
            Lang.t("label.disc_crafting.underwater_strength", Settings.underwaterStrength()),
            this.left + 16, this.top + 68, 0xB7B7C8);

        int line = this.top + 112;
        for (net.minecraft.text.OrderedText hint
            : this.textRenderer.wrapLines(Lang.t("hint.disc_crafting.underwater"), PANEL_W - 32)) {
            context.drawTextWithShadow(this.textRenderer, hint, this.left + 16, line, 0x8A8A9A);
            line += 10;
        }

        // 页面最下方（返回按钮上方）的建议值提示
        context.drawCenteredTextWithShadow(this.textRenderer,
            Lang.t("hint.disc_crafting.underwater_suggest"),
            this.left + PANEL_W / 2, this.top + PANEL_H - 44, 0xE8C56A);
    }

    @Override
    public boolean shouldPause() {
        return true;
    }
}
