package cn.nightmare9948.disccrafting.client.screen;

import cn.nightmare9948.disccrafting.Lang;
import cn.nightmare9948.disccrafting.client.ModKeybinds;
import cn.nightmare9948.disccrafting.client.audio.Playlist;
import cn.nightmare9948.disccrafting.client.audio.PlaylistStore;
import cn.nightmare9948.disccrafting.client.audio.Track;
import cn.nightmare9948.disccrafting.client.audio.TrackStore;
import cn.nightmare9948.disccrafting.client.gui.Gui;
import cn.nightmare9948.disccrafting.client.gui.SelectableList;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * 播放序列编辑界面：左边是唱片工坊导入过的音频，右边是这台随身听的播放序列。
 * 点左边的曲目加进序列，点右边的曲目从序列里拿掉；下面的按钮换播放顺序。
 * “列表播放”的开关也在这里 —— 开关和进出界面都不会打断正在放的那一首。
 */
public class PlaylistScreen extends Screen {
    private static final int PANEL_W = 356;
    private static final int PANEL_H = 268;
    private static final int LIST_H = 140;

    private final Playlist playlist;
    private final Screen back;
    private final Runnable onChanged;

    private List<Track> library = List.of();
    private int left;
    private int top;
    private ButtonWidget orderButton;
    private ButtonWidget switchButton;
    private String notice = "";
    private int noticeTicks;
    /** 播放 / 暂停键按下的那个键码，用来挡住按住不放的重发。 */
    private int toggleKeyHeld = -1;

    public PlaylistScreen(Playlist playlist, Screen back, Runnable onChanged) {
        super(Lang.t("screen.disc_crafting.playlist"));
        this.playlist = playlist;
        this.back = back;
        this.onChanged = onChanged;
    }

    @Override
    protected void init() {
        this.left = Math.max(6, (this.width - PANEL_W) / 2);
        this.top = Math.max(6, (this.height - PANEL_H) / 2);
        this.library = TrackStore.all();

        this.addDrawableChild(new SelectableList(this.left + 12, this.top + 42, 160, LIST_H,
            new SelectableList.Model() {
                @Override
                public int size() {
                    return library.size();
                }

                @Override
                public String label(int index) {
                    Track track = library.get(index);
                    return track.title + "  " + track.lengthText();
                }

                @Override
                public boolean playing(int index) {
                    String current = playlist.current();
                    return current != null && current.equals(library.get(index).id);
                }
            }, index -> addTrack(library.get(index))));

        this.addDrawableChild(new SelectableList(this.left + 184, this.top + 42, 160, LIST_H,
            new SelectableList.Model() {
                @Override
                public int size() {
                    return playlist.size();
                }

                @Override
                public String label(int index) {
                    String id = playlist.trackAt(index);
                    Track track = id == null ? null : TrackStore.get(id);
                    String name = track != null ? track.title : (id == null ? "?" : id);
                    return (index + 1) + ". " + name;
                }

                @Override
                public boolean playing(int index) {
                    return playlist.current() != null && index == playlist.index();
                }
            }, this::removeTrack));

        // 列表播放的总开关：就在这里拨，不用再退回控制界面
        this.switchButton = ButtonWidget.builder(switchText(), button -> {
            this.playlist.setEnabled(!this.playlist.isEnabled());
            PlaylistStore.save();
            notifyChanged();
            button.setMessage(switchText());
            this.notice = Lang.text(this.playlist.isEnabled()
                ? "message.disc_crafting.playlist_on"
                : "message.disc_crafting.playlist_off");
            this.noticeTicks = 60;
        }).dimensions(this.left + 12, this.top + 202, 122, 20).build();
        this.addDrawableChild(this.switchButton);

        this.orderButton = ButtonWidget.builder(orderText(), button -> {
            this.playlist.cycleOrder();
            PlaylistStore.save();
            notifyChanged();
            button.setMessage(orderText());
            this.notice = Lang.text("message.disc_crafting.playlist_order",
                Lang.text("order.disc_crafting." + this.playlist.order().key()));
            this.noticeTicks = 60;
        }).dimensions(this.left + 138, this.top + 202, 122, 20).build();
        this.addDrawableChild(this.orderButton);

        this.addDrawableChild(ButtonWidget.builder(Lang.t("button.disc_crafting.playlist_clear"),
                button -> {
                    this.playlist.clear();
                    PlaylistStore.save();
                    notifyChanged();
                    this.notice = Lang.text("hint.disc_crafting.playlist_empty");
                    this.noticeTicks = 60;
                })
            .dimensions(this.left + 264, this.top + 202, 80, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Lang.t("button.disc_crafting.done"),
                button -> this.close())
            .dimensions(this.left + PANEL_W - 86, this.top + 226, 74, 20).build());
    }

