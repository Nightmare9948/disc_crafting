package cn.nightmare9948.disccrafting.client.audio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 一台随身听的播放序列（播放列表）。
 *
 * 序列里存的是本地曲库（唱片工坊导入的那些音频）的编号，三种播放顺序：
 * 随机播放、顺序播放（放完最后一首就停）、顺序循环（放完回到第一首）。
 * 序列只活在这台电脑上 —— 音频文件本来就在玩家自己的硬盘里。
 */
public final class Playlist {
    /** 列表随机播放 / 列表顺序播放 / 列表顺序循环播放。 */
    public enum Order {
        SHUFFLE("shuffle"),
        SEQUENCE("sequence"),
        LOOP("loop");

        private final String key;

        Order(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public static Order byKey(String value) {
            for (Order order : values()) {
                if (order.key.equalsIgnoreCase(value)) {
                    return order;
                }
            }
            return SEQUENCE;
        }
    }

    private final List<String> trackIds = new ArrayList<>();
    private boolean enabled;
    private Order order = Order.SEQUENCE;
    private int index = -1;
    /** 顺序播放走到最后一首之后置上；再点播放就从头开始。 */
    private boolean ended;
    /**
     * 最近真的开始放的那一首。
     * 随机播放靠它兜底：只按 index 排除还不够（比如刚放的单张唱片刚好是序列第 0 首），
     * 有了这一首的编号，下一首就绝不会和正在放的是同一首。
     */
    private String playing;

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    public Order order() {
        return this.order;
    }

    public void setOrder(Order value) {
        this.order = value == null ? Order.SEQUENCE : value;
        this.ended = false;
    }

    /** 点一下按钮换下一种顺序，返回换成了哪种。 */
    public Order cycleOrder() {
        Order[] values = Order.values();
        setOrder(values[(this.order.ordinal() + 1) % values.length]);
        return this.order;
    }

    public List<String> trackIds() {
        return Collections.unmodifiableList(this.trackIds);
    }

    public int size() {
        return this.trackIds.size();
    }

    public boolean isEmpty() {
        return this.trackIds.isEmpty();
    }

    public boolean contains(String trackId) {
        return trackId != null && this.trackIds.contains(trackId);
    }

    public String trackAt(int position) {
        return position >= 0 && position < this.trackIds.size() ? this.trackIds.get(position) : null;
    }

    /** 正在放第几首（从 0 开始）；没有就是 -1。 */
    public int index() {
        return this.index;
    }

    /** 当前这首的编号；没有（空序列或已经放完）就是 null。 */
    public String current() {
        if (this.ended) {
            return null;
        }
        return trackAt(this.index);
    }

    /** 从头开始：播放序列时用。 */
    public void reset() {
        reset(null);
    }

    /**
     * 从头开始，并且尽量避开 {@code avoidTrackId} 这一首。
     * 随机播放时用来保证“下一首”不会又放回刚才那一首。
     */
    public void reset(String avoidTrackId) {
        this.ended = false;
        if (this.trackIds.isEmpty()) {
            this.index = -1;
            return;
        }
        this.index = 0;
        if (this.order == Order.SHUFFLE) {
            this.index = randomIndex(-1, avoidTrackId);
        }
    }

    /** 这一首开始放了：记下来，随机挑下一首时排掉它。 */
    public void markPlaying(String trackId) {
        this.playing = trackId;
    }

    public String playingTrack() {
        return this.playing;
    }

    /** 把整条序列（含开关、顺序、进度）复制一份，给放置后的随身听继承用。 */
    public Playlist copy() {
        Playlist copy = new Playlist();
        copy.enabled = this.enabled;
        copy.order = this.order;
        copy.index = this.index;
        copy.ended = this.ended;
        copy.playing = this.playing;
        copy.trackIds.addAll(this.trackIds);
        return copy;
    }

