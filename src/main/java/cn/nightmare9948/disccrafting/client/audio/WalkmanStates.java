package cn.nightmare9948.disccrafting.client.audio;

import cn.nightmare9948.disccrafting.DiscCrafting;
import cn.nightmare9948.disccrafting.ModComponents;
import cn.nightmare9948.disccrafting.client.Settings;
import cn.nightmare9948.disccrafting.item.WalkmanItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 每一台随身听自己的一份状态：曲目库（播放序列）、音量、单曲循环、上次放到哪一首、
 * 放到第几毫秒、是不是正在放。
 *
 * 以前这些全模组共用一份，背包里两支随身听会互相串味。现在按物品上的
 * walkman 编号分开存，存在 config/disc_crafting/walkmans.json 里。
 *
 * 写盘有节流：进度这类每 tick 都在变的数据先标脏，最多几秒写一次，
 * 免得边听歌边把硬盘写爆。
 */
public final class WalkmanStates {
    private WalkmanStates() {
    }

    /** 物品还没有编号时用的兜底编号（一般只在创造模式刚拿到的那一瞬间出现）。 */
    public static final String UNSIGNED_KEY = "w:unassigned";

    private static final String KEY_PREFIX = "w:";
    private static final int MAX_ENTRIES = 64;
    /** 两次写盘之间至少隔这么久（毫秒）。 */
    private static final long SAVE_INTERVAL_MS = 4000L;

    /** 一台随身听的全部状态。 */
    public static final class State {
        public Playlist playlist = new Playlist();
        public int volumePercent = 100;
        public boolean loop;
        /** 上次放到哪一首、放到哪儿了；空字符串表示还没放过。 */
        public String trackId = "";
        public String songId = "";
        public long positionMs;
        /** 上次是不是在放（重进游戏时按它决定自动接着放还是停在那里等人点播放）。 */
        public boolean playing;
        long updatedAt;
    }

    private static final Map<String, State> STATES = new LinkedHashMap<>();
    private static boolean loaded;
    /** 老版本只有一份全局序列：第一次用到随身听时把它交给这台。 */
    private static boolean legacyPending;
    private static boolean dirty;
    private static long lastSaveAt;

    private static Path file() {
        return Settings.dir().resolve("walkmans.json");
    }

