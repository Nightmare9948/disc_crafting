package cn.nightmare9948.disccrafting.client.screen;

import cn.nightmare9948.disccrafting.Lang;
import cn.nightmare9948.disccrafting.client.ModKeybinds;
import cn.nightmare9948.disccrafting.client.audio.ClientAudio;
import cn.nightmare9948.disccrafting.client.audio.Playback;
import cn.nightmare9948.disccrafting.client.audio.Playlist;
import cn.nightmare9948.disccrafting.client.gui.Gui;
import cn.nightmare9948.disccrafting.client.gui.ProgressBar;
import cn.nightmare9948.disccrafting.client.gui.VolumeSlider;
import cn.nightmare9948.disccrafting.component.DiscSource;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 随身听控制界面的公共骨架：曲名 / 播放进度条（可拖动）/ 播放暂停 / 单曲循环 /
 * 上一首、下一首 / 音量，右上角是“编辑列表”（列表播放的开关在编辑界面里）。
 * 手持的随身听和放在地上的随身听各有一套数据来源，所以具体怎么读写由子类实现。
 */
public abstract class WalkmanPanelScreen extends Screen {
    protected static final int PANEL_W = 320;
    protected static final int PANEL_H = 228;

    protected int left;
    protected int top;
    private ButtonWidget playButton;
    private ButtonWidget loopButton;
    private ButtonWidget prevButton;
    private ButtonWidget nextButton;
    /** 按键按住不放时系统会不停重发，记着按下的那个键，只认第一次。 */
    private int toggleKeyHeld = -1;

    protected WalkmanPanelScreen(Text title) {
        super(title);
    }

    // ---------------------------------------------------------------- 子类实现

    /** 当前这一路播放；没有就返回 null。 */
    protected abstract Playback playback();

    /** 当前放的唱片（单张唱片模式用）。 */
    protected abstract DiscSource source();

    /** 这台随身听的播放序列。 */
    protected abstract Playlist playlist();

    /** 列表开关 / 顺序变了之后，通知客户端音频层换模式。 */
    protected abstract void onPlaylistChanged();

    /** 上一首 / 下一首；返回是不是真的切了（只有列表播放模式才切得动）。 */
    protected boolean skip(int direction) {
        return false;
    }

    protected abstract boolean isPlaying();

    protected abstract void togglePlay();

    protected abstract boolean loopEnabled();

    protected abstract void setLoop(boolean loop);

    protected abstract int volumePercent();

    protected abstract void setVolume(int percent);

    /** 界面最下面那行提示。 */
    protected Text hint() {
        return Text.empty();
    }

    /** 额外的状态文字（比如“已暂停”）。 */
    protected Text stateText() {
        return isPlaying()
            ? Lang.t("label.disc_crafting.walkman_playing")
            : Lang.t("label.disc_crafting.walkman_paused");
    }

    /**
     * 界面和进度条显示的曲目：优先显示“真的在放的那一首”，
     * 没有（比如列表播放刚开、还没换曲）就退回序列里的当前曲目。
     */
    protected DiscSource displaySource() {
        DiscSource live = source();
        if (live != null && !live.isEmpty()) {
            return live;
        }
        Playlist playlist = playlist();
        if (playlist != null && playlist.isEnabled() && !playlist.isEmpty()) {
            String current = playlist.current();
            if (current != null) {
                return new DiscSource(current, "");
            }
        }
        return live;
    }

    // ---------------------------------------------------------------- 界面

