package cn.nightmare9948.disccrafting.client;

import cn.nightmare9948.disccrafting.DiscCrafting;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.MathHelper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 客户端设置：随身听音量（0~200%）、导入音质、单曲循环、水中音效、界面语言。
 * 存在 config/disc_crafting/settings.json 里，跟着玩家电脑走。
 */
public final class Settings {
    private Settings() {
    }

    public static final int MIN_VOLUME = 0;
    public static final int MAX_VOLUME = 200;

    /** 水中音效强度：100% 是默认闷感，最大可以调到 300%。 */
    public static final int MIN_UNDERWATER_STRENGTH = 0;
    public static final int MAX_UNDERWATER_STRENGTH = 300;
    public static final int DEFAULT_UNDERWATER_STRENGTH = 100;

    /**
     * 语言选项：旧版本存过的“跟随游戏”（现在界面上不再提供，只用来兼容老配置）、
     * 简体中文、繁體中文、English。
     */
    public static final String LANG_AUTO = "auto";
    public static final String LANG_ZH = "zh_cn";
    public static final String LANG_ZH_TW = "zh_tw";
    public static final String LANG_EN = "en_us";

    private static int volumePercent = 100;
    private static int quality = 1;
    private static boolean loopSingle;
    /** 水中音效：随身听在水里时自动把声音闷住，默认开。 */
    private static boolean underwater = true;
    /** 水中音效的强度（%），默认 100，可调 0~300。 */
    private static int underwaterStrength = DEFAULT_UNDERWATER_STRENGTH;
    private static String language = LANG_AUTO;

    /** 头顶音符粒子：总开关、生成速度档位、样式。 */
    private static boolean noteParticles = true;
    /** 0 = 慢，1 = 普通（默认），2 = 快，3 = 很密。 */
    private static int particleSpeed = 1;
    /** 0 = 音符，1 = 环绕音符，2 = 星尘。 */
    private static int particleStyle = 0;

    /** 播放 / 暂停时的淡入淡出，默认开。 */
    private static boolean fade = true;

