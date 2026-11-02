package cn.nightmare9948.disccrafting.client.screen;

import cn.nightmare9948.disccrafting.DiscCrafting;
import cn.nightmare9948.disccrafting.Lang;
import cn.nightmare9948.disccrafting.client.Settings;
import cn.nightmare9948.disccrafting.client.audio.AudioImporter;
import cn.nightmare9948.disccrafting.client.audio.Track;
import cn.nightmare9948.disccrafting.client.audio.TrackStore;
import cn.nightmare9948.disccrafting.client.gui.Gui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 唱片工艺主界面：导入音频（也能直接把文件拖进窗口）、选音质、选曲目、进入唱片制作。
 * 导入只留一个按钮，点它就弹系统自带的选文件窗口；打不开就把原因写清楚。
 */
public class ImportScreen extends Screen {
    private static final int PANEL_W = 380;
    private static final int PANEL_H = 240;

    private final List<Track> tracks = new ArrayList<>();
    private int left;
    private int top;
    private int selected = -1;
    private volatile String status = "";
    private boolean importing;

    public ImportScreen() {
        super(Lang.t("screen.disc_crafting.main"));
    }

    @Override
    protected void init() {
        this.tracks.clear();
        this.tracks.addAll(TrackStore.all());
        this.left = Math.max(6, (this.width - PANEL_W) / 2);
        this.top = Math.max(6, (this.height - PANEL_H) / 2);

        this.addDrawableChild(ButtonWidget.builder(Lang.t("button.disc_crafting.import"),
                button -> openFilePicker())
            .dimensions(this.left + 14, this.top + 30, 140, 20).build());

        this.addDrawableChild(ButtonWidget.builder(qualityText(), button -> {
            Settings.cycleQuality();
            button.setMessage(qualityText());
            this.status = Lang.text("message.disc_crafting.quality_set",
                Lang.text("quality.disc_crafting." + Settings.qualityName()),
                Lang.text(Settings.qualityDetailKey()));
        }).dimensions(this.left + 14, this.top + 54, 170, 20).build());

        // 语言 / 音量这些设置从这里也能进（装了模组菜单的话在模组菜单里也有入口）
        this.addDrawableChild(ButtonWidget.builder(Lang.t("button.disc_crafting.settings"),
                button -> this.client.setScreen(new ConfigScreen(this)))
            .dimensions(this.left + 14, this.top + PANEL_H - 28, 90, 20).build());

        int rows = Math.min(this.tracks.size(), 4);
        for (int i = 0; i < rows; i++) {
            final int index = i;
            this.addDrawableChild(ButtonWidget.builder(rowText(i), button -> {
                this.selected = index;
                this.clearAndInit();
            }).dimensions(this.left + 200, this.top + 30 + i * 24, 166, 20).build());
        }

        this.addDrawableChild(ButtonWidget.builder(Lang.t("button.disc_crafting.make"),
                button -> openStudio())
            .dimensions(this.left + 200, this.top + 138, 166, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Lang.t("button.disc_crafting.done"),
                button -> this.close())
            .dimensions(this.left + PANEL_W / 2 - 50 + 30, this.top + PANEL_H - 28, 100, 20).build());
    }

    private static Text qualityText() {
        return Lang.t("button.disc_crafting.quality",
            Lang.t("quality.disc_crafting." + Settings.qualityName()));
    }

    private Text rowText(int index) {
        Track track = this.tracks.get(index);
        String prefix = index == this.selected ? "▶ " : "  ";
        String name = track.title;
        if (name.length() > 14) {
            name = name.substring(0, 14);
        }
        return Text.literal(prefix + name + "  " + track.lengthText());
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
        context.drawTextWithShadow(this.textRenderer, Lang.t("gui.disc_crafting.library"),
            this.left + 200, this.top + 18, 0xB7B7C8);
        context.drawTextWithShadow(this.textRenderer,
            Lang.t("label.disc_crafting.quality_detail", Lang.text(Settings.qualityDetailKey())),
            this.left + 14, this.top + 78, 0x9A9AAC);

        int line = this.top + 94;
        for (net.minecraft.text.OrderedText hint : this.textRenderer.wrapLines(Lang.t("hint.disc_crafting.import"), 174)) {
            context.drawTextWithShadow(this.textRenderer, hint, this.left + 14, line, 0x8A8A9A);
            line += 10;
        }

        if (this.tracks.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, Lang.t("hint.disc_crafting.empty"),
                this.left + 200, this.top + 60, 0x9A9AAC);
        }

