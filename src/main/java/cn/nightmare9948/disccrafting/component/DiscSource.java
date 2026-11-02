package cn.nightmare9948.disccrafting.component;

/**
 * 一张唱片能放什么。
 * 自制唱片给 trackId（玩家自己导入的本地音频），原版唱片给 songId（原版曲目编号），
 * 两者只有一个非空。
 */
public record DiscSource(String trackId, String songId) {
    public static final DiscSource NONE = new DiscSource("", "");

    public boolean isEmpty() {
        return blank(trackId) && blank(songId);
    }

    /** 是不是原版唱片（交给游戏自己的唱片音频播放）。 */
    public boolean isVanilla() {
        return !blank(songId);
    }

    private static boolean blank(String value) {
        return value == null || value.isEmpty();
    }
}
