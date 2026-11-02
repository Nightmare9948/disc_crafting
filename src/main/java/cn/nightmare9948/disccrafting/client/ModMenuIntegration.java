package cn.nightmare9948.disccrafting.client;

import cn.nightmare9948.disccrafting.client.screen.ConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * 模组菜单（Mod Menu）里的“配置”按钮：点开就是唱片工艺自己的设置界面，
 * 里面能切换中英双语，也能调默认音量、导入音质和单曲循环。
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        ConfigScreenFactory<ConfigScreen> factory = ConfigScreen::new;
        return factory;
    }
}
