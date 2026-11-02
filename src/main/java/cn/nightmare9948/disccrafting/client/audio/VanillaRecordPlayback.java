package cn.nightmare9948.disccrafting.client.audio;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;

/**
 * 退路播放：直接把原版那一段唱片音频交给游戏声音系统放。
 *
 * 正常情况下原版唱片会走 {@link OggAssetSource} 自己解码（这样才有进度和拖动），
 * 只有在读不到音频文件时才退到这里：没有进度、不能拖动，但至少能出声。
 *
 * 音量随时可改：声音实例是“每 tick 重新取一次音量”的类型，
 * 暂停就是把音量压到 0（声音停在那里不出声），继续时再抬回来。
 */
public final class VanillaRecordPlayback implements Playback {
    private final SoundEvent soundEvent;
    private final Vec3d position;
    /** 随身听拿在手上的时候，声音要跟着玩家走，不能钉在原地。 */
    private final boolean followPlayer;
    private final long durationMs;
    private volatile float gain = 1.0f;
    private volatile boolean paused;
    private volatile boolean stopped;
    private volatile boolean loop;
    /** 水中音效（这条退路上只能压音量）。 */
    private volatile boolean muffled;
    /** 水中音效强度，0~3；这条退路上只能按它多压一点音量。 */
    private volatile float muffleStrength = 1.0f;
    private RecordSound instance;
    private long startedAt;
    private long positionBase;
    private long runningSince;

    public VanillaRecordPlayback(SoundEvent soundEvent, Vec3d position, boolean followPlayer, long durationMs) {
        this.soundEvent = soundEvent;
        this.position = position == null ? Vec3d.ZERO : position;
        this.followPlayer = followPlayer;
        this.durationMs = Math.max(0, durationMs);
    }

    @Override
    public void start() {
        if (this.soundEvent == null || this.stopped) {
            return;
        }
        SoundManager manager = MinecraftClient.getInstance().getSoundManager();
        if (manager == null) {
            return;
        }
        this.startedAt = System.currentTimeMillis();
        this.positionBase = 0;
        this.runningSince = this.startedAt;
        this.instance = new RecordSound(this.soundEvent, this.position, this.followPlayer);
        this.instance.setGain(this.gain * this.muffleGain());
        manager.play(this.instance);
    }

    @Override
    public void stop() {
        this.stopped = true;
        if (this.instance != null) {
            MinecraftClient.getInstance().getSoundManager().stop(this.instance);
        }
    }

    @Override
    public void pause() {
        this.paused = true;
        if (this.instance != null) {
            this.instance.setGain(0.0f);
        }
    }

    @Override
    public void resume() {
        this.paused = false;
        applyGain();
    }

    @Override
    public boolean isPaused() {
        return this.paused;
    }

    @Override
    public boolean isFinished() {
        if (this.stopped || this.instance == null) {
            return true;
        }
        // 刚开始播放的头半秒，声音系统可能还没登记好，先当它在放
        if (System.currentTimeMillis() - this.startedAt < 500L) {
            return false;
        }
        return !MinecraftClient.getInstance().getSoundManager().isPlaying(this.instance);
    }

    @Override
    public boolean isActive() {
        return !this.stopped && !this.paused && this.instance != null && !this.isFinished();
    }

    @Override
    public boolean handlesDistance() {
        return true;
    }

    @Override
    public void setGain(float value) {
        this.gain = Math.max(0.0f, Math.min(1.0f, value));
        applyGain();
    }

    /**
     * 退路播放走的是游戏自带的声音系统，改不了频响，只能把音量压下去近似“闷住”。
     * 真正的低通在水中音效里由 {@link StreamPlayback} 负责，这条路只在读不到音频文件时才会走到。
     */
    @Override
    public void setMuffled(boolean value) {
        this.muffled = value;
        applyGain();
    }

    @Override
    public void setMuffleStrength(float strength) {
        this.muffleStrength = Math.max(0.0f, Math.min(3.0f, strength));
        applyGain();
    }

    private void applyGain() {
        if (this.instance != null && !this.paused) {
            this.instance.setGain(this.gain * this.muffleGain());
        }
    }

    /** 强度越高压得越狠；300% 时大约只剩四成音量。 */
    private float muffleGain() {
        if (!this.muffled) {
            return 1.0f;
        }
        return Math.max(0.1f, 1.0f - 0.4f * Math.min(2.0f, Math.max(0.0f, this.muffleStrength)));
    }

    @Override
    public String error() {
        return null;
    }

    // 退路播放没有精确进度：只能按墙上的钟估一估，也不能拖动。

    @Override
    public boolean canSeek() {
        return false;
    }

    @Override
    public long positionMs() {
        long now = System.currentTimeMillis();
        return Math.max(0, this.positionBase + (this.paused ? 0 : now - this.runningSince));
    }

    @Override
    public long durationMs() {
        return this.durationMs;
    }

    @Override
    public void seekTo(long ms) {
        // 声音系统没法从中间开始，拖动在退路上不生效
    }

    @Override
    public boolean isLooping() {
        return this.loop;
    }

    @Override
    public void setLooping(boolean value) {
        this.loop = value;
    }

    /** 每 tick 会被游戏重新读一次音量的声音实例。 */
    private static final class RecordSound extends PositionedSoundInstance implements TickableSoundInstance {
        private final boolean followPlayer;
        private volatile float gain = 1.0f;

        RecordSound(SoundEvent event, Vec3d pos, boolean followPlayer) {
            super(event, SoundCategory.RECORDS, 1.0f, 1.0f, SoundInstance.createRandom(), pos.x, pos.y, pos.z);
            this.followPlayer = followPlayer;
        }

        @Override
        public float getVolume() {
            return super.getVolume() * this.gain;
        }

        @Override
        public void tick() {
            if (this.followPlayer) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    Vec3d eye = client.player.getEyePos();
                    this.x = eye.x;
                    this.y = eye.y;
                    this.z = eye.z;
                }
            }
            // 放下的那种随身听位置固定，不跟着人走
        }

        @Override
        public boolean isDone() {
            return false;
        }

        void setGain(float value) {
            this.gain = Math.max(0.0f, Math.min(1.0f, value));
        }
    }
}
