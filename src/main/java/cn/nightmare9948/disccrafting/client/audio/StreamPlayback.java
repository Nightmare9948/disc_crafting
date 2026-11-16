package cn.nightmare9948.disccrafting.client.audio;

import cn.nightmare9948.disccrafting.DiscCrafting;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * 自己解码、自己送声卡的一路播放：支持暂停 / 继续、0~200% 音量、
 * 单曲循环，以及“播放到哪儿了 / 拖到哪儿”的进度控制。
 *
 * 播放跑在单独一条线程里，游戏主线程只改几个 volatile 字段，互不阻塞。
 */
public final class StreamPlayback implements Playback {
    /** 每路播放怎么造声音来源（循环、拖动之后要重开）。 */
    public interface SourceFactory {
        PcmSource create() throws Exception;
    }

    private final SourceFactory factory;
    private final long declaredDurationMs;

    /** 水中音效：截止频率压到这个值以下，听着就像隔着水（强度 100% 时）。 */
    private static final float MUFFLE_CUTOFF_HZ = 850.0f;
    /** 进出水的过渡时间常数（秒），实际听感约 0.2 秒就切完，不算拖。 */
    private static final float MUFFLE_TAU_SECONDS = 0.055f;
    /** 水底下再压一点总音量，闷感更明显：每 100% 强度压 20%。 */
    private static final float MUFFLE_VOLUME_STEP = 0.2f;
    /** 淡入时长（秒）。 */
    private static final float FADE_IN_SECONDS = 0.7f;
    /** 淡出时长（秒），比淡入短一点，停下时不拖泥带水。 */
    private static final float FADE_OUT_SECONDS = 0.4f;

    private volatile float gain = 1.0f;
    private volatile boolean paused;
    private volatile boolean stopped;
    private volatile boolean finished;
    private volatile boolean loop;
    private volatile boolean started;
    private volatile String error;
    private volatile boolean muffled;
    /** 淡入淡出开关（模组设置里那个），主线程写、播放线程读。 */
    private volatile boolean fadeEnabled = true;
    /** 暂停请求：先把声音淡出去，淡完再真正停住。 */
    private volatile boolean pauseRequested;
    /** 只在播放线程里改：当前这一块数据用的淡入淡出系数（0~1）。 */
    private float fadeVolume = 1.0f;
    /** 强度目标（0~3），主线程写、播放线程读。 */
    private volatile float muffleStrengthTarget = 1.0f;
    /** 只在播放线程里改：当前“闷”的程度，0 = 正常，1 = 完全水下。 */
    private float muffle;
    /** 只在播放线程里改：平滑之后的强度，拖滑块时不会突变。 */
    private float muffleStrength = 1.0f;
    /** 只在播放线程里改：当前这一块数据用的低通截止频率。 */
    private float cutoffHz = MUFFLE_CUTOFF_HZ;
    /** 一阶低通的状态，左右声道各一份（单声道时只用第 0 份）。 */
    private final float[] lowPass = new float[2];

    /** 待执行的拖动目标（帧），-1 表示没有。 */
    private volatile long seekFrame = -1;
    private volatile long framesPlayed;
    private volatile int sampleRate = 44100;
    private volatile long totalFrames = -1;

    private volatile SourceDataLine line;
    private Thread thread;

    /**
     * @param factory            造声音来源的方法
     * @param declaredDurationMs 已知时长（毫秒）；不知道传 0，让它自己算
     */
    public StreamPlayback(SourceFactory factory, long declaredDurationMs) {
        this.factory = factory;
        this.declaredDurationMs = Math.max(0, declaredDurationMs);
    }

    @Override
    public void start() {
        if (this.thread != null && this.thread.isAlive()) {
            this.paused = false;
            this.pauseRequested = false;
            this.stopped = false;
            if (this.fadeEnabled) {
                this.fadeVolume = 0.0f;
            }
            return;
        }
        this.started = true;
        this.paused = false;
        this.pauseRequested = false;
        this.stopped = false;
        this.finished = false;
        this.fadeVolume = this.fadeEnabled ? 0.0f : 1.0f;
        // 暂停时拖过进度条的，从头开始在拖到的位置播
        if (this.seekFrame < 0) {
            this.framesPlayed = 0;
        }
        spawnThread();
    }

    private void spawnThread() {
        Thread created = new Thread(this::run, "disc-crafting-audio");
        created.setDaemon(true);
        this.thread = created;
        created.start();
    }

