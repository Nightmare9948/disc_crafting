# 唱片工艺 — 乐随我心（Disc Crafting）

<img src="src/main/resources/assets/disc_crafting/icon.png" width="128" alt="Disc Crafting icon">

把本地音频做成游戏内唱片，再用随身听随身播放的 Minecraft 轻量音频拓展模组。

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green)
![Fabric](https://img.shields.io/badge/Loader-Fabric-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

| 项目 | 值 |
| --- | --- |
| 模组版本 | `1.1.0.4-fabric-1.21.1` |
| 适用游戏 | Minecraft 1.21.1（Fabric） |
| 运行环境 | 客户端（`environment` 为 `*`，服务端可装但音频功能在客户端） |
| 作者 | **Nightmare9948** |
| 前置 | [Fabric API](https://modrinth.com/mod/fabric-api)（必需）、[Mod Menu](https://modrinth.com/mod/modmenu)（可选） |

## 功能

- **本地音频导入**：支持 `.mp3` / `.wav` / `.ogg` / `.flac` / `.aiff` / `.au`，系统文件选择器或直接把文件拖进窗口。
- **自定义唱片**：用音轨 + 空白唱片制作可播放的唱片，可自定义名称与图标。
- **随身听**：手持播放、放置到地面播放、独立音量 / 循环 / 列表播放，与方块上的唱片机互不干扰。
- **曲目库与播放列表**：导入的音轨集中管理，支持顺序 / 随机 / 循环播放。
- **界面设置**：导入质量（22kHz 单声道 / 44kHz 单声道 / 44kHz 立体声）、默认音量、水中音效强度、界面语言（简中 / 繁中 / English）。

## 安装

1. 安装 Minecraft 1.21.1 与 [Fabric Loader](https://fabricmc.net/use/)（≥ 0.15.11）。
2. 把 [Fabric API](https://modrinth.com/mod/fabric-api) 和本模组放进 `mods/` 目录。
3. 可选：安装 [Mod Menu](https://modrinth.com/mod/modmenu) 以获得更完整的配置界面。

## 从源码构建

需要 **JDK 21** 与 **Gradle 8.10+**（工程未附带 wrapper，可执行 `gradle wrapper --gradle-version 8.12.1` 生成）。

```bash
# 构建
gradle build

# 产物
build/libs/disc_crafting-1.1.0.4-fabric-1.21.1.jar
```

版本号与作者只在 `gradle.properties` 中维护一次：

```properties
mod_version=1.1.0.4-fabric-1.21.1   # 完整版本，= jar 文件名
mod_display_version=1.1.0.4-fabric-1.21.1   # 模组列表里显示的版本
mod_authors=Nightmare9948           # 写进 fabric.mod.json 的作者
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
    ├── audio/                 音频导入、解码、播放、播放列表、音轨存储
    ├── gui/                   自制控件（图标按钮、滑块、进度条、可选中列表）
    ├── screen/                唱片工作室、曲目库、设置、随身听面板、导入界面
    └── render/                随身听实体渲染
src/main/resources
├── fabric.mod.json            模组元数据（作者/版本由构建注入）
├── assets/disc_crafting/      材质、模型、语言文件、图标
└── data/disc_crafting/        配方
```

## 致谢

- 音频解码：`jlayer`（MP3，随模组打包）、LWJGL `STBVorbis`（OGG）。
- 依赖与生态：[Fabric](https://fabricmc.net/)、[Fabric API](https://modrinth.com/mod/fabric-api)、[Mod Menu](https://modrinth.com/mod/modmenu)。

## 许可证

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

本项目以 MIT 许可证发布，详见 [LICENSE](LICENSE)。
Minecraft 是 Mojang Synergies AB 的商标，本项目与 Mojang / Microsoft 无关联。
