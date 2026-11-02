package cn.nightmare9948.disccrafting.client.screen;

import cn.nightmare9948.disccrafting.Lang;
import cn.nightmare9948.disccrafting.client.audio.Track;
import cn.nightmare9948.disccrafting.client.gui.Gui;
import cn.nightmare9948.disccrafting.client.gui.IconButton;
import cn.nightmare9948.disccrafting.util.HandScan;
import cn.nightmare9948.disccrafting.net.CreateDiscPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/** 唱片制作界面：八款图标任选 + 起名字。生存模式还要手里有一张空唱片。 */
public class StudioScreen extends Screen {
    private static final int PANEL_W = 320;
    private static final int PANEL_H = 200;

    private final Track track;
    private TextFieldWidget nameField;
    private int left;
    private int top;
    private int icon = 1;
    private String warning = "";

    public StudioScreen(Track track) {
        super(Lang.t("screen.disc_crafting.studio"));
        this.track = track;
    }

    @Override
    protected void init() {
        this.left = Math.max(6, (this.width - PANEL_W) / 2);
        this.top = Math.max(6, (this.height - PANEL_H) / 2);

        this.nameField = new TextFieldWidget(this.textRenderer, this.left + 40, this.top + 46, 240, 20,
            Lang.t("label.disc_crafting.name"));
        this.nameField.setMaxLength(32);
        this.nameField.setText(this.track.title);
        this.nameField.setPlaceholder(Lang.t("label.disc_crafting.name"));
        this.addDrawableChild(this.nameField);

        for (int i = 1; i <= 8; i++) {
            int column = (i - 1) % 4;
            int row = (i - 1) / 4;
            this.addDrawableChild(new IconButton(
                this.left + 46 + column * 58, this.top + 88 + row * 30, i,
                () -> this.icon, value -> this.icon = value));
        }

        this.addDrawableChild(ButtonWidget.builder(Lang.t("button.disc_crafting.create"), button -> create())
            .dimensions(this.left + 40, this.top + 152, 120, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Lang.t("button.disc_crafting.back"), button -> back())
            .dimensions(this.left + 168, this.top + 152, 112, 20).build());
    }

    private void back() {
        if (this.client != null) {
            this.client.setScreen(new ImportScreen());
        }
    }

    /** 创造模式随便做；生存模式必须手里（主手或副手）拿着空唱片。 */
    private boolean canCraft() {
        if (this.client == null || this.client.player == null) {
            return true;
        }
        if (this.client.player.isCreative()) {
            return true;
        }
        return HandScan.emptyDiscHand(this.client.player) != null;
    }

    private void create() {
        if (!canCraft()) {
            this.warning = Lang.text("message.disc_crafting.need_empty_disc");
            if (this.client != null && this.client.player != null) {
                this.client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.5f);
            }
            return;
        }
        String title = this.nameField.getText() == null ? "" : this.nameField.getText().strip();
        if (title.isEmpty()) {
            title = this.track.title;
        }
        ClientPlayNetworking.send(new CreateDiscPayload(this.track.id, title, this.icon, this.track.durationMs));
        if (this.client != null) {
            this.client.setScreen(null);
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
            this.left + PANEL_W / 2, this.top + 12, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer,
            Lang.t("label.disc_crafting.track_info", this.track.title, this.track.lengthText()),
            this.left + 40, this.top + 32, 0x9A9AAC);
        context.drawTextWithShadow(this.textRenderer, Lang.t("label.disc_crafting.icon"),
            this.left + 40, this.top + 74, 0xB7B7C8);

        boolean ready = canCraft();
        Text bottom;
        int color;
        if (!ready) {
            bottom = Lang.t("message.disc_crafting.need_empty_disc");
            color = 0xFF8A3C;
        } else if (!this.warning.isEmpty()) {
            bottom = Text.literal(this.warning);
            color = 0xFF8A3C;
        } else {
            this.warning = "";
            bottom = Lang.t("hint.disc_crafting.studio");
            color = 0x8A8A9A;
        }
        context.drawCenteredTextWithShadow(this.textRenderer, bottom,
            this.left + PANEL_W / 2, this.top + PANEL_H - 18, color);
    }

    @Override
    public boolean shouldPause() {
        return true;
    }
}
