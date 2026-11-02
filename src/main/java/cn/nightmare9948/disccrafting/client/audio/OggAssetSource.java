package cn.nightmare9948.disccrafting.client.audio;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * 原版唱片自带的 ogg 音频：直接从游戏资源里读出来解码。
 *
 * 以前原版唱片是交给游戏声音系统放的，好处是不用管解码，
 * 坏处是没有进度、不能拖动。现在统一自己解码，进度条和拖动对原版唱片也能用。
 */
public final class OggAssetSource implements PcmSource {
    private final ByteBuffer data;
    private final long handle;
    private final int channels;
    private final int sampleRate;
    private final AudioFormat format;
    private final long totalFrames;
    private short[] samples = new short[0];

    public OggAssetSource(InputStream input) throws Exception {
        this.data = readAll(input);
        IntBuffer error = BufferUtils.createIntBuffer(1);
        this.handle = STBVorbis.stb_vorbis_open_memory(this.data, error, null);
        if (this.handle == 0L) {
            throw new IllegalStateException("ogg 打不开（错误码 " + error.get(0) + "）");
        }
        STBVorbisInfo info = STBVorbisInfo.malloc();
        try {
            STBVorbis.stb_vorbis_get_info(this.handle, info);
            this.channels = Math.max(1, info.channels());
            this.sampleRate = info.sample_rate();
        } finally {
            info.free();
        }
        this.format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, this.sampleRate, 16,
            this.channels, this.channels * 2, this.sampleRate, false);
        int frames = STBVorbis.stb_vorbis_stream_length_in_samples(this.handle);
        this.totalFrames = frames > 0 ? frames : -1;
    }

    /** 先试着开一次，确认这段 ogg 真能解码（失败就让上层退回声音系统播放）。 */
    public static boolean probe(byte[] data) {
        if (data == null || data.length == 0) {
            return false;
        }
        ByteBuffer buffer = BufferUtils.createByteBuffer(data.length);
        buffer.put(data);
        buffer.flip();
        IntBuffer error = BufferUtils.createIntBuffer(1);
        long handle = STBVorbis.stb_vorbis_open_memory(buffer, error, null);
        if (handle == 0L) {
            return false;
        }
        STBVorbis.stb_vorbis_close(handle);
        return true;
    }

    private static ByteBuffer readAll(InputStream input) throws Exception {
        try (InputStream in = input) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            byte[] bytes = out.toByteArray();
            ByteBuffer direct = BufferUtils.createByteBuffer(bytes.length);
            direct.put(bytes);
            direct.flip();
            return direct;
        }
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
    public int read(byte[] buffer) {
        int frameSize = this.channels * 2;
        int framesWanted = Math.max(1, buffer.length / frameSize);
        int needed = framesWanted * this.channels;
        if (this.samples.length < needed) {
            this.samples = new short[needed];
        }
        int frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(this.handle, this.channels, this.samples);
        if (frames <= 0) {
            return -1;
        }
        int samples = frames * this.channels;
        for (int i = 0; i < samples; i++) {
            short value = this.samples[i];
            buffer[i * 2] = (byte) (value & 0xFF);
            buffer[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
        }
        return samples * 2;
    }

    @Override
    public void seekToFrame(long frame) {
        int target = (int) Math.max(0, Math.min(frame, Integer.MAX_VALUE - 1L));
        STBVorbis.stb_vorbis_seek(this.handle, target);
    }

    @Override
    public void close() {
        STBVorbis.stb_vorbis_close(this.handle);
    }
}
