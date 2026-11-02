package cn.nightmare9948.disccrafting.client.screen;

import cn.nightmare9948.disccrafting.Lang;
import cn.nightmare9948.disccrafting.client.Settings;
import cn.nightmare9948.disccrafting.client.gui.Gui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 模组设置界面（模组菜单里的“配置”入口，也可以从唱片工艺界面左下角进来）：
 * 界面语言（一个横条，点开是简中 / 繁中 / English 的列表）、默认音量、导入音质、默认单曲循环；
 * “水中音效”点进去是单独的详细设置页（总开关 + 0~300% 强度）。
 */
public class ConfigScreen extends Screen {
    private static final int PANEL_W = 320;
    private static final int PANEL_H = 220;

    /** 横条里可选的语言，顺序就是这个列表从上到下的顺序。 */
    private static final String[] LANGUAGES = {
        Settings.LANG_ZH, Settings.LANG_ZH_TW, Settings.LANG_EN
    };
    private static final String[] LANGUAGE_KEYS = {
        "button.disc_crafting.lang_zh",
        "button.disc_crafting.lang_zh_tw",
        "button.disc_crafting.lang_en"
    };

    private static final int BAR_W = PANEL_W - 32;
    private static final int ROW_H = 20;
    /** 设置区最下面一行（单曲循环 / 水中音效）的下边缘，相对面板顶。 */
    private static final int SETTINGS_BOTTOM = 144;

    private final Screen parent;
    private int left;
    private int top;
    /** 语言列表有没有展开。 */
    private boolean languageOpen;
    private ButtonWidget languageBar;
    /** 会被展开的语言列表盖住的按钮：列表一展开就把它们收起来，见 {@link #applyListCover()}。 */
    private final List<ButtonWidget> coveredButtons = new ArrayList<>();

