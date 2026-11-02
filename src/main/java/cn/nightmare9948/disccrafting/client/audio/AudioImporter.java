package cn.nightmare9948.disccrafting.client.audio;

import cn.nightmare9948.disccrafting.DiscCrafting;
import cn.nightmare9948.disccrafting.client.Settings;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 音频导入：把 MP3 / WAV / OGG 等文件解码成统一的 16 位 PCM，
 * 按玩家选的音质档位重采样后写成 wav 存进本地曲库。
 */
public final class AudioImporter {
    private AudioImporter() {
    }

    public static final String[] SUPPORTED_EXTENSIONS = {".mp3", ".wav", ".ogg", ".aiff", ".aif", ".au", ".flac"};

    public static boolean looksLikeAudio(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /** 解码 -> 重采样 -> 写 wav -> 登记进曲库。 */
    public static Track importFile(Path source) throws Exception {
        Settings.load();
        Pcm pcm = decode(source);
        if (pcm == null || pcm.samples.length == 0) {
            throw new IllegalArgumentException("无法解析这个音频文件");
        }
        int[] target = switch (Settings.quality()) {
            case 0 -> new int[]{22050, 1};
            case 2 -> new int[]{44100, 2};
            default -> new int[]{44100, 1};
        };
        short[] converted = resample(pcm.samples, pcm.sampleRate, pcm.channels, target[0], target[1]);
        Path wav = TrackStore.tempFile();
        writeWav(wav, converted, target[0], target[1]);
        int frameCount = converted.length / target[1];
        int durationMs = (int) Math.min(Integer.MAX_VALUE, (long) frameCount * 1000L / target[0]);
        String title = source.getFileName().toString();
        int dot = title.lastIndexOf('.');
        if (dot > 0) {
            title = title.substring(0, dot);
        }
        return TrackStore.add(wav, title, durationMs);
    }

    private record Pcm(short[] samples, int sampleRate, int channels) {
    }

    private static Pcm decode(Path source) throws Exception {
        String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".ogg")) {
            Pcm pcm = decodeOgg(source);
            if (pcm != null) {
                return pcm;
            }
        }
        if (name.endsWith(".mp3")) {
            try {
                Pcm pcm = decodeMp3(source);
                if (pcm != null) {
                    return pcm;
                }
            } catch (Throwable t) {
                DiscCrafting.LOGGER.warn("MP3 解码失败，改用系统解码器", t);
            }
        }
        return decodeWithAudioSystem(source);
    }

    private static Pcm decodeOgg(Path source) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer channels = stack.mallocInt(1);
            IntBuffer sampleRate = stack.mallocInt(1);
            ShortBuffer buffer = STBVorbis.stb_vorbis_decode_filename(source.toString(), channels, sampleRate);
            if (buffer == null) {
                return null;
            }
            short[] samples = new short[buffer.remaining()];
            buffer.get(samples);
            return new Pcm(samples, sampleRate.get(0), channels.get(0));
        } catch (Throwable t) {
            DiscCrafting.LOGGER.warn("OGG 解码失败", t);
            return null;
        }
    }

    private static Pcm decodeMp3(Path source) throws Exception {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(source))) {
            javazoom.jl.decoder.Bitstream bitstream = new javazoom.jl.decoder.Bitstream(input);
            javazoom.jl.decoder.Decoder decoder = new javazoom.jl.decoder.Decoder();
            List<short[]> chunks = new ArrayList<>();
            int total = 0;
            int sampleRate = 44100;
            int channels = 2;
            javazoom.jl.decoder.Header header;
            while ((header = bitstream.readFrame()) != null) {
                javazoom.jl.decoder.SampleBuffer output =
                    (javazoom.jl.decoder.SampleBuffer) decoder.decodeFrame(header, bitstream);
                if (output != null) {
                    sampleRate = output.getSampleFrequency();
                    channels = Math.max(1, output.getChannelCount());
                    int length = output.getBufferLength();
                    short[] chunk = new short[length];
                    System.arraycopy(output.getBuffer(), 0, chunk, 0, length);
                    chunks.add(chunk);
                    total += length;
                }
                bitstream.closeFrame();
            }
            bitstream.close();
            if (total == 0) {
                return null;
            }
            short[] samples = new short[total];
            int offset = 0;
            for (short[] chunk : chunks) {
                System.arraycopy(chunk, 0, samples, offset, chunk.length);
                offset += chunk.length;
            }
            return new Pcm(samples, sampleRate, channels);
        }
    }

    private static Pcm decodeWithAudioSystem(Path source) throws Exception {
        try (AudioInputStream input = AudioSystem.getAudioInputStream(source.toFile())) {
            AudioFormat base = input.getFormat();
            AudioFormat target = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                base.getSampleRate(), 16,
                Math.max(1, base.getChannels()),
                Math.max(1, base.getChannels()) * 2,
                base.getSampleRate(), false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(target, input)) {
                byte[] bytes = readAll(pcm);
                short[] samples = new short[bytes.length / 2];
                for (int i = 0; i < samples.length; i++) {
                    samples[i] = (short) ((bytes[i * 2] & 0xFF) | (bytes[i * 2 + 1] << 8));
                }
                return new Pcm(samples, (int) target.getSampleRate(), target.getChannels());
            }
        }
    }

    private static byte[] readAll(AudioInputStream stream) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int read;
        while ((read = stream.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    /** 线性插值重采样 + 声道数调整（立体声混合成单声道 / 单声道复制成双声道）。 */
    private static short[] resample(short[] input, int sourceRate, int sourceChannels, int targetRate, int targetChannels) {
        int sourceFrames = input.length / sourceChannels;
        if (sourceFrames <= 0) {
            return new short[0];
        }
        int targetFrames = (int) Math.max(1L, Math.round((double) sourceFrames * targetRate / sourceRate));
        short[] output = new short[targetFrames * targetChannels];
        for (int frame = 0; frame < targetFrames; frame++) {
            double position = (double) frame * sourceRate / targetRate;
            int index0 = (int) Math.floor(position);
            int index1 = Math.min(index0 + 1, sourceFrames - 1);
            double frac = position - index0;
            for (int channel = 0; channel < targetChannels; channel++) {
                double sample0 = sampleAt(input, sourceFrames, sourceChannels, index0, channel, targetChannels);
                double sample1 = sampleAt(input, sourceFrames, sourceChannels, index1, channel, targetChannels);
                int value = (int) Math.round(sample0 + (sample1 - sample0) * frac);
                output[frame * targetChannels + channel] = (short) Math.max(-32768, Math.min(32767, value));
            }
        }
        return output;
    }

    private static double sampleAt(short[] input, int frames, int channels, int frame, int targetChannel, int targetChannels) {
        int safeFrame = Math.max(0, Math.min(frame, frames - 1));
        if (channels == targetChannels) {
            return input[safeFrame * channels + targetChannel];
        }
        if (channels == 1) {
            return input[safeFrame];
        }
        if (targetChannels == 1) {
            long sum = 0;
            for (int channel = 0; channel < channels; channel++) {
                sum += input[safeFrame * channels + channel];
            }
            return (double) sum / channels;
        }
        return input[safeFrame * channels + Math.min(targetChannel, channels - 1)];
    }

    private static void writeWav(Path target, short[] samples, int sampleRate, int channels) throws Exception {
        AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
        byte[] bytes = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            bytes[i * 2] = (byte) (samples[i] & 0xFF);
            bytes[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
        }
        try (AudioInputStream stream = new AudioInputStream(
            new ByteArrayInputStream(bytes), format, samples.length / channels)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, target.toFile());
        }
    }

    /** 未使用但保留：把宽字节缓冲转成 short[]。 */
    private static short[] toShorts(ByteBuffer buffer) {
        short[] samples = new short[buffer.remaining() / 2];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = buffer.getShort(i * 2);
        }
        return samples;
    }
}