    private void run() {
        PcmSource source = null;
        SourceDataLine opened = null;
        try {
            source = this.factory.create();
            AudioFormat format = source.format();
            this.sampleRate = Math.max(1, (int) format.getSampleRate());
            this.totalFrames = source.totalFrames();

            opened = AudioSystem.getSourceDataLine(format);
            opened.open(format, 32768);
            opened.start();
            this.line = opened;

            int frameSize = Math.max(1, format.getFrameSize());
            byte[] raw = new byte[8192];
            byte[] output = new byte[8192];
            this.lowPass[0] = 0.0f;
            this.lowPass[1] = 0.0f;
            this.fadeVolume = this.fadeEnabled ? 0.0f : 1.0f;
            while (true) {
                boolean stopping = this.stopped;
                // 停止 / 暂停都先淡出，淡完再真正停住，不会“啪”一下断掉
                if (stopping && (!this.fadeEnabled || this.paused || this.fadeVolume <= 0.002f)) {
                    break;
                }
                if (this.pauseRequested && (!this.fadeEnabled || this.fadeVolume <= 0.002f)) {
                    this.pauseRequested = false;
                    this.paused = true;
                    this.fadeVolume = this.fadeEnabled ? 0.0f : 1.0f;
                    if (opened.isRunning()) {
                        opened.stop();
                    }
                    continue;
                }
                if (this.paused) {
                    if (opened.isRunning()) {
                        opened.stop();
                    }
                    Thread.sleep(20L);
                    continue;
                }
                long target = this.seekFrame;
                if (target >= 0) {
                    this.seekFrame = -1;
                    source.seekToFrame(target);
                    this.framesPlayed = target;
                    this.lowPass[0] = 0.0f;
                    this.lowPass[1] = 0.0f;
                    opened.flush();
                }
                int read = source.read(raw);
                if (read <= 0) {
                    if (this.loop && !this.stopped) {
                        source.seekToFrame(0);
                        this.framesPlayed = 0;
                        opened.flush();
                        continue;
                    }
                    this.finished = true;
                    break;
                }
                if (!opened.isRunning()) {
                    opened.start();
                }
                stepMuffle(read, frameSize);
                stepFade(read, frameSize, (stopping || this.pauseRequested) ? 0.0f : 1.0f);
                process(raw, output, read, this.gain * this.fadeVolume);
                opened.write(output, 0, read);
                this.framesPlayed += read / frameSize;
            }
            if (!this.stopped) {
                opened.drain();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            if (!this.stopped) {
                this.error = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
                DiscCrafting.LOGGER.warn("音频播放失败", t);
            }
        } finally {
            this.finished = true;
            closeQuietly(opened);
            if (source != null) {
                try {
                    source.close();
                } catch (Throwable ignored) {
                    // 关不掉无所谓
                }
            }
        }
    }

    private static void closeQuietly(SourceDataLine line) {
        if (line == null) {
            return;
        }
        try {
            line.stop();
            line.close();
        } catch (Throwable ignored) {
            // 关不掉无所谓
        }
    }

    /**
     * 每块数据算一次“闷”的程度：目标是 0 或 1，用指数逼近走过去，
     * 时间常数很小，进出水大约 0.2 秒就过渡完，不会拖出一段奇怪的声音。
     */
    private void stepMuffle(int length, int frameSize) {
        int frames = Math.max(1, length / Math.max(1, frameSize));
        float seconds = frames / (float) Math.max(1, this.sampleRate);
        float alpha = 1.0f - (float) Math.exp(-seconds / MUFFLE_TAU_SECONDS);

        // 强度也一起平滑：玩家拖强度滑块时，闷感是渐变过去的
        float targetStrength = this.muffleStrengthTarget;
        if (this.muffleStrength != targetStrength) {
            this.muffleStrength += (targetStrength - this.muffleStrength) * alpha;
            if (Math.abs(targetStrength - this.muffleStrength) < 0.004f) {
                this.muffleStrength = targetStrength;
            }
        }
        // 截止频率跟着强度走：100% 时 850Hz，300% 时约 280Hz，越低越闷
        this.cutoffHz = MUFFLE_CUTOFF_HZ / Math.max(0.25f, this.muffleStrength);

        float target = this.muffled ? 1.0f : 0.0f;
        if (this.muffle == target) {
            return;
        }
        this.muffle += (target - this.muffle) * alpha;
        if (Math.abs(target - this.muffle) < 0.004f) {
            this.muffle = target;
        }
    }

    /**
     * 每块数据把淡入淡出系数往目标挪一点：
     * 开始 / 继续时从 0 爬到 1，暂停 / 停止时从 1 落回 0。
     */
    private void stepFade(int length, int frameSize, float target) {
        if (!this.fadeEnabled) {
            this.fadeVolume = 1.0f;
            return;
        }
        int frames = Math.max(1, length / Math.max(1, frameSize));
        float seconds = frames / (float) Math.max(1, this.sampleRate);
        boolean rising = target > this.fadeVolume;
        float span = rising ? FADE_IN_SECONDS : FADE_OUT_SECONDS;
        float step = seconds / Math.max(0.05f, span);
        if (rising) {
            this.fadeVolume = Math.min(target, this.fadeVolume + step);
        } else if (this.fadeVolume > target) {
            this.fadeVolume = Math.max(target, this.fadeVolume - step);
        }
    }

    /** 一阶低通的系数（按当前截止频率、采样率算）。 */
    private float lowPassCoefficient(float cutoff) {
        double cut = 2.0 * Math.PI * Math.max(80.0f, cutoff) / Math.max(1, this.sampleRate);
        return (float) (1.0 - Math.exp(-cut));
    }

    /**
     * 把一块 16 位 PCM 按音量和水中音效处理后写进 output。
     * 水里 = 低通滤掉高频（发闷）+ 按强度压一点音量，两者都随 muffle 平滑渐变。
     */
    private void process(byte[] source, byte[] output, int length, float gain) {
        float strength = Math.max(0.0f, this.muffleStrength);
        // 强度决定“滤多深”：0% 完全不处理，100% 是全闷，300% 更狠
        float depth = Math.min(1.0f, this.muffle * strength);
        float volumeTilt = 1.0f - Math.min(1.2f, MUFFLE_VOLUME_STEP * strength) * this.muffle;
        float total = gain * volumeTilt;
        if (depth <= 0.001f && Math.abs(total - 1.0f) < 0.001f) {
            System.arraycopy(source, 0, output, 0, length);
            return;
        }
        float alpha = depth > 0.001f ? lowPassCoefficient(this.cutoffHz) : 0.0f;
        int channel = 0;
        int i = 0;
        for (; i + 1 < length; i += 2) {
            int sample = (source[i + 1] << 8) | (source[i] & 0xFF);
            float value = sample;
            if (depth > 0.001f) {
                this.lowPass[channel] += alpha * (value - this.lowPass[channel]);
                value = value * (1.0f - depth) + this.lowPass[channel] * depth;
                channel ^= 1;
            }
            int scaled = (int) (value * total);
            if (scaled > 32767) {
                scaled = 32767;
            } else if (scaled < -32768) {
                scaled = -32768;
            }
            output[i] = (byte) (scaled & 0xFF);
            output[i + 1] = (byte) ((scaled >> 8) & 0xFF);
        }
        if (length % 2 != 0) {
            output[length - 1] = source[length - 1];
        }
    }
    @Override
    public void stop() {
        // 不在这里硬关声卡：播放线程写数据时会先淡出、再自己退出并把线路关掉，
        // 免得正在写的线程抛异常，把“停止”误报成播放失败。
        this.stopped = true;
        this.pauseRequested = false;
    }

    @Override
    public void pause() {
        if (this.fadeEnabled && !this.paused && !this.stopped
            && this.thread != null && this.thread.isAlive()) {
            // 交给播放线程：先淡出 0.4 秒，再真正停下来
            this.pauseRequested = true;
            return;
        }
        this.paused = true;
        this.pauseRequested = false;
    }

    @Override
    public void resume() {
        if (this.thread == null || !this.thread.isAlive()) {
            start();
            return;
        }
        boolean wasSilent = this.paused || this.pauseRequested;
        this.paused = false;
        this.pauseRequested = false;
        this.stopped = false;
        if (wasSilent && this.fadeEnabled) {
            this.fadeVolume = 0.0f;
        }
    }

    @Override
    public boolean isPaused() {
        return this.paused || this.pauseRequested;
    }

    @Override
    public boolean isFinished() {
        return this.finished;
    }

    @Override
    public boolean isActive() {
        return this.started && !this.stopped && !this.paused && !this.pauseRequested && !this.finished;
    }

    @Override
    public void setFadeEnabled(boolean enabled) {
        this.fadeEnabled = enabled;
        if (!enabled) {
            this.fadeVolume = 1.0f;
        }
    }

    @Override
    public boolean handlesDistance() {
        return false;
    }

    @Override
    public void setGain(float value) {
        this.gain = Math.max(0.0f, Math.min(2.0f, value));
    }

    @Override
    public void setMuffled(boolean value) {
        // 只改目标值，真正的过渡在播放线程里一点点做，主线程不阻塞
        this.muffled = value;
    }

    @Override
    public void setMuffleStrength(float strength) {
        this.muffleStrengthTarget = Math.max(0.0f, Math.min(3.0f, strength));
    }

    @Override
    public String error() {
        return this.error;
    }

    @Override
    public boolean canSeek() {
        return true;
    }

    @Override
    public long positionMs() {
        return this.framesPlayed * 1000L / Math.max(1, this.sampleRate);
    }

    @Override
    public long durationMs() {
        if (this.declaredDurationMs > 0) {
            return this.declaredDurationMs;
        }
        long frames = this.totalFrames;
        return frames > 0 ? frames * 1000L / Math.max(1, this.sampleRate) : 0;
    }

    @Override
    public void seekTo(long ms) {
        long frame = Math.max(0, ms) * Math.max(1, this.sampleRate) / 1000L;
        if (this.totalFrames > 0) {
            frame = Math.min(frame, this.totalFrames - 1);
        }
        this.framesPlayed = frame;
        this.seekFrame = frame;
        this.finished = false;
        if (!this.started) {
            // 还没开始放：先把位置记在 seekFrame 上，start() 之后再说
            return;
        }
        if (this.thread == null || !this.thread.isAlive()) {
            this.started = true;
            this.stopped = false;
            this.paused = false;
            spawnThread();
        }
    }

    @Override
    public boolean isLooping() {
        return this.loop;
    }

    @Override
    public void setLooping(boolean value) {
        this.loop = value;
    }
}
