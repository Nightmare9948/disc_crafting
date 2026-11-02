package cn.nightmare9948.disccrafting.client.audio;

import cn.nightmare9948.disccrafting.DiscCrafting;
import cn.nightmare9948.disccrafting.client.Settings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 播放序列的存档：手持的随身听一条，放在地上的随身听各一条（按实体编号记），
 * 存在 config/disc_crafting/playlists.json。
 */
public final class PlaylistStore {
    private PlaylistStore() {
    }

    private static final String HANDHELD_KEY = "handheld";
    private static final int MAX_ENTITY_ENTRIES = 32;

    private static final Map<String, Playlist> PLAYLISTS = new LinkedHashMap<>();
    private static Playlist handheld = new Playlist();
    private static boolean loaded;

    private static Path file() {
        return Settings.dir().resolve("playlists.json");
    }

    public static Playlist handheld() {
        load();
        return handheld;
    }

    public static Playlist entity(UUID uuid) {
        load();
        if (uuid == null) {
            return handheld;
        }
        return PLAYLISTS.computeIfAbsent(uuid.toString(), key -> new Playlist());
    }

    /**
     * 把一份序列原样装到某个实体随身听上（放置时继承手里那台用）。
     * 开关、播放顺序、曲目、放到第几首全都照抄。
     */
    public static void adopt(UUID uuid, Playlist source) {
        load();
        if (uuid == null || source == null) {
            return;
        }
        while (!PLAYLISTS.containsKey(uuid.toString()) && PLAYLISTS.size() >= MAX_ENTITY_ENTRIES) {
            PLAYLISTS.remove(PLAYLISTS.keySet().iterator().next());
        }
        PLAYLISTS.put(uuid.toString(), source.copy());
        save();
    }

    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Path path = file();
            if (!Files.exists(path)) {
                return;
            }
            String text = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            JsonArray array = root.has("playlists") ? root.getAsJsonArray("playlists") : new JsonArray();
            for (JsonElement element : array) {
                JsonObject entry = element.getAsJsonObject();
                String key = entry.has("key") ? entry.get("key").getAsString() : "";
                if (key.isEmpty()) {
                    continue;
                }
                Playlist playlist = new Playlist();
                playlist.setEnabled(entry.has("enabled") && entry.get("enabled").getAsBoolean());
                playlist.setOrder(Playlist.Order.byKey(entry.has("order") ? entry.get("order").getAsString() : ""));
                JsonArray tracks = entry.has("tracks") ? entry.getAsJsonArray("tracks") : new JsonArray();
                for (JsonElement track : tracks) {
                    playlist.add(track.getAsString());
                }
                int index = entry.has("index") ? entry.get("index").getAsInt() : 0;
                playlist.setIndex(index);
                if (HANDHELD_KEY.equals(key)) {
                    handheld = playlist;
                } else {
                    PLAYLISTS.put(key, playlist);
                }
            }
        } catch (Throwable throwable) {
            DiscCrafting.LOGGER.warn("读取播放序列失败", throwable);
        }
    }

    /** 任何改动之后调一下，把当前状态写回硬盘。 */
    public static void save() {
        try {
            Files.createDirectories(Settings.dir());
            JsonArray array = new JsonArray();
            array.add(toJson(HANDHELD_KEY, handheld));
            while (PLAYLISTS.size() > MAX_ENTITY_ENTRIES) {
                String first = PLAYLISTS.keySet().iterator().next();
                PLAYLISTS.remove(first);
            }
            for (Map.Entry<String, Playlist> entry : PLAYLISTS.entrySet()) {
                array.add(toJson(entry.getKey(), entry.getValue()));
            }
            JsonObject root = new JsonObject();
            root.add("playlists", array);
            Files.writeString(file(), root.toString(), StandardCharsets.UTF_8);
        } catch (Throwable throwable) {
            DiscCrafting.LOGGER.warn("保存播放序列失败", throwable);
        }
    }

    private static JsonObject toJson(String key, Playlist playlist) {
        JsonObject object = new JsonObject();
        object.addProperty("key", key);
        object.addProperty("enabled", playlist.isEnabled());
        object.addProperty("order", playlist.order().key());
        object.addProperty("index", playlist.index());
        JsonArray tracks = new JsonArray();
        for (String trackId : playlist.trackIds()) {
            tracks.add(trackId);
        }
        object.add("tracks", tracks);
        return object;
    }
}
