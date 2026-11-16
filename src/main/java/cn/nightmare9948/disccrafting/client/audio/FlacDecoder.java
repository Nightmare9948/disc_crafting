package cn.nightmare9948.disccrafting.client.audio;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 自带的 FLAC 解码器（纯 Java，不依赖任何第三方库）。
 *
 * Java 自带的音频系统（javax.sound）不认 FLAC，所以以前把 .flac 拖进来会解码失败。
 * 这里按 FLAC 规范把文件读成 16 位 PCM，后面的“重采样 -> 存成 wav”流程和别的格式完全一样。
 *
 * 支持：1~8 声道、8/12/16/20/24/32 位、固定预测和 LPC 预测、立体声去相关
 * （左右 / 右左 / 中侧），以及两种 Rice 残差编码。校验和直接跳过 —— 音频数据本身
 * 有 CRC 保护的场合正常情况下不会损坏，跳过能省下大量计算。
 */
public final class FlacDecoder {
    private FlacDecoder() {
    }

    /** 解码结果：交错的 16 位 PCM 采样。 */
    public record Pcm(short[] samples, int sampleRate, int channels) {
    }

    private static final int MAX_BLOCK_SIZE = 65536;
    private static final int MAX_CHANNELS = 8;

    /** 文件开头是不是 "fLaC"。 */
    public static boolean probe(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            byte[] magic = new byte[4];
            if (input.read(magic) != 4) {
                return false;
            }
            return magic[0] == 'f' && magic[1] == 'L' && magic[2] == 'a' && magic[3] == 'C';
        } catch (Throwable t) {
            return false;
        }
    }

    /** 把整个文件解码成 16 位 PCM；格式不认识就抛异常，让上层把原因显示给玩家。 */
    public static Pcm decode(Path file) throws IOException {
        byte[] data;
        try (InputStream input = new BufferedInputStream(Files.newInputStream(file), 1 << 16)) {
            data = input.readAllBytes();
        }
        if (data.length < 8 || data[0] != 'f' || data[1] != 'L' || data[2] != 'a' || data[3] != 'C') {
            throw new IOException("不是有效的 FLAC 文件");
        }
        BitReader bits = new BitReader(data);
        bits.skipBytes(4);

        StreamInfo info = null;
        boolean last = false;
        while (!last) {
            int header = bits.readByte();
            last = (header & 0x80) != 0;
            int type = header & 0x7F;
            int length = bits.readBits(24);
            if (length < 0) {
                throw new IOException("FLAC 元数据损坏");
            }
            int start = bits.bytePosition();
            if (type == 0) {
                info = StreamInfo.parse(bits);
            }
            // 每块元数据都是字节对齐的，直接跳到下一块开头
            bits.seekByte(start + length);
            if (start + length > data.length) {
                throw new IOException("FLAC 元数据长度越界");
            }
        }
        if (info == null) {
            throw new IOException("FLAC 缺少 STREAMINFO");
        }
        if (info.sampleRate <= 0 || info.channels <= 0 || info.channels > MAX_CHANNELS) {
            throw new IOException("FLAC 参数不支持");
        }

        ShortSink sink = new ShortSink(info.totalSamples > 0
            ? (int) Math.min(Integer.MAX_VALUE, info.totalSamples * info.channels) : 4096);
        int frames = info.totalSamples > 0 ? (int) Math.min(Integer.MAX_VALUE, info.totalSamples) : -1;

        int decodedFrames = 0;
        while (frames < 0 || decodedFrames < frames) {
            if (!seekSync(bits)) {
                break;
            }
            Frame frame;
            try {
                frame = readFrame(bits, info);
            } catch (IOException damaged) {
                // 文件后半截坏了（下载没下完之类）：已经解出来的部分照样能听
                if (sink.size > 0) {
                    break;
                }
                throw damaged;
            }
            if (frame == null) {
                continue;
            }
            int count = frame.blockSize;
            if (frames < 0) {
                sink.write(frame.samples, count * info.channels);
                decodedFrames += count;
                continue;
            }
            int remaining = frames - decodedFrames;
            int take = Math.min(count, remaining);
            sink.write(frame.samples, take * info.channels);
            decodedFrames += take;
            if (take < count) {
                break;
            }
        }
        if (sink.size == 0) {
            throw new IOException("FLAC 里没有可用的音频数据");
        }
        short[] out = new short[sink.size];
        System.arraycopy(sink.data, 0, out, 0, sink.size);
        return new Pcm(out, info.sampleRate, info.channels);
    }

    // ------------------------------------------------------------- STREAMINFO

    private static final class StreamInfo {
        int sampleRate;
        int channels;
        int bitsPerSample;
        long totalSamples;

        static StreamInfo parse(BitReader bits) throws IOException {
            StreamInfo info = new StreamInfo();
            bits.readBits(16); // 最小块大小
            bits.readBits(16); // 最大块大小
            bits.readBits(24); // 最小帧大小
            bits.readBits(24); // 最大帧大小
            info.sampleRate = bits.readBits(20);
            info.channels = bits.readBits(3) + 1;
            info.bitsPerSample = bits.readBits(5) + 1;
            info.totalSamples = ((long) bits.readBits(4) << 32) | (bits.readBits(32) & 0xFFFFFFFFL);
            return info;
        }
    }

    // ------------------------------------------------------------- 帧头 / 子帧

    private static final class Frame {
        int blockSize;
        short[] samples;
    }

    /** 找到下一个帧同步字（0b11111111111110）；找不到就是文件尾巴，返回 false。 */
    private static boolean seekSync(BitReader bits) throws IOException {
        // 同步字按字节对齐出现，从当前位置往后按位搜，最多搜到文件结束
        while (bits.remainingBits() >= 15) {
            int position = bits.bitPosition();
            if (bits.readBits(15) == 0x7FFC) { // 14 位同步码 + 固定的保留位 0
                // 回到同步字开头，交给 readFrame 从 14 位的同步码重新读
                bits.seekBit(position);
                return true;
            }
            bits.seekBit(position + 1);
        }
        return false;
    }

    private static Frame readFrame(BitReader bits, StreamInfo info) throws IOException {
        int sync = bits.readBits(14);
        if (sync != 0x3FFE) {
            return null;
        }
        bits.readBits(1); // 保留位
        bits.readBits(1); // 块大小策略
        int blockSizeCode = bits.readBits(4);
        int sampleRateCode = bits.readBits(4);
        int channelAssignment = bits.readBits(4);
        int sampleSizeCode = bits.readBits(3);
        bits.readBits(1); // 保留位
        readFrameNumber(bits);

        int blockSize = switch (blockSizeCode) {
            case 1 -> 192;
            case 2, 3, 4, 5 -> 576 << (blockSizeCode - 2);
            case 6 -> bits.readBits(8) + 1;
            case 7 -> bits.readBits(16) + 1;
            default -> 256 << (blockSizeCode - 8);
        };
        if (blockSize <= 0 || blockSize > MAX_BLOCK_SIZE) {
            throw new IOException("FLAC 帧块大小异常");
        }
        skipSampleRateExtra(bits, sampleRateCode);
        bits.readBits(8); // 帧头 CRC-8

        int channels = channelAssignment <= 7 ? channelAssignment + 1 : 2;
        if (channels > MAX_CHANNELS) {
            throw new IOException("FLAC 声道数异常");
        }
        int bitsPerSample = sampleSizeFor(sampleSizeCode, info.bitsPerSample);
        int[][] subframes = new int[channels][];
        for (int channel = 0; channel < channels; channel++) {
            int channelBits = bitsPerSample + sideChannelExtra(channelAssignment, channel);
            subframes[channel] = readSubframe(bits, blockSize, channelBits);
        }
        bits.alignToByte();
        bits.skipBytes(2); // 帧尾 CRC-16

        short[] interleaved = new short[blockSize * channels];
        int[] left = null;
        int[] right = null;
        if (channelAssignment <= 7) {
            left = subframes[0];
            right = channels > 1 ? subframes[1] : null;
        } else {
            int[] first = subframes[0];
            int[] second = subframes[1];
            left = new int[blockSize];
            right = new int[blockSize];
            if (channelAssignment == 8) {
                // 左 / 侧：第一路是左声道，第二路是“左 - 右”
                for (int i = 0; i < blockSize; i++) {
                    left[i] = first[i];
                    right[i] = first[i] - second[i];
                }
            } else if (channelAssignment == 9) {
                // 侧 / 右：第一路是“左 - 右”，第二路是右声道
                for (int i = 0; i < blockSize; i++) {
                    right[i] = second[i];
                    left[i] = first[i] + second[i];
                }
            } else {
                // 中 / 侧：先用最低位还原出真假中声道，再对称拆回左右
                for (int i = 0; i < blockSize; i++) {
                    int mid = (first[i] << 1) | (second[i] & 1);
                    int side = second[i];
                    left[i] = (mid + side) >> 1;
                    right[i] = (mid - side) >> 1;
                }
            }
        }
        int shift = bitsPerSample - 16;
        for (int i = 0; i < blockSize; i++) {
            interleaved[i * channels] = toShort(left[i], shift);
            if (channels > 1) {
                interleaved[i * channels + 1] = toShort(right[i], shift);
            }
            for (int channel = 2; channel < channels; channel++) {
                interleaved[i * channels + channel] = toShort(subframes[channel][i], shift);
            }
        }
        Frame frame = new Frame();
        frame.blockSize = blockSize;
        frame.samples = interleaved;
        return frame;
    }

    private static short toShort(int value, int shift) {
        if (shift > 0) {
            value >>= shift;
        } else if (shift < 0) {
            value <<= -shift;
        }
        if (value > 32767) {
            value = 32767;
        } else if (value < -32768) {
            value = -32768;
        }
        return (short) value;
    }

    private static int sampleSizeFor(int code, int streamInfoBits) {
        return switch (code) {
            case 1 -> 8;
            case 2 -> 12;
            case 4 -> 16;
            case 5 -> 20;
            case 6 -> 24;
            case 7 -> 32;
            default -> streamInfoBits;
        };
    }

    private static int sideChannelExtra(int channelAssignment, int channel) {
        if (channel == 0) {
            return 0;
        }
        return switch (channelAssignment) {
            // 左右 / 右左 / 中侧这几种“侧”声道比别的声道多一位
            case 8, 9, 10 -> 1;
            default -> 0;
        };
    }

    private static void skipSampleRateExtra(BitReader bits, int code) throws IOException {
        if (code == 12) {
            bits.readBits(8);
        } else if (code == 13 || code == 14) {
            bits.readBits(16);
        } else if (code == 15) {
            throw new IOException("FLAC 帧头采样率非法");
        }
    }

    /** 帧序号是 UTF-8 那种变长整数，只用来定位，读完丢掉。 */
    private static void readFrameNumber(BitReader bits) throws IOException {
        int first = bits.readBits(8);
        int extra = 0;
        int mask = 0x80;
        while (extra < 6 && (first & mask) != 0) {
            extra++;
            mask >>= 1;
        }
        for (int i = 0; i < extra; i++) {
            bits.readBits(8);
        }
    }

    private static int[] readSubframe(BitReader bits, int blockSize, int bitsPerSample) throws IOException {
        if (bits.readBits(1) != 0) {
            throw new IOException("FLAC 子帧头损坏");
        }
        int type = bits.readBits(6);
        boolean wastedFlag = bits.readBits(1) == 1;
        int wasted = 0;
        if (wastedFlag) {
            wasted = (int) Math.min(31L, bits.readUnary() + 1L);
            bitsPerSample -= wasted;
        }
        if (bitsPerSample <= 0) {
            throw new IOException("FLAC 子帧位深异常");
        }
        int[] samples;
        if (type == 0) {
            int value = bits.readSigned(bitsPerSample);
            samples = new int[blockSize];
            java.util.Arrays.fill(samples, value);
        } else if (type == 1) {
            samples = new int[blockSize];
            for (int i = 0; i < blockSize; i++) {
                samples[i] = bits.readSigned(bitsPerSample);
            }
        } else if (type >= 8 && type <= 12) {
            samples = readFixed(bits, blockSize, bitsPerSample, type - 8);
        } else if (type >= 32) {
            samples = readLpc(bits, blockSize, bitsPerSample, type - 31);
        } else {
            throw new IOException("FLAC 子帧类型不支持：" + type);
        }
        if (wasted > 0) {
            for (int i = 0; i < samples.length; i++) {
                samples[i] <<= wasted;
            }
        }
        return samples;
    }

    private static int[] readFixed(BitReader bits, int blockSize, int bitsPerSample, int order)
        throws IOException {
        if (order > 4 || order > blockSize) {
            throw new IOException("FLAC 固定预测阶数异常");
        }
        int[] samples = new int[blockSize];
        for (int i = 0; i < order; i++) {
            samples[i] = bits.readSigned(bitsPerSample);
        }
        int[] residual = readResidual(bits, blockSize, order);
        for (int i = order; i < blockSize; i++) {
            int predicted = switch (order) {
                case 0 -> 0;
                case 1 -> samples[i - 1];
                case 2 -> 2 * samples[i - 1] - samples[i - 2];
                case 3 -> 3 * samples[i - 1] - 3 * samples[i - 2] + samples[i - 3];
                default -> 4 * samples[i - 1] - 6 * samples[i - 2] + 4 * samples[i - 3] - samples[i - 4];
            };
            samples[i] = predicted + residual[i - order];
        }
        return samples;
    }

    private static int[] readLpc(BitReader bits, int blockSize, int bitsPerSample, int order)
        throws IOException {
        if (order <= 0 || order > 32 || order > blockSize) {
            throw new IOException("FLAC LPC 阶数异常");
        }
        int[] samples = new int[blockSize];
        for (int i = 0; i < order; i++) {
            samples[i] = bits.readSigned(bitsPerSample);
        }
        int precision = bits.readBits(4) + 1;
        if (precision == 16) {
            throw new IOException("FLAC LPC 系数精度非法");
        }
        int shift = bits.readSigned(5);
        int[] coefficients = new int[order];
        for (int i = 0; i < order; i++) {
            coefficients[i] = bits.readSigned(precision);
        }
        int[] residual = readResidual(bits, blockSize, order);
        for (int i = order; i < blockSize; i++) {
            long sum = 0;
            for (int j = 0; j < order; j++) {
                sum += (long) coefficients[j] * samples[i - 1 - j];
            }
            int predicted = shift >= 0 ? (int) (sum >> shift) : (int) (sum << -shift);
            samples[i] = predicted + residual[i - order];
        }
        return samples;
    }

    /** 残差：Rice / Rice2 分区编码，返回长度 blockSize - order 的数组。 */
    private static int[] readResidual(BitReader bits, int blockSize, int order) throws IOException {
        int method = bits.readBits(2);
        if (method > 1) {
            throw new IOException("FLAC 残差编码方式不支持");
        }
        int paramBits = method == 0 ? 4 : 5;
        int escape = (1 << paramBits) - 1;
        int partitionOrder = bits.readBits(4);
        if (partitionOrder > 8) {
            throw new IOException("FLAC 残差分区过多");
        }
        int partitions = 1 << partitionOrder;
        int partitionSize = blockSize >> partitionOrder;
        if (partitionSize <= 0 || (blockSize & (partitions - 1)) != 0) {
            throw new IOException("FLAC 残差分区大小异常");
        }
        int[] residual = new int[Math.max(0, blockSize - order)];
        int index = 0;
        for (int partition = 0; partition < partitions; partition++) {
            int count = partitionSize - (partition == 0 ? order : 0);
            int parameter = bits.readBits(paramBits);
            if (parameter == escape) {
                int rawBits = bits.readBits(5);
                for (int i = 0; i < count; i++) {
                    if (index >= residual.length) {
                        break;
                    }
                    residual[index++] = rawBits == 0 ? 0 : bits.readSigned(rawBits);
                }
            } else {
                for (int i = 0; i < count; i++) {
                    if (index >= residual.length) {
                        break;
                    }
                    long quotient = bits.readUnary();
                    long remainder = parameter == 0 ? 0 : bits.readBits(parameter);
                    long value = (quotient << parameter) | remainder;
                    // Rice 编码里存的是 zigzag 值：0, -1, 1, -2, 2 ...
                    residual[index++] = (int) ((value >> 1) ^ -(value & 1));
                }
            }
        }
        return residual;
    }

    // ------------------------------------------------------------- 底层读取

    private static final class BitReader {
        private final byte[] data;
        private long position;

        BitReader(byte[] data) {
            this.data = data;
        }

        int bitPosition() {
            return (int) this.position;
        }

        void seekBit(int bit) {
            this.position = Math.max(0, Math.min(this.data.length * 8L, bit));
        }

        int bytePosition() {
            return (int) (this.position / 8);
        }

        void seekByte(int bytePosition) {
            seekBit(bytePosition * 8);
        }

        void skipBytes(int count) {
            this.position += count * 8L;
            clamp();
        }

        void alignToByte() {
            this.position = (this.position + 7) / 8 * 8;
        }

        int remainingBits() {
            return (int) Math.max(0, this.data.length * 8L - this.position);
        }

        private void clamp() {
            long max = this.data.length * 8L;
            if (this.position > max) {
                this.position = max;
            }
        }

        int readByte() throws IOException {
            return readBits(8);
        }

        int readBits(int count) throws IOException {
            if (count == 0) {
                return 0;
            }
            if (count < 0 || count > 32) {
                throw new IOException("FLAC 位读取长度异常");
            }
            if (remainingBits() < count) {
                throw new IOException("FLAC 数据意外结束");
            }
            long value = 0;
            for (int i = 0; i < count; i++) {
                int index = (int) (this.position >> 3);
                int bit = (this.data[index] >> (7 - (int) (this.position & 7))) & 1;
                value = (value << 1) | bit;
                this.position++;
            }
            return (int) value;
        }

        int readSigned(int count) throws IOException {
            if (count <= 0 || count > 32) {
                throw new IOException("FLAC 有符号读取长度异常");
            }
            int value = readBits(count);
            int shift = 32 - count;
            return (value << shift) >> shift;
        }

        /** 一元编码：数 0 的个数，读到 1 为止。 */
        long readUnary() throws IOException {
            long count = 0;
            while (true) {
                if (remainingBits() < 1) {
                    throw new IOException("FLAC 一元编码意外结束");
                }
                if (readBits(1) == 1) {
                    return count;
                }
                count++;
                if (count > 1 << 20) {
                    throw new IOException("FLAC 一元编码过长");
                }
            }
        }
    }

    /** 按需变长的 short 缓冲：总采样数未知（流式 FLAC）时也能解。 */
    private static final class ShortSink {
        short[] data;
        int size;

        ShortSink(int capacity) {
            this.data = new short[Math.max(16, Math.min(capacity, 1 << 24))];
        }

        void write(short[] values, int count) {
            ensure(this.size + count);
            System.arraycopy(values, 0, this.data, this.size, count);
            this.size += count;
        }

        private void ensure(int capacity) {
            if (capacity <= this.data.length) {
                return;
            }
            int next = this.data.length;
            while (next < capacity) {
                next = next + (next >> 1) + 16;
            }
            this.data = java.util.Arrays.copyOf(this.data, Math.min(next, Integer.MAX_VALUE - 8));
        }
    }
}
