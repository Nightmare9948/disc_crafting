package cn.nightmare9948.disccrafting.client.audio;

/** 已导入到本地曲库的一首音频。 */
public final class Track {
    public final String id;
    public final String title;
    public final int icon;
    public final int durationMs;
    public final String file;

    public Track(String id, String title, int icon, int durationMs, String file) {
        this.id = id;
        this.title = title;
        this.icon = icon;
        this.durationMs = durationMs;
        this.file = file;
    }

    public String lengthText() {
        int totalSeconds = Math.max(0, durationMs / 1000);
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}
