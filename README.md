# 唱片工艺 — 乐随我心（Disc Crafting）

<img src="src/main/resources/assets/disc_crafting/icon.png" width="128" alt="Disc Crafting icon">

把本地音频做成游戏内唱片，再用随身听随身播放的 Minecraft 轻量音频拓展模组。

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green)
![Fabric](https://img.shields.io/badge/Loader-Fabric-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

| 项目 | 值 |
| --- | --- |
| 模组版本 | `1.1.0.5-fabric-1.21.1` |
| 适用游戏 | Minecraft 1.21.1（Fabric） |
| 运行环境 | 客户端（`environment` 为 `*`，服务端可装但音频功能在客户端） |
| 作者 | **Nightmare9948** |
| 前置 | [Fabric API](https://modrinth.com/mod/fabric-api)（必需）、[Mod Menu](https://modrinth.com/mod/modmenu)（可选） |

## 功能

- **本地音频导入**：支持 `.mp3` / `.wav` / `.ogg` / `.flac` / `.aiff` / `.au`，系统文件选择器或直接把文件拖进窗口。
- **自定义唱片**：用音轨 + 空白唱片制作可播放的唱片，可自定义名称与图标。
- **随身听**：手持播放、放置到地面播放，独立音量 / 循环 / 列表播放，与方块上的唱片机互不干扰。
- **曲目库与播放列表**：导入的音轨集中管理，支持顺序 / 随机 / 循环播放，列表可上下滚动。
- **播放进度记忆**：退出再进游戏，手持随身听会接着上次那一首、那个位置继续放。
- **每台随身听互相独立**：曲目库、音量、循环、进度都跟着这一台走，背包里放两支也不会串味。
- **音符粒子**：随身听放歌时在它上方冒音符，可在设置里开关、调生成速度（慢 / 普通 / 快 / 密集）和样式（音符 / 光圈 / 星尘）。
- **淡入淡出**：开始播放时音量缓缓起来，暂停 / 停止时缓缓落下，可在模组设置页开关。
- **界面设置**：导入质量（22kHz 单声道 / 44kHz 单声道 / 44kHz 立体声）、默认音量、水中音效强度、界面语言（简中 / 繁中 / English）。

## 1.1.0.5 更新内容

1. 新增音符粒子设置（开关、生成速度、样式）。
2. 新增随身听播放进度记忆，重进游戏后可接着上次的进度播放。
3. 唱片工艺界面的曲目库改为可上下滚动，能显示更多曲目。
4. 每支手持随身听状态互相独立，可分别设置曲目库、音量等。
5. 随身听放到地上继承手里那台时，默认状态为停止播放。
6. 新增播放淡入 / 淡出效果及其开关（在模组菜单页）。
7. 支持 `.flac` 音频导入。
8. 版本号更新为 1.1.0.5。

## 安装

1. 安装 Minecraft 1.21.1 与 [Fabric Loader](https://fabricmc.net/use/)（≥ 0.15.11）。
2. 把 [Fabric API](https://modrinth.com/mod/fabric-api) 和本模组放进 `mods/` 目录。
3. 可选：安装 [Mod Menu](https://modrinth.com/mod/modmenu) 以获得更完整的配置界面。

## 从源码构建

需要 **JDK 21**（工程自带 Gradle wrapper，首次构建会下载 Gradle 8.12.1）。

```bash
# Windows
gradlew.bat build
# macOS / Linux
./gradlew build
```

产物：

```
build/libs/disc_crafting-1.1.0.5-fabric-1.21.1.jar
```

版本号与作者只在 `gradle.properties` 中维护一次：

```properties
mod_version=1.1.0.5-fabric-1.21.1         # 完整版本，= jar 文件名
mod_display_version=1.1.0.5-fabric-1.21.1 # 模组列表里显示的版本
mod_authors=Nightmare9948                 # 写进 fabric.mod.json 的作者
maven_group=cn.nightmare9948
```

构建时 `processResources` 会把这三个值替换进 `src/main/resources/fabric.mod.json` 的
`${version}` / `${mod_display_version}` / `${mod_authors}` 占位符。

## 项目结构

```
src/main/java/cn/nightmare9948/disccrafting
├── DiscCrafting.java          模组入口（注册物品、实体、组件、网络）
├── ClientBridge.java          客户端钩子桥接（服务端安全调用）
├── Lang.java                  双语/多语言查找
├── ModItems / ModComponents / ModEntities / ModNetworking
├── item/                      唱片物品、随身听物品
├── entity/                    随身听实体
├── component/                 唱片数据组件
├── net/                       网络载荷
├── util/                      手持物品扫描等工具
└── client/
    ├── audio/                 音频导入、解码（含 FLAC）、播放、播放列表、音轨存储、每台随身听的状态
    ├── gui/                   自制控件（图标按钮、滑块、进度条、可滚动列表）
    ├── screen/                唱片工作室、曲目库、设置、音符粒子设置、随身听面板、导入界面
    └── render/                随身听实体渲染
src/main/resources
├── fabric.mod.json            模组元数据（作者/版本由构建注入）
├── assets/disc_crafting/      材质、模型、语言文件、图标
└── data/disc_crafting/        配方
```

## 致谢

- 音频解码：`jlayer`（MP3，随模组打包）、LWJGL `STBVorbis`（OGG）、内置纯 Java FLAC 解码器。
- 依赖与生态：[Fabric](https://fabricmc.net/)、[Fabric API](https://modrinth.com/mod/fabric-api)、[Mod Menu](https://modrinth.com/mod/modmenu)。

## 许可证

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

本项目以 MIT 许可证发布，详见 [LICENSE](LICENSE)。
Minecraft 是 Mojang Synergies AB 的商标，本项目与 Mojang / Microsoft 无关联。
