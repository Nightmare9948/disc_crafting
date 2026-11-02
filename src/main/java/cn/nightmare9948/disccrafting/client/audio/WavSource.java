package cn.nightmare9948.disccrafting.client.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.nio.file.Path;

/** 本地 wav（玩家导入时已经统一转成 16 位 PCM）当作声音来源。 */
public final class WavSource implements PcmSource {
    private final Path file;
    private AudioInputStream stream;
    private AudioFormat format;
    private long totalFrames = -1;
    private int frameSize = 2;

    public WavSource(Path file) throws Exception {
        this.file = file;
        open();
    }

    private void open() throws Exception {
        AudioInputStream input = AudioSystem.getAudioInputStream(this.file.toFile());
        AudioFormat base = input.getFormat();
        int channels = Math.max(1, base.getChannels());
        if (base.getEncoding() != AudioFormat.Encoding.PCM_SIGNED || base.getSampleSizeInBits() != 16) {
            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                base.getSampleRate(), 16, channels, channels * 2, base.getSampleRate(), false);
            AudioInputStream converted = AudioSystem.getAudioInputStream(target, input);
            input.close();
            input = converted;
        }
        this.stream = input;
        this.format = input.getFormat();
        this.frameSize = Math.max(1, this.format.getFrameSize());
        long frames = input.getFrameLength();
        this.totalFrames = frames > 0 ? frames : -1;
    }

    @Override
    public AudioFormat format() {
        return this.format;
    }

    @Override
    public long totalFrames() {
        return this.totalFrames;
    }

    @Override
    public int read(byte[] buffer) throws Exception {
        int read = this.stream.read(buffer);
        return read <= 0 ? -1 : read;
    }

    @Override
    public void seekToFrame(long frame) throws Exception {
        long target = Math.max(0, frame);
        long bytesToSkip = target * this.frameSize;
        if (bytesToSkip < 0) {
            bytesToSkip = 0;
        }
        closeStream();
        open();
        byte[] scratch = new byte[8192];
        long skipped = 0;
        while (skipped < bytesToSkip) {
            int want = (int) Math.min(scratch.length, bytesToSkip - skipped);
            int read = this.stream.read(scratch, 0, want);
            if (read <= 0) {
                break;
            }
            skipped += read;
        }
    }

    private void closeStream() {
        if (this.stream != null) {
            try {
                this.stream.close();
            } catch (IOException ignored) {
                // 关不掉也无所谓
            }
            this.stream = null;
        }
    }

    @Override
    public void close() {
        closeStream();
    }
}