    /** 手里这支随身听的编号；不是随身听就给兜底编号。 */
    public static String keyOf(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof WalkmanItem)) {
            return UNSIGNED_KEY;
        }
        String id = stack.get(ModComponents.WALKMAN_ID);
        return id == null || id.isEmpty() ? UNSIGNED_KEY : KEY_PREFIX + id;
    }

    /** 取（或新建）一台随身听的状态。 */
    public static synchronized State of(String key) {
        load();
        String safeKey = key == null || key.isEmpty() ? UNSIGNED_KEY : key;
        State state = STATES.get(safeKey);
        if (state == null) {
            state = new State();
            state.volumePercent = Settings.volumePercent();
            state.loop = Settings.loopSingle();
            if (legacyPending) {
                // 老配置里的那一份全局播放序列，交到玩家第一次用的这台随身听上
                state.playlist = PlaylistStore.handheld().copy();
                legacyPending = false;
            }
            put(safeKey, state);
        }
        state.updatedAt = System.currentTimeMillis();
        return state;
    }

    /**
     * 把“还没编号”时攒下的临时状态交给正式编号，播放不会被打断。
     * 已经有自己状态的编号不动。
     */
    public static synchronized void claim(String key) {
        load();
        if (key == null || key.isEmpty() || key.equals(UNSIGNED_KEY) || STATES.containsKey(key)) {
            return;
        }
        State pending = STATES.remove(UNSIGNED_KEY);
        if (pending == null) {
            return;
        }
        put(key, pending);
    }

    /** 某台随身听的播放序列（界面上“编辑列表”用的就是这一份）。 */
    public static Playlist playlist(String key) {
        return of(key).playlist;
    }

    public static synchronized void markDirty() {
        dirty = true;
    }

    /** 到了写盘间隔就顺手写一次；每 tick 调用也不心疼。 */
    public static synchronized void flushIfDue() {
        if (!dirty || System.currentTimeMillis() - lastSaveAt < SAVE_INTERVAL_MS) {
            return;
        }
        saveNow();
    }

    public static synchronized void saveNow() {
        lastSaveAt = System.currentTimeMillis();
        dirty = false;
        if (!loaded) {
            return;
        }
        try {
            Files.createDirectories(Settings.dir());
            List<Map.Entry<String, State>> entries = new ArrayList<>(STATES.entrySet());
            // 超出上限就丢掉最久没碰过的那几台
            if (entries.size() > MAX_ENTRIES) {
                entries.sort(Comparator.comparingLong(entry -> -entry.getValue().updatedAt));
                entries = entries.subList(0, MAX_ENTRIES);
            }
            JsonArray array = new JsonArray();
            for (Map.Entry<String, State> entry : entries) {
                array.add(toJson(entry.getKey(), entry.getValue()));
            }
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.add("walkmans", array);
            Files.writeString(file(), root.toString(), StandardCharsets.UTF_8);
        } catch (Throwable throwable) {
            DiscCrafting.LOGGER.warn("保存随身听状态失败", throwable);
        }
    }

    private static void put(String key, State state) {
        while (!STATES.containsKey(key) && STATES.size() >= MAX_ENTRIES) {
            STATES.remove(STATES.keySet().iterator().next());
        }
        STATES.put(key, state);
        dirty = true;
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = file();
        if (!Files.exists(path)) {
            // 没有新文件：说明是从老版本升上来的，先把老的那份全局序列留着待认领
            legacyPending = true;
            return;
        }
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            JsonArray array = root.has("walkmans") ? root.getAsJsonArray("walkmans") : new JsonArray();
            for (JsonElement element : array) {
                JsonObject entry = element.getAsJsonObject();
                String key = entry.has("key") ? entry.get("key").getAsString() : "";
                if (key.isEmpty()) {
                    continue;
                }
                State state = new State();
                state.volumePercent = entry.has("volume") ? entry.get("volume").getAsInt() : 100;
                state.loop = entry.has("loop") && entry.get("loop").getAsBoolean();
                state.trackId = entry.has("track") ? entry.get("track").getAsString() : "";
                state.songId = entry.has("song") ? entry.get("song").getAsString() : "";
                state.positionMs = entry.has("position") ? entry.get("position").getAsLong() : 0L;
                state.playing = entry.has("playing") && entry.get("playing").getAsBoolean();
                state.updatedAt = entry.has("updated") ? entry.get("updated").getAsLong() : 0L;
                if (entry.has("playlist")) {
                    readPlaylist(state.playlist, entry.getAsJsonObject("playlist"));
                }
                STATES.put(key, state);
            }
        } catch (Throwable throwable) {
            DiscCrafting.LOGGER.warn("读取随身听状态失败", throwable);
        }
    }

    private static void readPlaylist(Playlist playlist, JsonObject object) {
        playlist.setEnabled(object.has("enabled") && object.get("enabled").getAsBoolean());
        playlist.setOrder(Playlist.Order.byKey(object.has("order") ? object.get("order").getAsString() : ""));
        if (object.has("tracks")) {
            for (JsonElement track : object.getAsJsonArray("tracks")) {
                playlist.add(track.getAsString());
            }
        }
        if (object.has("index")) {
            playlist.setIndex(object.get("index").getAsInt());
        }
    }

    private static JsonObject toJson(String key, State state) {
        JsonObject object = new JsonObject();
        object.addProperty("key", key);
        object.addProperty("volume", state.volumePercent);
        object.addProperty("loop", state.loop);
        object.addProperty("track", state.trackId == null ? "" : state.trackId);
        object.addProperty("song", state.songId == null ? "" : state.songId);
        object.addProperty("position", state.positionMs);
        object.addProperty("playing", state.playing);
        object.addProperty("updated", System.currentTimeMillis());
        JsonObject playlist = new JsonObject();
        playlist.addProperty("enabled", state.playlist.isEnabled());
        playlist.addProperty("order", state.playlist.order().key());
        playlist.addProperty("index", state.playlist.index());
        JsonArray tracks = new JsonArray();
        for (String trackId : state.playlist.trackIds()) {
            tracks.add(trackId);
        }
        playlist.add("tracks", tracks);
        object.add("playlist", playlist);
        return object;
    }
}
