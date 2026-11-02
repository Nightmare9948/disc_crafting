package cn.nightmare9948.disccrafting.client;

import cn.nightmare9948.disccrafting.DiscCrafting;
import cn.nightmare9948.disccrafting.Lang;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 把模组自带的 lang 文件（简体中文 / 繁體中文 / English）都读进内存，
 * 这样玩家在模组设置里一换语言，界面马上跟着变，不用重启游戏。
 *
 * 读不到（比如还在加载资源）就返回 null，自动退回原版翻译。
 */
public final class ClientLang {
    private static final Map<String, String> ZH = new HashMap<>();
    private static final Map<String, String> ZH_TW = new HashMap<>();
    private static final Map<String, String> EN = new HashMap<>();
    private static volatile boolean loaded;

    private ClientLang() {
    }

    public static void init() {
        Lang.setTranslator(ClientLang::translate);
    }

    /** 资源包重新加载后调用，保证换了语言包也能跟上。 */
    public static void reload() {
        loaded = false;
        ensureLoaded();
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        try {
            Map<String, String> zh = read(client, "zh_cn");
            Map<String, String> zhTw = read(client, "zh_tw");
            Map<String, String> en = read(client, "en_us");
            if (zh.isEmpty() && zhTw.isEmpty() && en.isEmpty()) {
                return;
            }
            synchronized (ZH) {
                ZH.clear();
                ZH.putAll(zh);
            }
            synchronized (ZH_TW) {
                ZH_TW.clear();
                ZH_TW.putAll(zhTw);
            }
            synchronized (EN) {
                EN.clear();
                EN.putAll(en);
            }
            loaded = true;
        } catch (Throwable throwable) {
            DiscCrafting.LOGGER.warn("读取模组语言文件失败", throwable);
        }
    }

    private static Map<String, String> read(MinecraftClient client, String code) throws Exception {
        Map<String, String> map = new HashMap<>();
        Identifier id = Identifier.of(DiscCrafting.MOD_ID, "lang/" + code + ".json");
        Resource resource = client.getResourceManager().getResource(id).orElse(null);
        if (resource == null) {
            return map;
        }
        try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (String key : root.keySet()) {
                map.put(key, root.get(key).getAsString());
            }
        }
        return map;
    }

    /** 返回 null 表示交给原版翻译（老配置里的“跟随游戏”就走这条路）。 */
    private static String translate(String key) {
        ensureLoaded();
        if (!loaded) {
            return null;
        }
        String mode = Settings.language();
        if (Settings.LANG_ZH_TW.equals(mode)) {
            // 繁体文件万一缺了某一条，先退回简体，再退回英文，界面不会出现裸键名
            return lookup(ZH_TW, ZH, EN, key);
        }
        if (Settings.LANG_ZH.equals(mode)) {
            return lookup(ZH, EN, ZH_TW, key);
        }
        if (Settings.LANG_EN.equals(mode)) {
            return lookup(EN, ZH, ZH_TW, key);
        }
        return null;
    }

    private static String lookup(Map<String, String> primary, Map<String, String> secondary,
                                 Map<String, String> tertiary, String key) {
        String value = get(primary, key);
        if (value != null) {
            return value;
        }
        value = get(secondary, key);
        return value != null ? value : get(tertiary, key);
    }

    private static String get(Map<String, String> map, String key) {
        synchronized (map) {
            return map.get(key);
        }
    }
}
