package cn.nightmare9948.disccrafting;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/**
 * 模组自己的语言切换入口。
 *
 * 原版的 Text.translatable 只认游戏当前语言，玩家在游戏里切语言要重启，
 * 而这里想做到“模组菜单里点一下就换”。所以界面上的字统一走 {@link #t(String, Object...)}：
 * 客户端启动时会把中英两份 lang 文件都读进内存，由玩家选的模式决定用哪一份。
 * 客户端没接管（比如服务端）时自动退回原版翻译，行为不变。
 */
public final class Lang {
    private Lang() {
    }

    /** 客户端的翻译来源；返回 null 表示“跟随游戏语言”。 */
    public interface Translator {
        String translate(String key);
    }

    private static volatile Translator translator;

    public static void setTranslator(Translator value) {
        translator = value;
    }

    public static MutableText t(String key, Object... args) {
        Translator current = translator;
        if (current != null) {
            String value = current.translate(key);
            if (value != null) {
                return Text.literal(format(value, args));
            }
        }
        return Text.translatable(key, args);
    }

    /** 只取字符串（给 item.getName 之类需要 Text 之外的地方用）。 */
    public static String text(String key, Object... args) {
        return t(key, args).getString();
    }

    /** 物品名：模组语言是“跟随游戏”时保留原版翻译（含语言包的附加翻译）。 */
    public static Text itemName(String key, Text fallback) {
        Translator current = translator;
        if (current == null) {
            return fallback;
        }
        String value = current.translate(key);
        return value == null ? fallback : Text.literal(value);
    }

    private static String format(String value, Object... args) {
        if (args == null || args.length == 0) {
            return value;
        }
        try {
            return String.format(value, args);
        } catch (Throwable ignored) {
            return value;
        }
    }
}