    @Override
    protected void init() {
        this.left = Math.max(6, (this.width - PANEL_W) / 2);
        this.top = Math.max(6, (this.height - PANEL_H) / 2);

        this.addDrawableChild(new ProgressBar(this.left + 24, this.top + 70, PANEL_W - 48, 14,
            () -> {
                Playback current = playback();
                return current == null ? 0L : current.positionMs();
            },
            () -> {
                Playback current = playback();
                if (current != null && current.durationMs() > 0) {
                    return current.durationMs();
                }
                return ClientAudio.discDurationMs(displaySource());
            },
            ms -> {
                Playback current = playback();
                if (current != null && current.canSeek()) {
                    current.seekTo(ms);
                }
            }));

        this.playButton = ButtonWidget.builder(playText(), button -> {
            togglePlay();
            updateButtons();
        }).dimensions(this.left + 24, this.top + 104, 132, 20).build();
        this.addDrawableChild(this.playButton);

        this.loopButton = ButtonWidget.builder(loopText(), button -> {
            setLoop(!loopEnabled());
            updateButtons();
        }).dimensions(this.left + 164, this.top + 104, 132, 20).build();
        this.addDrawableChild(this.loopButton);

        // 这一排：上一首 / 下一首（只有列表播放模式能切，别的模式下点了没反应）
        this.prevButton = ButtonWidget.builder(Lang.t("button.disc_crafting.prev"),
                button -> skip(-1))
            .dimensions(this.left + 24, this.top + 130, 64, 20).build();
        this.addDrawableChild(this.prevButton);
        this.nextButton = ButtonWidget.builder(Lang.t("button.disc_crafting.next"),
                button -> skip(1))
            .dimensions(this.left + 92, this.top + 130, 64, 20).build();
        this.addDrawableChild(this.nextButton);

        // 右上角：编辑列表（列表播放的开关、顺序都在那个界面里）
        this.addDrawableChild(ButtonWidget.builder(Lang.t("button.disc_crafting.playlist_edit"),
                button -> openEditor())
            .dimensions(this.left + PANEL_W - 76, this.top + 6, 70, 18).build());

        this.addDrawableChild(new VolumeSlider(this.left + 272, this.top + 130, 24, 66,
            this::volumePercent, this::setVolume));

        this.addDrawableChild(ButtonWidget.builder(Lang.t("button.disc_crafting.volume_mute"),
                button -> setVolume(0))
            .dimensions(this.left + 24, this.top + 154, 72, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Lang.t("button.disc_crafting.volume_normal"),
                button -> setVolume(100))
            .dimensions(this.left + 100, this.top + 154, 72, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Lang.t("button.disc_crafting.volume_max"),
                button -> setVolume(200))
            .dimensions(this.left + 176, this.top + 154, 72, 20).build());
    }

    private Text playText() {
        return Lang.t(isPlaying() ? "button.disc_crafting.pause" : "button.disc_crafting.play");
    }

    private Text loopText() {
        return Lang.t(loopEnabled() ? "button.disc_crafting.loop_on" : "button.disc_crafting.loop_off");
    }

    private void openEditor() {
        Playlist playlist = playlist();
        if (playlist == null || this.client == null) {
            return;
        }
        // 进编辑界面不改开关、也不动正在放的这一首：只让玩家自己决定要不要开列表播放
        this.client.setScreen(new PlaylistScreen(playlist, this, this::onPlaylistChanged));
    }