    private static boolean loaded;

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve(DiscCrafting.MOD_ID);
    }

    private static Path file() {
        return dir().resolve("settings.json");
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
            if (root.has("volume")) {
                volumePercent = MathHelper.clamp(root.get("volume").getAsInt(), MIN_VOLUME, MAX_VOLUME);
            }
            if (root.has("quality")) {
                quality = MathHelper.clamp(root.get("quality").getAsInt(), 0, 2);
            }
            if (root.has("loop")) {
                loopSingle = root.get("loop").getAsBoolean();
            }
            if (root.has("underwater")) {
                underwater = root.get("underwater").getAsBoolean();
            }
            if (root.has("underwater_strength")) {
                underwaterStrength = MathHelper.clamp(root.get("underwater_strength").getAsInt(),
                    MIN_UNDERWATER_STRENGTH, MAX_UNDERWATER_STRENGTH);
            }
            if (root.has("language")) {
                language = normalizeLanguage(root.get("language").getAsString());
            }
            if (root.has("note_particles")) {
                noteParticles = root.get("note_particles").getAsBoolean();
            }
            if (root.has("particle_speed")) {
                particleSpeed = MathHelper.clamp(root.get("particle_speed").getAsInt(), 0, 3);
            }
            if (root.has("particle_style")) {
                particleStyle = MathHelper.clamp(root.get("particle_style").getAsInt(), 0, 2);
            }
            if (root.has("fade")) {
                fade = root.get("fade").getAsBoolean();
            }
        } catch (Throwable t) {
            DiscCrafting.LOGGER.warn("读取设置失败", t);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(dir());
            JsonObject root = new JsonObject();
            root.addProperty("volume", volumePercent);
            root.addProperty("quality", quality);
            root.addProperty("loop", loopSingle);
            root.addProperty("underwater", underwater);
            root.addProperty("underwater_strength", underwaterStrength);
            root.addProperty("language", language);
            root.addProperty("note_particles", noteParticles);
            root.addProperty("particle_speed", particleSpeed);
            root.addProperty("particle_style", particleStyle);
            root.addProperty("fade", fade);
            Files.writeString(file(), root.toString(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            DiscCrafting.LOGGER.warn("保存设置失败", t);
        }
    }

    public static int volumePercent() {
        load();
        return volumePercent;
    }

    public static void setVolumePercent(int percent) {
        load();
        volumePercent = MathHelper.clamp(percent, MIN_VOLUME, MAX_VOLUME);
        save();
    }

    public static float volumeGain() {
        return volumePercent() / 100.0f;
    }

    public static int quality() {
        load();
        return quality;
    }

    public static void cycleQuality() {
        load();
        quality = (quality + 1) % 3;
        save();
    }

    /** "low" / "mid" / "high"，渲染时再翻译。 */
    public static String qualityName() {
        return switch (quality()) {
            case 0 -> "low";
            case 2 -> "high";
            default -> "mid";
        };
    }

    /** 音质说明走翻译键，中文英文都能显示。 */
    public static String qualityDetailKey() {
        return switch (quality()) {
            case 0 -> "quality.disc_crafting.low.detail";
            case 2 -> "quality.disc_crafting.high.detail";
            default -> "quality.disc_crafting.mid.detail";
        };
    }

    public static boolean loopSingle() {
        load();
        return loopSingle;
    }

    public static void setLoopSingle(boolean loop) {
        load();
        loopSingle = loop;
        save();
    }

    public static boolean underwaterSound() {
        load();
        return underwater;
    }

    public static void setUnderwaterSound(boolean value) {
        load();
        underwater = value;
        save();
    }

    public static int underwaterStrength() {
        load();
        return underwaterStrength;
    }

    public static void setUnderwaterStrength(int percent) {
        load();
        underwaterStrength = MathHelper.clamp(percent, MIN_UNDERWATER_STRENGTH, MAX_UNDERWATER_STRENGTH);
        save();
    }

    /** 0 = 完全不闷，1 = 默认，3 = 最强（300%）。 */
    public static float underwaterStrengthScale() {
        return underwaterStrength() / 100.0f;
    }

    // ------------------------------------------------------------ 音符粒子

    public static boolean noteParticles() {
        load();
        return noteParticles;
    }

    public static void setNoteParticles(boolean value) {
        load();
        noteParticles = value;
        save();
    }

    public static int particleSpeed() {
        load();
        return particleSpeed;
    }

    public static void cycleParticleSpeed() {
        load();
        particleSpeed = (particleSpeed + 1) % 4;
        save();
    }

    public static String particleSpeedName() {
        return switch (particleSpeed()) {
            case 0 -> "slow";
            case 2 -> "fast";
            case 3 -> "dense";
            default -> "normal";
        };
    }

    /** 隔多少 tick 冒一次（越小越密）。 */
    public static int particleIntervalTicks() {
        return switch (particleSpeed()) {
            case 0 -> 12;
            case 2 -> 3;
            case 3 -> 1;
            default -> 6;
        };
    }

    public static int particleStyle() {
        load();
        return particleStyle;
    }

    public static void cycleParticleStyle() {
        load();
        particleStyle = (particleStyle + 1) % 3;
        save();
    }

    public static String particleStyleName() {
        return switch (particleStyle()) {
            case 1 -> "ring";
            case 2 -> "spark";
            default -> "note";
        };
    }

    public static void resetParticles() {
        load();
        noteParticles = true;
        particleSpeed = 1;
        particleStyle = 0;
        save();
    }

    // ------------------------------------------------------------ 淡入淡出

    public static boolean fadeEnabled() {
        load();
        return fade;
    }

    public static void setFadeEnabled(boolean value) {
        load();
        fade = value;
        save();
    }

    public static String language() {
        load();
        return language;
    }

    public static void setLanguage(String value) {
        load();
        language = normalizeLanguage(value);
        save();
    }

    public static String normalizeLanguage(String value) {
        if (LANG_ZH.equals(value) || LANG_ZH_TW.equals(value) || LANG_EN.equals(value)) {
            return value;
        }
        return LANG_AUTO;
    }
}
