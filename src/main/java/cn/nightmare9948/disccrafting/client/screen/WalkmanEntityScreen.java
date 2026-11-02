package cn.nightmare9948.disccrafting.client.screen;

import cn.nightmare9948.disccrafting.Lang;
import cn.nightmare9948.disccrafting.client.audio.ClientAudio;
import cn.nightmare9948.disccrafting.client.audio.Playback;
import cn.nightmare9948.disccrafting.client.audio.Playlist;
import cn.nightmare9948.disccrafting.component.DiscSource;
import cn.nightmare9948.disccrafting.entity.WalkmanEntity;
import cn.nightmare9948.disccrafting.net.WalkmanControlPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * 放在地上的随身听自己的控制界面：播放 / 暂停、单曲循环、进度条、这台设备的音量。
 * 这些数据归服务端管，改完就把新状态发给服务端。
 */
public class WalkmanEntityScreen extends WalkmanPanelScreen {
    private final WalkmanEntity entity;

    public WalkmanEntityScreen(WalkmanEntity entity) {
        super(Lang.t("screen.disc_crafting.walkman_entity"));
        this.entity = entity;
    }

    @Override
    protected Playback playback() {
        return ClientAudio.get().entityPlayback(this.entity);
    }

    @Override
    protected DiscSource source() {
        // 列表播放时真正在放的是客户端这一路，不是实体身上记的那首
        DiscSource live = ClientAudio.get().entitySource(this.entity);
        if (live != null && !live.isEmpty()) {
            return live;
        }
        return new DiscSource(this.entity.getTrackId(), this.entity.getSongId());
    }

    @Override
    protected Playlist playlist() {
        return ClientAudio.get().entityPlaylist(this.entity);
    }

    @Override
    protected void onPlaylistChanged() {
        ClientAudio.get().reloadEntity(this.entity);
    }

    @Override
    protected boolean skip(int direction) {
        // 只有列表播放模式下切得动；别的模式下点了没反应
        return ClientAudio.get().skipEntity(this.entity, direction);
    }

    @Override
    protected boolean isPlaying() {
        return this.entity.isPlaying();
    }

    @Override
    protected void togglePlay() {
        this.entity.setPlaying(!this.entity.isPlaying());
        Playback current = playback();
        if (current != null) {
            if (this.entity.isPlaying()) {
                if (current.isPaused()) {
                    current.resume();
                }
            } else {
                current.pause();
            }
        }
        push();
    }

    @Override
    protected boolean loopEnabled() {
        return this.entity.isLooping();
    }

    @Override
    protected void setLoop(boolean loop) {
        this.entity.setLooping(loop);
        Playback current = playback();
        if (current != null) {
            current.setLooping(loop);
        }
        push();
    }

    @Override
    protected int volumePercent() {
        return Math.round(this.entity.getVolume() * 100.0f);
    }

    @Override
    protected void setVolume(int percent) {
        this.entity.setVolume(MathHelper.clamp(percent, 0, 200) / 100.0f);
        push();
    }

    @Override
    protected Text hint() {
        return Lang.t("hint.disc_crafting.walkman_entity");
    }

    /** 实体的数据归服务端管，本地点一下只是即时反馈，真正生效靠这个包。 */
    private void push() {
        ClientPlayNetworking.send(new WalkmanControlPayload(
            this.entity.getId(), this.entity.isPlaying(), this.entity.getVolume(), this.entity.isLooping(),
            this.entity.getTrackId(), this.entity.getSongId()));
    }

    @Override
    public void tick() {
        super.tick();
        // 随身听被敲掉或者被卸载了，界面就自己关掉
        if (this.client != null
            && (this.client.world == null
                || this.client.world.getEntityById(this.entity.getId()) != this.entity
                || !this.entity.isAlive())) {
            this.close();
        }
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(null);
        }
    }
}
