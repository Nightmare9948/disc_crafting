package cn.nightmare9948.disccrafting.client.screen;

import cn.nightmare9948.disccrafting.Lang;
import cn.nightmare9948.disccrafting.client.Settings;
import cn.nightmare9948.disccrafting.client.audio.ClientAudio;
import cn.nightmare9948.disccrafting.client.audio.Playback;
import cn.nightmare9948.disccrafting.client.audio.Playlist;
import cn.nightmare9948.disccrafting.component.DiscSource;
import net.minecraft.text.Text;

/**
 * 手持随身听的控制界面（默认 B 键打开）：
 * 曲名、进度条（可拖动）、播放/暂停、单曲循环、0~200% 音量。
 */
public class WalkmanHandheldScreen extends WalkmanPanelScreen {
    public WalkmanHandheldScreen() {
        super(Lang.t("screen.disc_crafting.walkman_handheld"));
    }

    @Override
    protected Playback playback() {
        return ClientAudio.get().handheldPlayback();
    }

    @Override
    protected DiscSource source() {
        return ClientAudio.get().handheldSource();
    }

    @Override
    protected Playlist playlist() {
        return ClientAudio.get().handheldPlaylist();
    }

    @Override
    protected void onPlaylistChanged() {
        ClientAudio.get().reloadHandheld();
    }

    @Override
    protected boolean skip(int direction) {
        // 只有列表播放模式下切得动；别的模式下点了没反应
        return ClientAudio.get().skipHandheld(direction);
    }

    @Override
    protected boolean isPlaying() {
        Playback current = playback();
        return current != null && current.isActive();
    }

    @Override
    protected void togglePlay() {
        ClientAudio.get().toggleHandheld();
    }

    @Override
    protected boolean loopEnabled() {
        return Settings.loopSingle();
    }

    @Override
    protected void setLoop(boolean loop) {
        Settings.setLoopSingle(loop);
        Playback current = playback();
        if (current != null) {
            current.setLooping(loop);
        }
    }

    @Override
    protected int volumePercent() {
        return Settings.volumePercent();
    }

    @Override
    protected void setVolume(int percent) {
        Settings.setVolumePercent(percent);
    }

    @Override
    protected Text hint() {
        return Lang.t("hint.disc_crafting.walkman_handheld");
    }
}
