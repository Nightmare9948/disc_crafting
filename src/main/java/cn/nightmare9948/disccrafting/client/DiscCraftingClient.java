package cn.nightmare9948.disccrafting.client;

import cn.nightmare9948.disccrafting.ClientBridge;
import cn.nightmare9948.disccrafting.ModEntities;
import cn.nightmare9948.disccrafting.client.audio.ClientAudio;
import cn.nightmare9948.disccrafting.client.audio.TrackStore;
import cn.nightmare9948.disccrafting.client.audio.WalkmanInheritance;
import cn.nightmare9948.disccrafting.client.render.WalkmanEntityRenderer;
import cn.nightmare9948.disccrafting.client.screen.ImportScreen;
import cn.nightmare9948.disccrafting.client.screen.WalkmanEntityScreen;
import cn.nightmare9948.disccrafting.client.screen.WalkmanHandheldScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;

/** 客户端入口：按键、渲染器、曲库、语言、音频调度。 */
public final class DiscCraftingClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Settings.load();
        // 游戏进程有时会被设成“无界面模式”，那样 Java 自带的选文件窗口就弹不出来。
        // 这里尽量把它关掉，关不掉也不要紧：系统原生的选文件工具是另一条路。
        try {
            System.setProperty("java.awt.headless", "false");
        } catch (Throwable ignored) {
            // 设不了就算了
        }
        ClientLang.init();
        TrackStore.init();
        ModKeybinds.register();

        EntityRendererRegistry.register(ModEntities.WALKMAN, WalkmanEntityRenderer::new);

        ClientBridge.setJukeboxHook(ClientAudio.get()::onJukeboxInsert);
        ClientBridge.setVolumeHook(() -> MinecraftClient.getInstance().setScreen(new WalkmanHandheldScreen()));
        // 右键地上的随身听 -> 打开它自己的控制界面
        ClientBridge.setWalkmanHook(entity -> MinecraftClient.getInstance().setScreen(new WalkmanEntityScreen(entity)));
        // 放下随身听时先记一笔，等实体同步过来再把手里那台的播放列表整份继承过去
        ClientBridge.setWalkmanPlaceHook(WalkmanInheritance::onPlaced);

        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
    }

    private void onEndTick(MinecraftClient client) {
        // 有界面打开时（比如正在输入唱片名字）不响应快捷键，免得打字触发
        boolean noScreen = client.currentScreen == null;
        while (ModKeybinds.OPEN_MENU.wasPressed()) {
            if (noScreen) {
                client.setScreen(new ImportScreen());
            }
        }
        while (ModKeybinds.TOGGLE_PLAY.wasPressed()) {
            if (noScreen) {
                ClientAudio.get().toggleHandheld();
            }
        }
        while (ModKeybinds.VOLUME.wasPressed()) {
            if (noScreen) {
                client.setScreen(new WalkmanHandheldScreen());
            }
        }
        ClientAudio.get().tick(client);
    }
}
