package cn.nightmare9948.disccrafting.client.screen;

import cn.nightmare9948.disccrafting.DiscCrafting;
import net.minecraft.client.MinecraftClient;

import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 系统自带的“打开文件”小窗口（Windows 那个选文件的对话框），
 * 和光影模组点“从文件导入配置”弹出来的是同一类东西。
 *
 * 游戏进程有时候会被设成“无界面模式”（java.awt.headless），这时 Java 自带的对话框
 * 打不开。所以这里准备了两条路：
 * 1. 先试 Java 自带的 FileDialog；
 * 2. 不行就直接叫操作系统自己的选文件工具（Windows PowerShell / macOS osascript /
 *    Linux zenity 或 kdialog），弹出来的是系统原生窗口。
 * 两条都不通就带着具体原因回到界面，让玩家知道到底为什么打不开。
 */
public final class NativeFilePicker {
    private static final String[] AUDIO_EXTENSIONS = {
        ".mp3", ".wav", ".ogg", ".flac", ".aiff", ".aif", ".au", ".m4a", ".aac"
    };

    /** 打不开的原因分类，界面上按这个选提示文字。 */
    public static final String FAIL_HEADLESS = "headless";
    public static final String FAIL_NO_TOOL = "no_tool";
    public static final String FAIL_OS = "os";
    public static final String FAIL_UNKNOWN = "unknown";

    private static volatile boolean open;
    private static volatile Path lastDir;

    private NativeFilePicker() {
    }

    public static boolean isOpen() {
        return open;
    }

    /**
     * 弹出系统选择框。
     *
     * @param onPick 选好文件后回调（在游戏主线程里跑）
     * @param onFail 打不开的时候回调，参数是（原因分类, 原始细节），同一个线程
     * @param onCancel 玩家自己点了取消时的回调
     */
    public static void open(Consumer<Path> onPick, BiConsumer<String, String> onFail, Runnable onCancel) {
        if (open) {
            return;
        }
        open = true;
        Thread thread = new Thread(() -> {
            Result result = run();
            open = false;
            MinecraftClient.getInstance().execute(() -> {
                if (result.path != null) {
                    onPick.accept(result.path);
                } else if (result.cancelled) {
                    if (onCancel != null) {
                        onCancel.run();
                    }
                } else if (result.failCode != null) {
                    onFail.accept(result.failCode, result.detail == null ? "" : result.detail);
                }
            });
        }, "disc-crafting-file-dialog");
        thread.setDaemon(true);
        thread.start();
    }

    private static final class Result {
        Path path;
        String failCode;
        String detail;
        boolean cancelled;
    }

    private static Result run() {
        Result result = new Result();
        List<String> failures = new ArrayList<>();

        // ---- 1. Java 自带的系统对话框
        try {
            if (GraphicsEnvironment.isHeadless()) {
                failures.add("headless");
            } else {
                Path picked = awtDialog();
                if (picked != null) {
                    result.path = picked;
                    return result;
                }
                result.cancelled = true;
                return result;
            }
        } catch (Throwable throwable) {
            DiscCrafting.LOGGER.warn("Java 自带的选文件窗口打不开", throwable);
            failures.add(throwable.getClass().getSimpleName()
                + (throwable.getMessage() == null ? "" : ": " + throwable.getMessage()));
        }

        // ---- 2. 直接叫操作系统自己的选文件工具
        try {
            OsResult os = osDialog();
            if (os.path != null) {
                result.path = os.path;
                return result;
            }
            if (os.cancelled) {
                result.cancelled = true;
                return result;
            }
            failures.add(os.detail);
        } catch (Throwable throwable) {
            DiscCrafting.LOGGER.warn("系统选文件工具调用失败", throwable);
            failures.add(throwable.getClass().getSimpleName()
                + (throwable.getMessage() == null ? "" : ": " + throwable.getMessage()));
        }

        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (failures.contains("headless")) {
            result.failCode = FAIL_HEADLESS;
        } else if (osName.contains("linux") && failures.size() > 0) {
            result.failCode = FAIL_NO_TOOL;
        } else {
            result.failCode = FAIL_OS;
        }
        result.detail = String.join(" / ", failures);
        DiscCrafting.LOGGER.warn("系统选文件窗口用不了：{}", result.detail);
        return result;
    }

    /** Java 自带的 FileDialog（不打游戏的脸，但确实由系统画）。 */
    private static Path awtDialog() {
        FileDialog dialog = null;
        try {
            dialog = new FileDialog((Frame) null, "Choose an audio file", FileDialog.LOAD);
            dialog.setMultipleMode(false);
            dialog.setFilenameFilter((dir, name) -> looksLikeAudio(name));
            Path dir = lastDir;
            if (dir != null && Files.isDirectory(dir)) {
                dialog.setDirectory(dir.toString());
            }
            dialog.setVisible(true);
            String fileName = dialog.getFile();
            String dirName = dialog.getDirectory();
            if (fileName == null || dirName == null) {
                return null;
            }
            Path picked = Paths.get(dirName, fileName);
            lastDir = picked.getParent();
            return picked;
        } finally {
            if (dialog != null) {
                try {
                    dialog.dispose();
                } catch (Throwable ignored) {
                    // 关窗失败无所谓
                }
            }
        }
    }

