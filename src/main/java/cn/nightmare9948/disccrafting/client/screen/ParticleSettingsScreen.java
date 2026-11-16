package cn.nightmare9948.disccrafting.client.screen;

import cn.nightmare9948.disccrafting.Lang;
import cn.nightmare9948.disccrafting.client.Settings;
import cn.nightmare9948.disccrafting.client.gui.Gui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 头顶音符粒子的设置：一个总开关、一个生成速度、一个样式。
 *
 * 粒子只在“真的在出声”的时候冒（暂停、静音、放完都不冒），
 * 冒的位置是随身听的上方 —— 手持那台就在玩家头顶，放在地上的那台就在它自己头顶。
 */
public class ParticleSettingsScreen extends Screen {
    private static final int PANEL_W = 320;
    private static final int PANEL_H = 196;
    private static final int BUTTON_W = 92;

    private final Screen parent;
    private int left;
    private int top;
    private ButtonWidget toggleButton;
    private ButtonWidget speedButton;
    private ButtonWidget styleButton;

    public ParticleSettingsScreen(Screen parent) {
        super(Lang.t("screen.disc_crafting.particles"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.left = Math.max(6, (this.width - PANEL_W) / 2);
        this.top = Math.max(6, (this.height - PANEL_H) / 2);

        int x = this.left + 16;
        int y = this.top + 40;
        this.toggleButton = ButtonWidget.builder(toggleText(), button -> {
            Settings.setNoteParticles(!Settings.noteParticles());
            button.setMessage(toggleText());
        }).dimensions(x, y, BUTTON_W, 20).build();
        this.addDrawableChild(this.toggleButton);

        this.speedButton = ButtonWidget.builder(speedText(), button -> {
            Settings.cycleParticleSpeed();
            button.setMessage(speedText());
        }).dimensions(x + BUTTON_W + 6, y, BUTTON_W, 20).build();
        this.addDrawableChild(this.speedButton);

        this.styleButton = ButtonWidget.builder(styleText(), button -> {
            Settings.cycleParticleStyle();
            button.setMessage(styleText());
        }).dimensions(x + (BUTTON_W + 6) * 2, y, BUTTON_W, 20).build();
        this.addDrawableChild(this.styleButton);

        this.addDrawableChild(ButtonWidget.builder(Lang.t("button.disc_crafting.particle_default"),
                button -> {
                    Settings.resetParticles();
                    refreshButtons();
                })
            .dimensions(x, this.top + 124, 140, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Lang.t("button.disc_crafting.back"), button -> back())
            .dimensions(x + 148, this.top + 124, 140, 20).build());
    }

    private void refreshButtons() {
        if (this.toggleButton != null) {
            this.toggleButton.setMessage(toggleText());
        }
        if (this.speedButton != null) {
            this.speedButton.setMessage(speedText());
        }
        if (this.styleButton != null) {
            this.styleButton.setMessage(styleText());
        }
    }

    private Text toggleText() {
        return Lang.t(Settings.noteParticles()
            ? "button.disc_crafting.particle_on" : "button.disc_crafting.particle_off");
    }

    private Text speedText() {
        // 参数用纯字符串：Lang 内部走 String.format，塞 Text 进去会变成对象地址
        return Lang.t("button.disc_crafting.particle_speed",
            Lang.text("speed.disc_crafting." + Settings.particleSpeedName()));
    }

    private Text styleText() {
        return Lang.t("button.disc_crafting.particle_style",
            Lang.text("style.disc_crafting." + Settings.particleStyleName()));
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

        int line = this.top + 70;
        for (net.minecraft.text.OrderedText hint
            : this.textRenderer.wrapLines(Lang.t("hint.disc_crafting.particles"), PANEL_W - 32)) {
            context.drawTextWithShadow(this.textRenderer, hint, this.left + 16, line, 0x8A8A9A);
            line += 10;
        }
    }

    @Override
    public boolean shouldPause() {
        return true;
    }
}