    private void updateButtons() {
        if (this.playButton != null) {
            this.playButton.setMessage(playText());
        }
        if (this.loopButton != null) {
            this.loopButton.setMessage(loopText());
        }
        // 不在列表播放模式时，上一首 / 下一首点不动，按钮也灰着，免得玩家以为坏了
        Playlist playlist = playlist();
        boolean canSkip = playlist != null && playlist.isEnabled() && !playlist.isEmpty();
        if (this.prevButton != null) {
            this.prevButton.active = canSkip;
        }
        if (this.nextButton != null) {
            this.nextButton.active = canSkip;
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        Gui.panel(context, this.left, this.top, PANEL_W, PANEL_H);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateButtons();
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
            this.left + PANEL_W / 2, this.top + 10, 0xFFFFFF);

        Playback current = playback();
        DiscSource disc = displaySource();
        long duration = current != null && current.durationMs() > 0
            ? current.durationMs() : ClientAudio.discDurationMs(disc);

        String info = Lang.t("label.disc_crafting.track_info",
            ClientAudio.discTitle(disc), time(duration)).getString();
        context.drawTextWithShadow(this.textRenderer,
            Text.literal(this.textRenderer.trimToWidth(info, 214)), this.left + 24, this.top + 30, 0x9A9AAC);
        context.drawTextWithShadow(this.textRenderer, stateText(), this.left + 24, this.top + 44,
            isPlaying() ? 0x9AE86B : 0xE8E8A0);

        Text playlistLine = playlistLine();
        if (playlistLine != null) {
            context.drawTextWithShadow(this.textRenderer,
                Text.literal(this.textRenderer.trimToWidth(playlistLine.getString(), PANEL_W - 48)),
                this.left + 24, this.top + 56, 0x9A9AAC);
        }

        long now = current == null ? 0 : current.positionMs();
        context.drawTextWithShadow(this.textRenderer, Text.literal(time(now)),
            this.left + 24, this.top + 88, 0xB7B7C8);
        String total = time(duration);
        context.drawTextWithShadow(this.textRenderer, Text.literal(total),
            this.left + PANEL_W - 24 - this.textRenderer.getWidth(total), this.top + 88, 0xB7B7C8);

        if (current != null && !current.canSeek()) {
            int lineY = this.top + 176;
            for (net.minecraft.text.OrderedText line
                : this.textRenderer.wrapLines(Lang.t("hint.disc_crafting.no_seek"), PANEL_W - 84)) {
                context.drawTextWithShadow(this.textRenderer, line, this.left + 24, lineY, 0x8A8A9A);
                lineY += 10;
            }
        }

        int percent = volumePercent();
        context.drawTextWithShadow(this.textRenderer,
            Lang.t("label.disc_crafting.volume_now").copy().append(Text.literal(" " + percent + "%")),
            this.left + 162, this.top + 136, percent > 100 ? 0xFFD24A : 0xB7B7C8);

        Text hint = hint();
        if (!hint.getString().isEmpty()) {
            int lineY = this.top + 198;
            for (net.minecraft.text.OrderedText line : this.textRenderer.wrapLines(hint, PANEL_W - 84)) {
                context.drawTextWithShadow(this.textRenderer, line, this.left + 24, lineY, 0x8A8A9A);
                lineY += 10;
            }
        }
    }

    /** 播放序列那行状态：没开列表就返回 null。 */
    private Text playlistLine() {
        Playlist playlist = playlist();
        if (playlist == null || !playlist.isEnabled()) {
            return null;
        }
        if (playlist.isEmpty()) {
            return Lang.t("hint.disc_crafting.playlist_empty_short");
        }
        String order = Lang.text("order.disc_crafting." + playlist.order().key());
        return Lang.t("label.disc_crafting.playlist_state", order,
            (playlist.index() + 1) + "/" + playlist.size());
    }

    /** 毫秒 -> 分:秒。 */
    protected static String time(long ms) {
        long totalSeconds = Math.max(0, ms / 1000L);
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    /**
     * 界面开着的时候，原版不会再把按键派给“播放 / 暂停”快捷键，
     * 于是玩家在随身听控制界面里按 N 就毫无反应。这里自己接一次：
     * 按键对得上就当场切播放，效果和按钮一模一样。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ModKeybinds.isTogglePlayKey(keyCode, scanCode)) {
            if (this.toggleKeyHeld == keyCode) {
                return true; // 按住不放的重发事件，别再切回去
            }
            this.toggleKeyHeld = keyCode;
            shortcutToggle();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == this.toggleKeyHeld) {
            this.toggleKeyHeld = -1;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    /** 快捷键切播放；切完顺手刷新一下按钮上的字。 */
    boolean shortcutToggle() {
        togglePlay();
        updateButtons();
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