    private static final class OsResult {
        Path path;
        boolean cancelled;
        String detail;
    }

    /** 用操作系统自己的选文件工具，弹出来的是原生窗口。 */
    private static OsResult osDialog() throws Exception {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return windowsDialog();
        }
        if (osName.contains("mac")) {
            return macDialog();
        }
        OsResult zenity = linuxDialog("zenity");
        if (zenity.path != null || zenity.cancelled) {
            return zenity;
        }
        OsResult kdialog = linuxDialog("kdialog");
        if (kdialog.path != null || kdialog.cancelled) {
            return kdialog;
        }
        OsResult result = new OsResult();
        result.detail = zenity.detail + " / " + kdialog.detail;
        return result;
    }

    private static OsResult windowsDialog() throws Exception {
        OsResult result = new OsResult();
        Path out = Files.createTempFile("disc-crafting-pick", ".txt");
        String script = "Add-Type -AssemblyName System.Windows.Forms;"
            + "$d = New-Object System.Windows.Forms.OpenFileDialog;"
            + "$d.Title = 'Choose an audio file';"
            + "$d.Filter = 'Audio files (*.mp3;*.wav;*.ogg;*.flac;*.m4a;*.aac)|*.mp3;*.wav;*.ogg;*.flac;*.m4a;*.aac|All files (*.*)|*.*';"
            + "if ($d.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {"
            + "[System.IO.File]::WriteAllText($args[0], $d.FileName) }";
        for (String shell : new String[]{"powershell", "pwsh"}) {
            try {
                Process process = new ProcessBuilder(shell, "-NoProfile", "-STA", "-WindowStyle", "Hidden",
                    "-Command", script, out.toString()).redirectErrorStream(true).start();
                process.getInputStream().readAllBytes();
                int code = process.waitFor();
                String text = Files.exists(out) ? Files.readString(out, StandardCharsets.UTF_8) : "";
                Files.deleteIfExists(out);
                text = text.replace("\uFEFF", "").strip();
                if (!text.isEmpty()) {
                    Path picked = Paths.get(text);
                    lastDir = picked.getParent();
                    result.path = picked;
                    return result;
                }
                if (code == 0) {
                    result.cancelled = true;
                    return result;
                }
                result.detail = "PowerShell 返回 " + code;
                return result;
            } catch (IOException missing) {
                // 换下一种方式
            }
        }
        result.detail = "找不到 powershell";
        return result;
    }

    private static OsResult macDialog() throws Exception {
        OsResult result = new OsResult();
        Process process = new ProcessBuilder("osascript",
            "-e", "set f to (choose file with prompt \"Choose an audio file\")",
            "-e", "POSIX path of f").start();
        byte[] stdout = process.getInputStream().readAllBytes();
        int code = process.waitFor();
        String text = new String(stdout, StandardCharsets.UTF_8).strip();
        if (code == 0 && !text.isEmpty()) {
            Path picked = Paths.get(text);
            lastDir = picked.getParent();
            result.path = picked;
            return result;
        }
        if (code == 1) {
            result.cancelled = true;
            return result;
        }
        result.detail = "osascript 返回 " + code;
        return result;
    }

    private static OsResult linuxDialog(String tool) throws Exception {
        OsResult result = new OsResult();
        List<String> command = new ArrayList<>();
        command.add(tool);
        if ("zenity".equals(tool)) {
            command.add("--file-selection");
            command.add("--title=Choose an audio file");
            command.add("--file-filter=Audio files | *.mp3 *.wav *.ogg *.flac *.m4a *.aac");
        } else {
            command.add("--getopenfilename");
            command.add(System.getProperty("user.home", "."));
            command.add("Audio files (*.mp3 *.wav *.ogg *.flac *.m4a *.aac)");
        }
        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException missing) {
            result.detail = tool + " 没装";
            return result;
        }
        byte[] stdout = process.getInputStream().readAllBytes();
        int code = process.waitFor();
        String text = new String(stdout, StandardCharsets.UTF_8).strip();
        if (code == 0 && !text.isEmpty()) {
            Path picked = Paths.get(text);
            lastDir = picked.getParent();
            result.path = picked;
            return result;
        }
        if (code == 1) {
            result.cancelled = true;
            return result;
        }
        result.detail = tool + " 返回 " + code;
        return result;
    }

    private static boolean looksLikeAudio(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String extension : AUDIO_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }
}