    /**
     * 随机挑一个下标，避开 {@code excludeIndex} 和 {@code excludeTrack}。
     * 只有一首没得挑，就返回它自己。
     */
    private int randomIndex(int excludeIndex, String excludeTrack) {
        int size = this.trackIds.size();
        if (size <= 1) {
            return size == 1 ? 0 : -1;
        }
        // 先随机抽，抽到正在放的这首就再抽（最多 64 次，够用）
        for (int attempt = 0; attempt < 64; attempt++) {
            int candidate = ThreadLocalRandom.current().nextInt(size);
            if (candidate == excludeIndex) {
                continue;
            }
            String track = this.trackIds.get(candidate);
            if (excludeTrack != null && excludeTrack.equals(track)) {
                continue;
            }
            return candidate;
        }
        // 兜底：按顺序找一个不同的，保证一定不会重复正在放的那首
        for (int offset = 1; offset < size; offset++) {
            int candidate = excludeIndex < 0 ? offset : (excludeIndex + offset) % size;
            if (candidate == excludeIndex) {
                continue;
            }
            String track = this.trackIds.get(candidate);
            if (excludeTrack != null && excludeTrack.equals(track)) {
                continue;
            }
            return candidate;
        }
        return excludeIndex < 0 ? 0 : excludeIndex;
    }

    public void setIndex(int value) {
        this.index = value >= 0 && value < this.trackIds.size() ? value : -1;
        this.ended = false;
    }

    /** 加一首；已经在序列里就不重复加。返回是不是真加了。 */
    public boolean add(String trackId) {
        if (trackId == null || trackId.isEmpty() || this.trackIds.contains(trackId)) {
            return false;
        }
        this.trackIds.add(trackId);
        if (this.index < 0) {
            this.index = 0;
        }
        this.ended = false;
        return true;
    }

    public boolean removeAt(int position) {
        if (position < 0 || position >= this.trackIds.size()) {
            return false;
        }
        this.trackIds.remove(position);
        if (this.trackIds.isEmpty()) {
            this.index = -1;
        } else if (position < this.index) {
            this.index--;
        } else if (position == this.index && this.index >= this.trackIds.size()) {
            this.index = this.trackIds.size() - 1;
        }
        this.ended = false;
        return true;
    }

    public void clear() {
        this.trackIds.clear();
        this.index = -1;
        this.ended = false;
    }

    /**
     * 换到下一首。顺序播放走到头就返回 false（并把 ended 置上），
     * 随机播放永远不会停，循环播放绕回第一首。
     */
    public boolean advance() {
        if (this.trackIds.isEmpty() || this.ended) {
            return false;
        }
        switch (this.order) {
            case SHUFFLE -> {
                if (this.trackIds.size() == 1) {
                    this.index = 0;
                } else {
                    this.index = randomIndex(this.index, this.playing);
                }
                return true;
            }
            case LOOP -> {
                this.index = this.index < 0 ? 0 : (this.index + 1) % this.trackIds.size();
                return true;
            }
            default -> {
                int next = this.index + 1;
                if (next >= this.trackIds.size()) {
                    this.ended = true;
                    this.index = -1;
                    return false;
                }
                this.index = next;
                return true;
            }
        }
    }

    /**
     * 回到上一首（界面上的“上一首”按钮）。
     * 随机播放换一个别的；顺序循环绕回最后一首；顺序播放走到第一首就停在第一首。
     */
    public boolean retreat() {
        if (this.trackIds.isEmpty() || this.ended) {
            return false;
        }
        switch (this.order) {
            case SHUFFLE -> {
                if (this.trackIds.size() == 1) {
                    this.index = 0;
                } else {
                    this.index = randomIndex(this.index, this.playing);
                }
                return true;
            }
            case LOOP -> {
                this.index = this.index < 0 ? 0 : (this.index - 1 + this.trackIds.size()) % this.trackIds.size();
                return true;
            }
            default -> {
                this.index = this.index < 0 ? 0 : Math.max(0, this.index - 1);
                return true;
            }
        }
    }

    /** 是不是已经按“顺序播放”放到最后一首、停下来了。 */
    public boolean hasEnded() {
        return this.ended;
    }
}
