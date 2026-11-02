package cn.nightmare9948.disccrafting.client.audio;

/**
 * 一路正在播放的声音。
 * 实现有两个：{@link StreamPlayback}（我们自己解码的音频，进度、拖动、循环都能做）
 * 和 {@link VanillaRecordPlayback}（系统声音系统的退路，只在实在读不到音频文件时用）。
 */
public interface Playback {
    void start();

    void stop();

    void pause();

    void resume();

    boolean isPaused();

    /** 放完了（自然结束或被停掉）。 */
    boolean isFinished();

    /** 真的在出声：没暂停、没结束。 */
    boolean isActive();

    /** 距离衰减是不是已经由游戏的声音系统处理了。 */
    boolean handlesDistance();

    void setGain(float gain);

    /**
     * 水中音效开关：true 时把声音压低、闷住（像隔着水听），false 恢复正常。
     * 实现要自己把过渡做平滑，别让玩家听见“啪”的一下。
     */
    void setMuffled(boolean muffled);

    /**
     * 水中音效的强度：0 = 完全不闷，1 = 默认闷感，3 = 最强（界面上调到的 300%）。
     * 过渡同样要平滑，玩家拖着强度滑块时不该听见“啪”的一下。
     */
    void setMuffleStrength(float strength);

    String error();

    // ------------------------------------------------------------ 进度条用

    /** 能不能拖动进度。 */
    boolean canSeek();

    /** 当前播放到第几毫秒。 */
    long positionMs();

    /** 这首一共多长（毫秒）；不知道就是 0。 */
    long durationMs();

    /** 跳到指定位置（毫秒）。 */
    void seekTo(long ms);

    /** 单曲循环开关。 */
    boolean isLooping();

    void setLooping(boolean loop);
}