    private Text switchText() {
        return Lang.t(this.playlist.isEnabled()
            ? "button.disc_crafting.playlist_on" : "button.disc_crafting.playlist_off");
    }

    private Text orderText() {
        return Lang.t("button.disc_crafting.playlist_order",
            Lang.text("order.disc_crafting." + this.playlist.order().key()));
    }

    /** 改完就通知音频层：它按此刻的开关决定下一首怎么走，不会重开当前这一首。 */
    private void notifyChanged() {
        if (this.onChanged != null) {
            this.onChanged.run();
        }
    }

    private void addTrack(Track track) {
        if (this.playlist.add(track.id)) {
            PlaylistStore.save();
            this.notice = Lang.text("message.disc_crafting.playlist_added", track.title);
        } else {
            this.notice = Lang.text("message.disc_crafting.playlist_duplicate");
        }
        this.noticeTicks = 60;
    }

    private void removeTrack(int index) {
        if (this.playlist.removeAt(index)) {
            PlaylistStore.save();
            notifyChanged();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.noticeTicks > 0) {
            this.noticeTicks--;
            if (this.noticeTicks == 0) {
                this.notice = "";
            }
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

        context.drawTextWithShadow(this.textRenderer,
            Lang.t("label.disc_crafting.library_count", Integer.toString(this.library.size())),
            this.left + 12, this.top + 28, 0xB7B7C8);
        context.drawTextWithShadow(this.textRenderer,
            Lang.t("label.disc_crafting.queue_count", Integer.toString(this.playlist.size())),
            this.left + 184, this.top + 28, 0xB7B7C8);

        String current = this.playlist.current();
        Text now = current == null
            ? Lang.t("label.disc_crafting.playlist_none")
            : Lang.t("label.disc_crafting.playlist_now", trackName(current));
        context.drawTextWithShadow(this.textRenderer, now, this.left + 12, this.top + 184, 0x8FBF6B);

        Text bottom;
        int color;
        if (!this.notice.isEmpty()) {
            bottom = Text.literal(this.notice);
            color = 0xFFD24A;
        } else if (this.playlist.isEmpty()) {
            bottom = Lang.t("hint.disc_crafting.playlist_empty");
            color = 0xFF8A3C;
        } else {
            bottom = Lang.t("hint.disc_crafting.playlist");
            color = 0x8A8A9A;
        }
        context.drawTextWithShadow(this.textRenderer, this.fit(bottom.getString(), PANEL_W - 24),
            this.left + 12, this.top + 250, color);
    }

    private String fit(String text, int width) {
        return this.textRenderer.trimToWidth(text, width) + (this.textRenderer.getWidth(text) > width ? "…" : "");
    }

    private static String trackName(String trackId) {
        Track track = TrackStore.get(trackId);
        return track != null ? track.title : trackId;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.back);
        }
    }

    /** 在这个界面里按播放 / 暂停键，也照样能切播放，不用退回上一页。 */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ModKeybinds.isTogglePlayKey(keyCode, scanCode)) {
            if (this.toggleKeyHeld == keyCode) {
                return true; // 按住不放的重发事件
            }
            this.toggleKeyHeld = keyCode;
            if (this.back instanceof WalkmanPanelScreen panel) {
                panel.shortcutToggle();
            }
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

    @Override
    public boolean shouldPause() {
        return false;
    }
}