    public ConfigScreen(Screen parent) {
        super(Lang.t("screen.disc_crafting.config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.left = Math.max(6, (this.width - PANEL_W) / 2);
        this.top = Math.max(6, (this.height - PANEL_H) / 2);

        this.coveredButtons.clear();

        // 语言：只留一个横条，显示当前语言，点开才列出可选语言
        this.languageBar = ButtonWidget.builder(languageBarText(),
                button -> toggleLanguageList())
            .dimensions(this.left + 16, this.top + 40, BAR_W, ROW_H).build();
        this.addDrawableChild(this.languageBar);

        // 音量
        addCovered(ButtonWidget.builder(volumeText(), button -> {
            Settings.setVolumePercent((Settings.volumePercent() + 50) % 250);
            button.setMessage(volumeText());
        }).dimensions(this.left + 16, this.top + 92, 140, 20).build());

        // 导入音质
        addCovered(ButtonWidget.builder(qualityText(), button -> {
            Settings.cycleQuality();
            button.setMessage(qualityText());
        }).dimensions(this.left + 164, this.top + 92, 140, 20).build());

        // 默认单曲循环
        addCovered(ButtonWidget.builder(loopText(), button -> {
            Settings.setLoopSingle(!Settings.loopSingle());
            button.setMessage(loopText());
        }).dimensions(this.left + 16, this.top + 124, 140, 20).build());

        // 水中音效：固定显示“水中音效设置”，点进去是详细设置（开关 + 0~300% 强度）
        addCovered(ButtonWidget.builder(Lang.t("button.disc_crafting.underwater_entry"),
                button -> openUnderwaterSettings())
            .dimensions(this.left + 164, this.top + 124, 140, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Lang.t("button.disc_crafting.done"), button -> back())
            .dimensions(this.left + PANEL_W / 2 - 50, this.top + PANEL_H - 30, 100, 20).build());

        // 改窗口大小时 init 会重来一遍：列表要是还开着，新的这一批按钮也得照样收起来
        applyListCover();
    }

    /** 加一个“列表展开时会被盖住”的按钮。 */
    private void addCovered(ButtonWidget button) {
        this.coveredButtons.add(button);
        this.addDrawableChild(button);
    }

    // ------------------------------------------------------------ 语言横条

    private Text languageBarText() {
        String label = Lang.text(languageKey(currentLanguage()));
        return Text.literal(label + (this.languageOpen ? "  ▴" : "  ▾"));
    }

    private void toggleLanguageList() {
        this.languageOpen = !this.languageOpen;
        if (this.languageBar != null) {
            this.languageBar.setMessage(languageBarText());
        }
        applyListCover();
    }

    /**
     * 列表展开时把被它盖住的几个按钮整个收起来（visible = false）。
     *
     * 只靠“先落屏再画底色”是不够的：同一批绘制里色块和文字分属两个渲染层，
     * 出图时色块统一先画、文字统一后画，按钮的标题文字还是会浮到列表底上面。
     * 让被盖住的按钮干脆不画，就没有任何字能透出来了。
     */
    private void applyListCover() {
        boolean visible = !this.languageOpen;
        for (ButtonWidget button : this.coveredButtons) {
            button.visible = visible;
        }
    }

    /** 展开的列表从哪儿开始（就在横条下面）。 */
    private int listTop() {
        return this.top + 40 + ROW_H + 2;
    }

    /** 展开的列表到哪儿结束（刚好盖住整个设置区）。 */
    private int listBottom() {
        return this.top + SETTINGS_BOTTOM + 1;
    }

    /** 展开后每一行的高度：把整个设置区平均分给几个语言。 */
    private int listRowHeight() {
        return Math.max(ROW_H, (listBottom() - listTop()) / LANGUAGES.length);
    }

    private int languageIndexAt(double mouseX, double mouseY) {
        int x = this.left + 16;
        int y = listTop();
        int rowHeight = listRowHeight();
        int height = rowHeight * LANGUAGES.length;
        if (mouseX < x || mouseX >= x + BAR_W || mouseY < y || mouseY >= y + height) {
            return -1;
        }
        return (int) ((mouseY - y) / rowHeight);
    }

    private void drawLanguageList(DrawContext context, int mouseX, int mouseY) {
        int x = this.left + 16;
        int y = listTop();
        int rowHeight = listRowHeight();
        int height = rowHeight * LANGUAGES.length;
        int bottom = listBottom();
        // 先把上面已经画过的东西落屏一次，再铺列表的底色和边框
        context.draw();
        context.fill(x - 2, y - 2, x + BAR_W + 2, bottom + 2, 0xFF0B0B10);
        context.fill(x, y, x + BAR_W, bottom, 0xFF16161F);
        context.drawBorder(x - 1, y - 1, BAR_W + 2, bottom - y + 1, 0xFF6E6E7A);
        // 底色再落一次屏，然后才写列表里的字
        context.draw();

        String current = currentLanguage();
        for (int i = 0; i < LANGUAGES.length; i++) {
            int rowY = y + i * rowHeight;
            boolean hover = mouseX >= x && mouseX < x + BAR_W
                && mouseY >= rowY && mouseY < rowY + rowHeight;
            if (hover) {
                context.fill(x, rowY, x + BAR_W, rowY + rowHeight, 0xFF2C3A55);
            }
            boolean selected = LANGUAGES[i].equals(current);
            Text label = Lang.t(LANGUAGE_KEYS[i]);
            if (selected) {
                label = Text.literal("▶ ").append(label);
            }
            context.drawTextWithShadow(this.textRenderer, label,
                x + 8, rowY + (rowHeight - 8) / 2, selected ? 0xFFD24A : 0xFFFFFF);
        }
    }

    private static String languageKey(String code) {
        for (int i = 0; i < LANGUAGES.length; i++) {
            if (LANGUAGES[i].equals(code)) {
                return LANGUAGE_KEYS[i];
            }
        }
        return LANGUAGE_KEYS[0];
    }

    /**
     * 当前生效的语言。老配置里存的是“跟随游戏”（界面上已经不再提供这一项），
     * 就按游戏现在用的语言挑一个对应的显示出来，玩家一眼能看出自己在用哪种。
     */
    private static String currentLanguage() {
        String mode = Settings.language();
        for (String code : LANGUAGES) {
            if (code.equals(mode)) {
                return code;
            }
        }
        String game = "";
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.options != null && client.options.language != null) {
            game = client.options.language.toLowerCase(Locale.ROOT);
        }
        if (game.startsWith("zh")) {
            if (game.contains("tw") || game.contains("hk") || game.contains("hant")) {
                return Settings.LANG_ZH_TW;
            }
            return Settings.LANG_ZH;
        }
        return Settings.LANG_EN;
    }

    // ------------------------------------------------------------ 其它设置

    private Text volumeText() {
        return Lang.t("button.disc_crafting.volume_default", Settings.volumePercent());
    }

    private Text qualityText() {
        return Lang.t("button.disc_crafting.quality_line",
            Lang.t("quality.disc_crafting." + Settings.qualityName()));
    }

    private void openUnderwaterSettings() {
        if (this.client != null) {
            this.client.setScreen(new UnderwaterSettingsScreen(this));
        }
    }

    private Text loopText() {
        return Lang.t(Settings.loopSingle()
            ? "button.disc_crafting.loop_on" : "button.disc_crafting.loop_off");
    }

    private void setLanguage(String mode) {
        Settings.setLanguage(mode);
        if (this.client != null) {
            // 换语言后整个界面重画一遍，标题和按钮都跟着变
            this.client.setScreen(new ConfigScreen(this.parent));
        }
    }

    private void back() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    // ------------------------------------------------------------ 交互

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.languageOpen) {
            int index = languageIndexAt(mouseX, mouseY);
            boolean onBar = this.languageBar != null
                && this.languageBar.isMouseOver(mouseX, mouseY);
            this.languageOpen = false;
            if (this.languageBar != null) {
                this.languageBar.setMessage(languageBarText());
            }
            applyListCover();
            if (index >= 0) {
                setLanguage(LANGUAGES[index]);
                return true;
            }
            if (onBar) {
                // 点横条本身就是“收起”，别再让它又展开一次
                return true;
            }
            // 点在别的按钮上：收起列表，这一下照常交给下面的按钮
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.languageOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.languageOpen = false;
            if (this.languageBar != null) {
                this.languageBar.setMessage(languageBarText());
            }
            applyListCover();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------ 绘制

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        Gui.panel(context, this.left, this.top, PANEL_W, PANEL_H);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
            this.left + PANEL_W / 2, this.top + 10, 0xFFFFFF);

        context.drawTextWithShadow(this.textRenderer, Lang.t("label.disc_crafting.language"),
            this.left + 16, this.top + 28, 0xB7B7C8);
        int line = this.top + 152;
        for (net.minecraft.text.OrderedText hint
            : this.textRenderer.wrapLines(Lang.t("hint.disc_crafting.language"), PANEL_W - 32)) {
            context.drawTextWithShadow(this.textRenderer, hint, this.left + 16, line, 0x8A8A9A);
            line += 10;
        }

        // 列表最后画，自己铺一层不透明的底；下面那几个按钮这会儿已经收起来了，
        // 所以列表后面不会再有别人的文字
        if (this.languageOpen) {
            drawLanguageList(context, mouseX, mouseY);
        }
    }

    @Override
    public boolean shouldPause() {
        return true;
    }
}