        String selectedName = this.selected >= 0 && this.selected < this.tracks.size()
            ? this.tracks.get(this.selected).title : "-";
        context.drawTextWithShadow(this.textRenderer,
            Lang.t("label.disc_crafting.selected").copy().append(Text.literal(selectedName)),
            this.left + 200, this.top + 124, 0xB7B7C8);

        int statusLine = this.top + 168;
        if (!this.status.isEmpty()) {
            for (net.minecraft.text.OrderedText text : this.textRenderer.wrapLines(Text.literal(this.status), PANEL_W - 28)) {
                context.drawTextWithShadow(this.textRenderer, text, this.left + 14, statusLine, 0xE8E8A0);
                statusLine += 10;
            }
        }
        if (this.importing) {
            context.drawTextWithShadow(this.textRenderer,
                Lang.t("hint.disc_crafting.working").formatted(Formatting.YELLOW),
                this.left + 14, this.top + 196, 0xFFD24A);
        }
    }

    @Override
    public void filesDragged(List<Path> paths) {
        for (Path path : paths) {
            if (AudioImporter.looksLikeAudio(path)) {
                startImport(path);
                return;
            }
        }
        this.status = Lang.text("message.disc_crafting.dragged_not_audio");
    }

    private void openStudio() {
        if (this.selected < 0 || this.selected >= this.tracks.size()) {
            this.status = Lang.text("message.disc_crafting.select_track_first");
            return;
        }
        if (this.client != null) {
            this.client.setScreen(new StudioScreen(this.tracks.get(this.selected)));
        }
    }

    /** 弹出系统自带的选文件小窗口（和光影模组“从文件导入配置”是同一个东西）。 */
    private void openFilePicker() {
        if (this.client == null) {
            return;
        }
        if (NativeFilePicker.isOpen()) {
            return;
        }
        this.status = Lang.text("message.disc_crafting.picker_opening");
        try {
            NativeFilePicker.open(this::startImport, this::pickerFailed,
                () -> this.status = Lang.text("message.disc_crafting.picker_cancelled"));
        } catch (Throwable throwable) {
            DiscCrafting.LOGGER.warn("调用系统文件选择框失败", throwable);
            pickerFailed(NativeFilePicker.FAIL_UNKNOWN, throwable.getClass().getSimpleName());
        }
    }

    /** 打不开系统窗口时，把原因说清楚，并提示还能拖文件进来。 */
    private void pickerFailed(String code, String detail) {
        String key = switch (code) {
            case NativeFilePicker.FAIL_HEADLESS -> "message.disc_crafting.picker_headless";
            case NativeFilePicker.FAIL_NO_TOOL -> "message.disc_crafting.picker_no_tool";
            default -> "message.disc_crafting.picker_failed";
        };
        String reason = detail == null || detail.isEmpty() ? "-" : detail;
        this.status = Lang.text(key, reason);
        DiscCrafting.LOGGER.warn("系统选文件窗口打不开（{}）：{}", code, reason);
    }

    public void startImport(Path path) {
        if (this.importing) {
            return;
        }
        this.importing = true;
        this.status = Lang.text("message.disc_crafting.importing", String.valueOf(path.getFileName()));
        Thread thread = new Thread(() -> {
            boolean ok = false;
            String message;
            try {
                Track track = AudioImporter.importFile(path);
                ok = true;
                message = Lang.text("message.disc_crafting.imported", track.title, track.lengthText());
            } catch (Throwable t) {
                message = Lang.text("message.disc_crafting.import_failed", friendly(t));
                DiscCrafting.LOGGER.warn("导入音频失败: {}", path, t);
            }
            final String finalMessage = message;
            final boolean success = ok;
            MinecraftClient.getInstance().execute(() -> {
                this.importing = false;
                this.status = finalMessage;
                if (success) {
                    this.selected = 0;
                    if (MinecraftClient.getInstance().currentScreen == this) {
                        this.clearAndInit();
                    }
                }
            });
        }, "disc-crafting-import");
        thread.setDaemon(true);
        thread.start();
    }

    private static String friendly(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isEmpty()) {
            return throwable.getClass().getSimpleName();
        }
        return message.length() > 60 ? message.substring(0, 60) : message;
    }

    @Override
    public boolean shouldPause() {
        return true;
    }
}
