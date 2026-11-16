package cn.nightmare9948.disccrafting.client.audio;

import cn.nightmare9948.disccrafting.DiscCrafting;
import cn.nightmare9948.disccrafting.client.Settings;
import cn.nightmare9948.disccrafting.component.DiscSource;
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
 * 放在地上的随身听“放到哪儿了”的记忆。
 *
 * 曲目和播放状态本来就存在实体身上（服务端管），但“放到第几毫秒”只有客户端知道，
 * 所以按实体编号单独记一份，退出游戏再进来能接着上次的位置放。
 * 存在 config/disc_crafting/entity_progress.json。
 */
public final class EntityProgress {
    private EntityProgress() {
    }

    private static final int MAX_ENTRIES = 64;

    private record Entry(String trackId, String songId, long positionMs) {
    }

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
    private static boolean loaded;
    private static boolean dirty;
    private static long lastSaveAt;

    private static Path file() {
        return Settings.dir().resolve("entity_progress.json");
    }

    public static synchronized void remember(UUID uuid, DiscSource source, long positionMs) {
        if (uuid == null || source == null || source.isEmpty()) {
            return;
        }
        load();
        String key = uuid.toString();
        ENTRIES.remove(key);
        ENTRIES.put(key, new Entry(source.trackId(), source.songId(), Math.max(0, positionMs)));
        while (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.remove(ENTRIES.keySet().iterator().next());
        }
        dirty = true;
    }

    /** 这台随身听上次放到第几毫秒；曲目对不上（换了唱片）就返回 -1。 */
    public static synchronized long positionFor(UUID uuid, DiscSource source) {
        if (uuid == null || source == null || source.isEmpty()) {
            return -1;
        }
        load();
        Entry entry = ENTRIES.get(uuid.toString());
        if (entry == null) {
            return -1;
        }
        if (!same(entry.trackId(), source.trackId()) || !same(entry.songId(), source.songId())) {
            return -1;
        }
        return entry.positionMs();
    }

    public static synchronized void forget(UUID uuid) {
        if (uuid == null) {
            return;
        }
        load();
        if (ENTRIES.remove(uuid.toString()) != null) {
            dirty = true;
        }
    }

    public static synchronized void flushIfDue() {
        if (!dirty || System.currentTimeMillis() - lastSaveAt < 4000L) {
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
            JsonArray array = new JsonArray();
            for (Map.Entry<String, Entry> entry : ENTRIES.entrySet()) {
                JsonObject object = new JsonObject();
                object.addProperty("id", entry.getKey());
                object.addProperty("track", entry.getValue().trackId());
                object.addProperty("song", entry.getValue().songId());
                object.addProperty("position", entry.getValue().positionMs());
                array.add(object);
            }
            JsonObject root = new JsonObject();
            root.add("progress", array);
            Files.writeString(file(), root.toString(), StandardCharsets.UTF_8);
        } catch (Throwable throwable) {
            DiscCrafting.LOGGER.warn("保存随身听进度失败", throwable);
        }
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Path path = file();
            if (!Files.exists(path)) {
                return;
            }
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                .getAsJsonObject();
            JsonArray array = root.has("progress") ? root.getAsJsonArray("progress") : new JsonArray();
            for (JsonElement element : array) {
                JsonObject object = element.getAsJsonObject();
                String id = object.has("id") ? object.get("id").getAsString() : "";
                if (id.isEmpty()) {
                    continue;
                }
                ENTRIES.put(id, new Entry(
                    object.has("track") ? object.get("track").getAsString() : "",
                    object.has("song") ? object.get("song").getAsString() : "",
                    object.has("position") ? object.get("position").getAsLong() : 0L));
            }
        } catch (Throwable throwable) {
            DiscCrafting.LOGGER.warn("读取随身听进度失败", throwable);
        }
    }

    private static boolean same(String a, String b) {
        return a == null ? b == null || b.isEmpty() : a.equals(b == null ? "" : b);
    }
}
