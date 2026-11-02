package cn.nightmare9948.disccrafting.client.audio;

import javax.sound.sampled.AudioFormat;

/**
 * 一段可以直接送声卡的声音（16 位小端 PCM）。
 * 两个来源：玩家导入的 wav 文件、原版唱片里自带的 ogg。
 * 每路播放自己开一个，用完 close，别跨线程共享。
 */
public interface PcmSource extends AutoCloseable {
    AudioFormat format();

    /** 一共多少帧；不知道返回 -1。 */
    long totalFrames();

    /** 读下一块 16 位小端 PCM，返回字节数；-1 表示放完了。 */
    int read(byte[] buffer) throws Exception;

    /** 跳到第 frame 帧（从 0 开始）。 */
    void seekToFrame(long frame) throws Exception;

    @Override
    void close();
}
