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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 本地曲库：把导入过的音频记在 config/disc_crafting/tracks.json，
 * 音频本体统一转成 wav 放在 config/disc_crafting/tracks/ 下。
 */
public final class TrackStore {
    private TrackStore() {
    }

    private static final Map<String, Track> TRACKS = new LinkedHashMap<>();
    private static boolean loaded;

    public static Path tracksDir() {
        return Settings.dir().resolve("tracks");
    }

    private static Path indexFile() {
        return Settings.dir().resolve("tracks.json");
    }

    public static synchronized void init() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Files.createDirectories(tracksDir());
            Path index = indexFile();
            if (!Files.exists(index)) {
                return;
            }
            String text = Files.readString(index, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            JsonArray array = root.has("tracks") ? root.getAsJsonArray("tracks") : new JsonArray();
            for (JsonElement element : array) {
                JsonObject entry = element.getAsJsonObject();
                String id = entry.get("id").getAsString();
                String title = entry.get("title").getAsString();
                int icon = entry.has("icon") ? entry.get("icon").getAsInt() : 1;
                int duration = entry.has("duration") ? entry.get("duration").getAsInt() : 0;
                String file = entry.get("file").getAsString();
                if (id.isEmpty() || file.isEmpty()) {
                    continue;
                }
                if (!Files.exists(tracksDir().resolve(file))) {
                    continue;
                }
                TRACKS.put(id, new Track(id, title, icon, duration, file));
            }
        } catch (Throwable t) {
            DiscCrafting.LOGGER.warn("读取曲库失败", t);
        }
    }

    public static synchronized List<Track> all() {
        init();
        return Collections.unmodifiableList(new ArrayList<>(TRACKS.values()));
    }

    public static synchronized Track get(String id) {
        init();
        return TRACKS.get(id);
    }

    public static synchronized Path fileOf(Track track) {
        return tracksDir().resolve(track.file);
    }

    public static synchronized Track add(Path wav, String title, int durationMs) {
        init();
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String fileName = id + ".wav";
        Track track = new Track(id, title, 1, durationMs, fileName);
        try {
            Files.createDirectories(tracksDir());
            Files.copy(wav, tracksDir().resolve(fileName), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(wav);
        } catch (Throwable t) {
            DiscCrafting.LOGGER.warn("保存音频失败", t);
        }
        TRACKS.put(id, track);
        save();
        return track;
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(Settings.dir());
            JsonObject root = new JsonObject();
            JsonArray array = new JsonArray();
            for (Track track : TRACKS.values()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("id", track.id);
                entry.addProperty("title", track.title);
                entry.addProperty("icon", track.icon);
                entry.addProperty("duration", track.durationMs);
                entry.addProperty("file", track.file);
                array.add(entry);
            }
            root.add("tracks", array);
            Files.writeString(indexFile(), root.toString(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            DiscCrafting.LOGGER.warn("保存曲库失败", t);
        }
    }

    /** 新建一个临时 wav 路径（转换完成后会移动到 tracks 目录）。 */
    public static Path tempFile() {
        try {
            Files.createDirectories(tracksDir());
        } catch (Throwable ignored) {
        }
        return tracksDir().resolve("import-" + System.nanoTime() + ".wav");
    }
}
