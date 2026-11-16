package cn.nightmare9948.disccrafting.client.audio;

import cn.nightmare9948.disccrafting.DiscCrafting;
import cn.nightmare9948.disccrafting.Lang;
import cn.nightmare9948.disccrafting.client.Settings;
import cn.nightmare9948.disccrafting.component.DiscData;
import cn.nightmare9948.disccrafting.component.DiscSource;
import cn.nightmare9948.disccrafting.entity.WalkmanEntity;
import cn.nightmare9948.disccrafting.net.WalkmanControlPayload;
import cn.nightmare9948.disccrafting.util.HandScan;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.block.JukeboxBlock;
import net.minecraft.block.jukebox.JukeboxSong;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.resource.Resource;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 客户端音频总控：随身听（手持）、唱片机、地上的随身听实体三种播放源。
 * 每 tick 校验来源是否还在，负责距离衰减、暂停/继续、音量、单曲循环与音符粒子。
 * 音符粒子只在“真的在出声”的时候冒：暂停、停止、放完都不会再冒。
 */
public final class ClientAudio {
    private static final ClientAudio INSTANCE = new ClientAudio();

    private static final double JUKEBOX_RANGE = 18.0;
    private static final double WALKMAN_RANGE = 20.0;

    private static final class Session {
        DiscSource source;
        Playback playback;
        /** 这一路是从播放序列来的；null 表示放的是单张唱片。 */
        Playlist playlist;
        BlockPos blockPos;
        UUID entityId;
        /** 唱片机刚放进去的那几 tick，方块状态还没从服务端同步回来，先别急着停。 */
        int grace;
        boolean errorReported;
        /** 地上的随身听可能一开始是暂停的，那个时候还没真正启动过。 */
        boolean started;
        /** 放完之后只通知服务端一次，别每 tick 刷包。 */
        boolean finishedReported;

        Session(DiscSource source, Playback playback) {
            this.source = source;
            this.playback = playback;
        }

        /** 序列里换下一首：停掉旧的，换成新的接着放，其余状态（位置、实体）不动。 */
        void swap(Session fresh, float gain) {
            this.playback.stop();
            this.source = fresh.source;
            this.playback = fresh.playback;
            this.playlist = fresh.playlist;
            this.started = true;
            this.finishedReported = false;
            this.errorReported = false;
            this.playback.setGain(gain);
            this.playback.setFadeEnabled(Settings.fadeEnabled());
            this.playback.start();
        }
    }

    private Session handheld;
    /** 现在手里那支随身听的编号：每支随身听的状态互相独立，靠它区分。 */
    private String handheldKey;
    /** 这个世界里已经试过“恢复上次进度”了，别每 tick 都来一次。 */
    private boolean handheldRestored;
    /** 世界在不在：从“没世界”切到“有世界”时重置上面那个标志。 */
    private boolean worldActive;
    /** 进度写回状态的节流计时。 */
    private int progressTimer;
    private final Map<BlockPos, Session> jukeboxes = new LinkedHashMap<>();
    private final Map<UUID, Session> entities = new LinkedHashMap<>();
    private int scanTimer;

    /** 现在是不是处在“游戏暂停”里（单人按 Esc 的暂停界面）。 */
    private boolean suspendedForPause;
    /** 被“游戏暂停”停住的那几路；退出暂停时只继续这几路，玩家自己按暂停的不动。 */
    private final Set<Session> pausedByGame =
        Collections.newSetFromMap(new IdentityHashMap<>());

    /** 原版唱片音频只从资源里读一次，之后重复播放直接用缓存。 */
    private final Map<String, byte[]> recordCache = new LinkedHashMap<>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > 4;
        }
    };

    private ClientAudio() {
    }

    public static ClientAudio get() {
        return INSTANCE;
    }

    // ---------------------------------------------------------------- 随身听

    /**
     * 手上（主手或副手）那支随身听；没有就返回空栈。
     * 每一支随身听在物品上带着自己的编号，所有设置都按编号分开存。
     */
    public static ItemStack heldWalkman(MinecraftClient client) {
        if (client == null || client.player == null) {
            return ItemStack.EMPTY;
        }
        Hand hand = HandScan.walkmanHand(client.player);
        return hand == null ? ItemStack.EMPTY : client.player.getStackInHand(hand);
    }

    private static String heldKey(MinecraftClient client) {
        ItemStack stack = heldWalkman(client);
        return stack.isEmpty() ? WalkmanStates.UNSIGNED_KEY : WalkmanStates.keyOf(stack);
    }

    /**
     * 现在手里这支随身听的编号。手里换了一支时，先把上一支停住、
     * 把进度记到它自己名下；如果是“刚拿到手、编号还没同步过来”的那一小会儿，
     * 就把临时状态认领成正式编号，不会把正在放的音乐打断。
     */
    private String currentKey(MinecraftClient client) {
        ItemStack stack = heldWalkman(client);
        if (stack.isEmpty()) {
            return WalkmanStates.UNSIGNED_KEY;
        }
        String key = WalkmanStates.keyOf(stack);
        if (this.handheld != null && !key.equals(this.handheldKey)) {
            if (WalkmanStates.UNSIGNED_KEY.equals(this.handheldKey)) {
                WalkmanStates.claim(key);
                this.handheldKey = key;
            } else {
                stopHandheld();
            }
        }
        return key;
    }

    /** 手持随身听自己的那份状态（曲目库、音量、循环、上次进度都在这）。 */
    public static WalkmanStates.State handheldState() {
        return WalkmanStates.of(heldKey(MinecraftClient.getInstance()));
    }

    public int handheldVolumePercent() {
        return handheldState().volumePercent;
    }

    public void setHandheldVolumePercent(int percent) {
        WalkmanStates.State state = handheldState();
        state.volumePercent = Math.max(0, Math.min(200, percent));
        WalkmanStates.markDirty();
        WalkmanStates.flushIfDue();
    }

    public boolean handheldLoop() {
        return handheldState().loop;
    }

    public void setHandheldLoop(boolean loop) {
        WalkmanStates.State state = handheldState();
        state.loop = loop;
        if (this.handheld != null) {
            this.handheld.playback.setLooping(loop);
        }
        WalkmanStates.markDirty();
        WalkmanStates.flushIfDue();
    }

    /**
     * 一个键搞定：没在放就开始，在放就暂停。
     *
     * 换唱片的情况：正在放唱片 1 时把另一只手的唱片换成唱片 2，
     * 按一下先暂停唱片 1（还是原来那首），再按一下才切到唱片 2 从头放。
     * 手里换成另一支随身听时，先把上一支停住记住进度，再按这支自己的进度来。
     */
    public void toggleHandheld() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        ItemStack stack = heldWalkman(client);
        if (stack.isEmpty()) {
            actionBar(Lang.t("message.disc_crafting.need_walkman"));
            return;
        }
        String key = currentKey(client);
        WalkmanStates.State state = WalkmanStates.of(key);
        boolean playlistMode = state.playlist.isEnabled() && !state.playlist.isEmpty();
        if (this.handheld != null && !this.handheld.playback.isFinished()) {
            Playback playback = this.handheld.playback;
            if (playback.isPaused()) {
                DiscSource held = playlistMode ? null : HandScan.heldSource(client.player);
                if (held != null && !held.isEmpty() && !held.equals(this.handheld.source)) {
                    // 手上换了另一张唱片：切过去
                    startHandheld(client, key);
                } else {
                    playback.resume();
                    state.playing = true;
                    WalkmanStates.markDirty();
                }
                return;
            }
            playback.pause();
            saveHandheldState();
            return;
        }
        startHandheld(client, key);
    }

    public void stopHandheld() {
        if (this.handheld != null) {
            saveHandheldState();
            this.handheld.playback.stop();
            this.handheld = null;
        }
        this.handheldKey = null;
    }

    /** 开始放这一支随身听：列表播放 > 手上拿着的唱片 > 上次放到一半的那一首。 */
    private void startHandheld(MinecraftClient client, String key) {
        WalkmanStates.State state = WalkmanStates.of(key);
        Playlist playlist = state.playlist;
        DiscSource remembered = rememberedSource(state);
        Session session;
        if (playlist.isEnabled() && !playlist.isEmpty()) {
            // 列表播放：上次放的那首还在序列里就接着它，否则按序列从头开始
            if (remembered != null && playlist.contains(remembered.trackId())) {
                playlist.setIndex(indexOf(playlist, remembered.trackId()));
            }
            session = startPlaylist(playlist, client.player.getEyePos(), true, state.loop);
            if (session == null) {
                actionBar(Lang.t("message.disc_crafting.missing_audio"));
                return;
            }
            if (remembered != null && sameSource(session.source, remembered)) {
                seekToStart(session.playback, state.positionMs);
            }
        } else {
            DiscSource held = HandScan.heldSource(client.player);
            DiscSource source;
            long resumeMs = 0;
            if (held != null && !held.isEmpty()) {
                source = held;
                // 另一只手还是上次那张唱片：接着上次的进度放
                if (sameSource(held, remembered)) {
                    resumeMs = state.positionMs;
                }
            } else if (remembered != null) {
                // 没有唱片也能接着放：进度记忆里存着上次那一首
                source = remembered;
                resumeMs = state.positionMs;
            } else {
                actionBar(Lang.t("message.disc_crafting.need_disc"));
                return;
            }
            session = createSession(source, client.player.getEyePos(), true, state.loop);
            if (session == null) {
                actionBar(Lang.t(source.isVanilla()
                    ? "message.disc_crafting.missing_song" : "message.disc_crafting.missing_audio"));
                return;
            }
            seekToStart(session.playback, resumeMs);
        }
        stopHandheld();
        session.started = true;
        session.playback.setFadeEnabled(Settings.fadeEnabled());
        session.playback.setGain(handheldGain(client, state));
        session.playback.start();
        this.handheld = session;
        this.handheldKey = key;
        state.playing = true;
        state.trackId = session.source.trackId();
        state.songId = session.source.songId();
        WalkmanStates.markDirty();
    }

    /**
     * 重进游戏之后的第一次：这支随身听上次放到哪一首、第几毫秒，就在这里接着来。
     * 上次是“正在放”就自动接着放；上次是暂停着退出的，就停在原处等玩家按播放。
     */
    private void restoreHandheld(MinecraftClient client, String key, WalkmanStates.State state) {
        DiscSource remembered = rememberedSource(state);
        if (remembered == null) {
            return;
        }
        Playlist playlist = state.playlist;
        Session session;
        if (playlist.isEnabled() && !playlist.isEmpty() && playlist.contains(remembered.trackId())) {
            playlist.setIndex(indexOf(playlist, remembered.trackId()));
            session = createSession(remembered, client.player.getEyePos(), true, state.loop);
            if (session != null) {
                session.playlist = playlist;
                playlist.markPlaying(remembered.trackId());
            }
        } else {
            session = createSession(remembered, client.player.getEyePos(), true, state.loop);
        }
        if (session == null) {
            return;
        }
        session.started = true;
        seekToStart(session.playback, state.positionMs);
        session.playback.setFadeEnabled(Settings.fadeEnabled());
        session.playback.setGain(handheldGain(client, state));
        if (state.playing) {
            session.playback.start();
        } else {
            session.playback.pause();
        }
        this.handheld = session;
        this.handheldKey = key;
    }

    /** 把这一路现在的曲目、进度、播放状态记到这支随身听自己的状态里。 */
    private void saveHandheldState() {
        if (this.handheld == null || this.handheldKey == null) {
            return;
        }
        WalkmanStates.State state = WalkmanStates.of(this.handheldKey);
        DiscSource source = this.handheld.source;
        if (source != null && !source.isEmpty()) {
            state.trackId = source.trackId();
            state.songId = source.songId();
        }
        Playback playback = this.handheld.playback;
        state.positionMs = Math.max(0, playback.positionMs());
        // 按 Esc 停住的那一路只是“被游戏暂停”，玩家其实还在听：重进游戏照旧自动接着放
        state.playing = playback.isActive()
            || (this.suspendedForPause && this.pausedByGame.contains(this.handheld));
        state.loop = playback.isLooping();
        if (this.handheld.playlist != null) {
            // 序列里也记一下“放到第几首”，下次接着走
            String playing = source == null || source.trackId().isEmpty()
                ? this.handheld.playlist.current() : source.trackId();
            int index = indexOf(this.handheld.playlist, playing);
            if (index >= 0) {
                this.handheld.playlist.setIndex(index);
            }
        }
        WalkmanStates.markDirty();
    }

    /** 只要随身听还拿在主手或副手，就一直放，不再管唱片是不是还在手上。 */
    private void tickHandheld(MinecraftClient client) {
        ItemStack stack = heldWalkman(client);
        if (stack.isEmpty()) {
            if (this.handheld != null) {
                stopHandheld();
            }
            return;
        }
        String key = currentKey(client);
        WalkmanStates.State state = WalkmanStates.of(key);
        if (this.handheld == null) {
            if (!this.handheldRestored) {
                this.handheldRestored = true;
                restoreHandheld(client, key, state);
            }
            if (this.handheld == null) {
                return;
            }
        }
        Session session = this.handheld;
        // 这些设置每 tick 都按这支随身听自己的状态来：界面上一改立刻生效
        session.playback.setLooping(state.loop);
        session.playback.setGain(handheldGain(client, state));
        session.playback.setFadeEnabled(Settings.fadeEnabled());
        // 水中音效：人在水里就当手持的随身听泡在水里，进水闷住、出水恢复；
        // 闷多深由设置里的强度滑块决定（0~300%，100% 是默认）
        session.playback.setMuffleStrength(Settings.underwaterStrengthScale());
        session.playback.setMuffled(
            Settings.underwaterSound() && playerInWater(client.player));
        if (this.progressTimer++ % 40 == 0) {
            saveHandheldState();
        }
        if (session.playback.isFinished()) {
            // “是不是列表播放”按此刻的开关算：中途拨开关不打断当前这一首，只影响下一首
            Playlist live = state.playlist;
            Session next = null;
            if (live.isEnabled() && !live.isEmpty()) {
                // 从单张唱片切进列表时，避开正放着的这一首（随机模式下不能原地重复）
                String avoid = trackIdOf(session.source);
                next = session.playlist == null
                    ? startPlaylist(live, client.player.getEyePos(), true, state.loop, avoid)
                    : nextPlaylistSession(live, client.player.getEyePos(), true, state.loop, avoid);
            }
            if (next != null) {
                session.swap(next, handheldGain(client, state));
            } else {
                // 放完了：进度回到开头，下次点播放从头来
                WalkmanStates.State finishedState = WalkmanStates.of(this.handheldKey);
                finishedState.positionMs = 0;
                finishedState.playing = false;
                WalkmanStates.markDirty();
                stopHandheld();
            }
        }
    }

    /** 给界面用：手持随身听当前这一路播放（没在放就是 null）。 */
    public Playback handheldPlayback() {
        return this.handheld == null ? null : this.handheld.playback;
    }

    public DiscSource handheldSource() {
        return this.handheld == null ? null : this.handheld.source;
    }

    /** 手持随身听的播放序列（就是这支随身听自己的那一份）。 */
    public Playlist handheldPlaylist() {
        return WalkmanStates.playlist(heldKey(MinecraftClient.getInstance()));
    }

    /** 地上某台随身听的播放序列。 */
    public Playlist entityPlaylist(WalkmanEntity entity) {
        return entity == null ? new Playlist() : PlaylistStore.entity(entity.getUuid());
    }

    /**
     * 列表播放开关被拨动 / 进了编辑界面之后调一下。
     * 这里什么都不用重开：正在放的那一首继续放，换曲时才按“此刻”的开关走，
     * 所以开关按钮和编辑界面都不会把音乐打断。
     */
    public void reloadHandheld() {
        if (this.handheld != null) {
            this.handheld.playback.setLooping(handheldState().loop);
        }
    }

    /** 地上某台随身听改了列表模式：同样不打断当前这一首。 */
    public void reloadEntity(WalkmanEntity entity) {
        // 不用重建这一路：换曲时读的是此刻的列表状态
        if (entity != null && this.entities.containsKey(entity.getUuid())) {
            Session session = this.entities.get(entity.getUuid());
            session.playback.setLooping(entity.isLooping());
        }
    }

    // ---------------------------------------------------------------- 切歌

    /**
     * 上一首 / 下一首（手持的随身听）。
     * 只有这台随身听开着列表播放、列表里有曲目时才生效；返回是不是真的切了。
     *
     * @param direction -1 = 上一首，1 = 下一首
     */
    public boolean skipHandheld(int direction) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || HandScan.walkmanHand(client.player) == null) {
            return false;
        }
        WalkmanStates.State state = WalkmanStates.of(heldKey(client));
        Playlist playlist = state.playlist;
        if (!playlist.isEnabled() || playlist.isEmpty()) {
            return false;
        }
        Session session = this.handheld;
        Session fresh = buildSkip(playlist, session, direction,
            client.player.getEyePos(), true, state.loop);
        if (fresh == null) {
            actionBar(Lang.t("message.disc_crafting.missing_audio"));
            return false;
        }
        if (session == null) {
            fresh.started = true;
            fresh.playback.setFadeEnabled(Settings.fadeEnabled());
            fresh.playback.setGain(handheldGain(client, state));
            fresh.playback.start();
            this.handheld = fresh;
            this.handheldKey = heldKey(client);
            return true;
        }
        boolean silent = !session.playback.isActive();
        session.swap(fresh, handheldGain(client, state));
        if (silent) {
            // 原来是暂停 / 停着的：换到新的一首但先不出声，等玩家点播放
            session.playback.pause();
        }
        return true;
    }

    /** 上一首 / 下一首（放在地上的随身听）。 */
    public boolean skipEntity(WalkmanEntity entity, int direction) {
        if (entity == null) {
            return false;
        }
        Playlist playlist = PlaylistStore.entity(entity.getUuid());
        if (!playlist.isEnabled() || playlist.isEmpty()) {
            return false;
        }
        Session session = this.entities.get(entity.getUuid());
        Session fresh = buildSkip(playlist, session, direction,
            entity.getPos(), false, entity.isLooping());
        if (fresh == null) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        float gain = deviceGain(client) * entity.getVolume();
        if (session == null) {
            fresh.entityId = entity.getUuid();
            fresh.started = true;
            fresh.playback.setGain(gain);
            if (entity.isPlaying()) {
                fresh.playback.start();
            } else {
                fresh.playback.pause();
            }
            this.entities.put(entity.getUuid(), fresh);
            return true;
        }
        boolean silent = !session.playback.isActive();
        session.swap(fresh, gain);
        if (silent) {
            session.playback.pause();
        }
        return true;
    }

    /**
     * 按方向挑出序列里的下一首 / 上一首；挑不到（音频不在本机）返回 null。
     * 之前放的是单张唱片的，就从序列的头（下一首）或尾（上一首）接上。
     */
    private Session buildSkip(Playlist playlist, Session session, int direction,
                              Vec3d pos, boolean followPlayer, boolean loop) {
        if (session == null || session.playlist == null) {
            // 正放的是单张唱片（或还没开始）：从序列的头 / 尾接上，
            // 并且避开正在放的那一首，随机模式下不会“下一首还是它”
            String avoid = session == null ? null : trackIdOf(session.source);
            playlist.reset(avoid);
            if (direction < 0) {
                if (playlist.order() == Playlist.Order.SHUFFLE) {
                    playlist.retreat();
                } else if (playlist.size() > 0) {
                    playlist.setIndex(playlist.size() - 1);
                }
            }
            return startPlaylist(playlist, pos, followPlayer, loop, avoid);
        }
        int attempts = Math.max(1, playlist.size());
        for (int i = 0; i < attempts; i++) {
            boolean moved = direction < 0 ? playlist.retreat() : playlist.advance();
            if (!moved) {
                // 顺序播放已经走到头：整条序列从头再走一遍
                playlist.reset(trackIdOf(session.source));
                if (direction < 0 && playlist.size() > 0) {
                    playlist.setIndex(playlist.size() - 1);
                }
            }
            Session candidate = playlistTrack(playlist, pos, followPlayer, loop);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    /** 给界面用：地上某台随身听当前这一路播放。 */
    public Playback entityPlayback(WalkmanEntity entity) {
        if (entity == null) {
            return null;
        }
        Session session = this.entities.get(entity.getUuid());
        return session == null ? null : session.playback;
    }

    public DiscSource entitySource(WalkmanEntity entity) {
        if (entity == null) {
            return null;
        }
        Session session = this.entities.get(entity.getUuid());
        return session == null ? null : session.source;
    }

    // ---------------------------------------------------------------- 唱片机

    public void onJukeboxInsert(BlockPos pos, DiscData data) {
        BlockPos key = pos.toImmutable();
        Session old = this.jukeboxes.remove(key);
        if (old != null) {
            old.playback.stop();
        }
        Session session = createSession(new DiscSource(data.trackId(), ""), key.toCenterPos(), false, false);
        if (session == null) {
            actionBar(Lang.t("message.disc_crafting.missing_audio"));
            return;
        }
        session.blockPos = key;
        session.grace = 60;
        session.started = true;
        session.playback.start();
        this.jukeboxes.put(key, session);
    }

    // ---------------------------------------------------------------- 每 tick

    public void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world == null || client.player == null) {
            if (this.worldActive) {
                // 退出世界 / 回主菜单：把进度和设置写进硬盘，下次进来接着来
                this.worldActive = false;
                stopAll();
            }
            return;
        }
        if (!this.worldActive) {
            this.worldActive = true;
            this.handheldRestored = false;
        }
        // 游戏暂停了（单人按 Esc 的那个暂停界面）：模组这边所有声音一起停住，
        // 进度、音量、播放列表都原地不动；退出暂停再接着放，不会从头开始。
        if (isGamePaused(client)) {
            suspendForPause();
            saveHandheldState();
            WalkmanStates.flushIfDue();
            return;
        }
        if (this.suspendedForPause) {
            resumeAfterPause();
        }
        float master = masterGain(client);
        // 放在地上的随身听音量归它自己管（0~200% 的滑块），所以这里不叠全局的随身听音量，
        // 这样“继承”过来的音量和手里那台听起来一样响。
        float device = deviceGain(client);

        tickHandheld(client);
        if (this.handheld != null) {
            Session session = this.handheld;
            reportError(session);
            if (session.playback.isActive() && handheldGain(client, handheldState()) > 0.02f) {
                spawnNotesIfDue(world, headTop(client.player));
            }
        }
        WalkmanStates.flushIfDue();
        EntityProgress.flushIfDue();

        Iterator<Map.Entry<BlockPos, Session>> iterator = this.jukeboxes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Session> entry = iterator.next();
            BlockPos pos = entry.getKey();
            Session session = entry.getValue();
            if (session.grace > 0) {
                session.grace--;
            } else {
                boolean stillThere = world.getBlockState(pos).isOf(Blocks.JUKEBOX)
                    && world.getBlockState(pos).get(JukeboxBlock.HAS_RECORD);
                if (!stillThere || session.playback.isFinished()) {
                    session.playback.stop();
                    iterator.remove();
                    continue;
                }
            }
            reportError(session);
            session.playback.setFadeEnabled(Settings.fadeEnabled());
            float gain = master * (float) distanceGain(pos.toCenterPos(), client.player.getEyePos(), JUKEBOX_RANGE);
            session.playback.setGain(gain);
            if (session.playback.isActive() && gain > 0.02f) {
                spawnNotesIfDue(world, pos.toCenterPos().add(0, 1.1, 0));
            }
        }

        this.scanTimer++;
        if (this.scanTimer % 5 == 0) {
            scanWalkmanEntities(client);
        }
        for (Map.Entry<UUID, Session> entry : new ArrayList<>(this.entities.entrySet())) {
            Session session = entry.getValue();
            WalkmanEntity entity = findEntity(world, entry.getKey());
            if (entity == null || !entity.isAlive()) {
                session.playback.stop();
                this.entities.remove(entry.getKey());
                continue;
            }
            // 地上那台随身听自己的播放状态：暂停就真的停住，继续就接着放
            session.playback.setLooping(entity.isLooping());
            session.playback.setFadeEnabled(Settings.fadeEnabled());
            // 水漫到这台随身听身上时，声音跟着闷住；水退了恢复，深浅同样看强度设置
            session.playback.setMuffleStrength(Settings.underwaterStrengthScale());
            session.playback.setMuffled(
                Settings.underwaterSound() && entityInWater(world, entity));
            // 每台随身听的实体还带着自己的音量（0~200%），跟在界面上调的那个一致
            float reach = (float) distanceGain(entity.getPos(), client.player.getEyePos(), WALKMAN_RANGE);
            float gain = device * entity.getVolume() * (session.playback.handlesDistance() ? 1.0f : reach);
            if (entity.isPlaying()) {
                if (!session.started) {
                    session.started = true;
                    session.playback.start();
                } else if (session.playback.isPaused()) {
                    session.playback.resume();
                } else if (session.playback.isFinished()) {
                    // 一首放完：按“此刻”的列表开关决定换下一首还是停下
                    Playlist live = PlaylistStore.entity(entity.getUuid());
                    Session next = null;
                    if (live.isEnabled() && !live.isEmpty()) {
                        String avoid = trackIdOf(session.source);
                        next = session.playlist == null
                            ? startPlaylist(live, entity.getPos(), false, entity.isLooping(), avoid)
                            : nextPlaylistSession(live, entity.getPos(), false, entity.isLooping(), avoid);
                    }
                    if (next != null) {
                        session.swap(next, gain);
                    } else if (!session.finishedReported) {
                        session.finishedReported = true;
                        ClientPlayNetworking.send(new WalkmanControlPayload(
                            entity.getId(), false, entity.getVolume(), entity.isLooping(),
                            entity.getTrackId(), entity.getSongId()));
                        continue;
                    } else {
                        continue;
                    }
                }
            } else if (!session.playback.isPaused()) {
                session.playback.pause();
            }
            if (session.playback.isFinished() && !session.playback.isLooping()) {
                // 放完了：让服务端把这台随身听标成“已暂停”，进度条停在末尾，
                // 下次点播放再从头放，而不是自动重来一遍。
                if (!session.finishedReported) {
                    session.finishedReported = true;
                    ClientPlayNetworking.send(new WalkmanControlPayload(
                        entity.getId(), false, entity.getVolume(), entity.isLooping(),
                        entity.getTrackId(), entity.getSongId()));
                }
                continue;
            }
            if (!session.playback.isFinished()) {
                session.finishedReported = false;
            }
            boolean audible = session.playback.isActive() && entity.getVolume() > 0.02f && reach > 0.02f;
            session.playback.setGain(gain);
            reportError(session);
            if (audible) {
                spawnNotesIfDue(world, entity.getPos().add(0, entity.getHeight() + 0.1, 0));
            }
            // 地上这台“放到哪儿了”也记一份：退出游戏再进来能接着放
            if (world.getTime() % 40 == 0) {
                EntityProgress.remember(entity.getUuid(), session.source, session.playback.positionMs());
            }
        }
    }

    /**
     * 附近有随身听就给它备好一路播放（暂停状态的也备着，这样点播放能接着放、
     * 进度条也能直接拖）。
     */
    private void scanWalkmanEntities(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world == null || client.player == null) {
            return;
        }
        List<WalkmanEntity> found = world.getEntitiesByClass(WalkmanEntity.class,
            client.player.getBoundingBox().expand(48.0), WalkmanEntity::isAlive);
        for (WalkmanEntity entity : found) {
            if (this.entities.containsKey(entity.getUuid())) {
                continue;
            }
            // 刚放下的随身听：把手里那台的播放列表、音量、单曲循环整份接过来
            WalkmanInheritance.Snapshot inherited = WalkmanInheritance.take(entity);
            if (inherited != null) {
                PlaylistStore.adopt(entity.getUuid(), inherited.playlist());
                applyInheritedSettings(entity, inherited);
            }
            Playlist playlist = PlaylistStore.entity(entity.getUuid());
            Session session;
            if (playlist.isEnabled() && !playlist.isEmpty()) {
                session = startPlaylist(playlist, entity.getPos(), false, entity.isLooping());
            } else {
                DiscSource source = new DiscSource(entity.getTrackId(), entity.getSongId());
                if (source.isEmpty()) {
                    continue;
                }
                session = createSession(source, entity.getPos(), false, entity.isLooping());
            }
            if (session == null) {
                continue;
            }
            session.entityId = entity.getUuid();
            // 上次退出游戏时这台放到哪儿了？曲目对得上就接着放
            long savedPosition = EntityProgress.positionFor(entity.getUuid(), session.source);
            if (savedPosition > 0) {
                seekToStart(session.playback, savedPosition);
            }
            session.playback.setFadeEnabled(Settings.fadeEnabled());
            session.playback.setGain(deviceGain(client) * entity.getVolume());
            if (entity.isPlaying()) {
                session.started = true;
                session.playback.start();
            } else {
                session.playback.pause();
            }
            this.entities.put(entity.getUuid(), session);
        }
    }

    /**
     * 继承列表时把“一切设置”也带过去：这台随身听的音量和单曲循环跟手上那台保持一致；
     * 空手放置时连正在放的那一首也一起照抄，但放下时是停着的，等玩家按播放。
     *
     * 音量直接写在这台实体身上（本机立刻生效，界面读下去就是手里那台的音量），
     * 再用控制包告诉服务端，让别的地方看到的也是同一个值。
     * 以前只发控制包、不等回包，实体上还是旧的默认值（0），看起来就像“继承过来是 0”。
     */
    private static void applyInheritedSettings(WalkmanEntity entity, WalkmanInheritance.Snapshot inherited) {
        float volume = Math.max(0.0f, Math.min(2.0f, inherited.volumePercent() / 100.0f));
        boolean loop = inherited.loop();
        entity.setVolume(volume);
        entity.setLooping(loop);
        if (inherited.inheritTrack()) {
            // 另一只手空着放的：曲目照抄手上那台，但放下时默认是停止播放
            entity.setTrackId(inherited.trackId());
            entity.setSongId(inherited.songId());
            // 继承过来的这台默认停着：想听就自己按播放
            entity.setPlaying(false);
        }
        // 曲目一起交给服务端：本机曲目编号只有客户端知道，不发过去就存不住
        ClientPlayNetworking.send(new WalkmanControlPayload(
            entity.getId(), entity.isPlaying(), volume, loop,
            entity.getTrackId(), entity.getSongId()));
        if (!inherited.playlist().isEmpty()) {
            actionBar(Lang.t("message.disc_crafting.playlist_inherited", inherited.playlist().size()));
        } else if (inherited.inheritTrack() && !(inherited.trackId().isEmpty() && inherited.songId().isEmpty())) {
            actionBar(Lang.t("message.disc_crafting.track_inherited"));
        }
    }

    private static WalkmanEntity findEntity(ClientWorld world, UUID uuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return null;
        }
        List<WalkmanEntity> nearby = world.getEntitiesByClass(WalkmanEntity.class,
            client.player.getBoundingBox().expand(64.0), entity -> entity.getUuid().equals(uuid));
        return nearby.isEmpty() ? null : nearby.get(0);
    }

    public void stopAll() {
        // 每一路都先把“放到哪儿了”记下来再停，退出游戏后下次能接着来
        saveHandheldState();
        for (Map.Entry<UUID, Session> entry : this.entities.entrySet()) {
            Session session = entry.getValue();
            EntityProgress.remember(entry.getKey(), session.source, session.playback.positionMs());
        }
        stopHandheld();
        for (Session session : this.jukeboxes.values()) {
            session.playback.stop();
        }
        this.jukeboxes.clear();
        for (Session session : this.entities.values()) {
            session.playback.stop();
        }
        this.entities.clear();
        this.pausedByGame.clear();
        this.suspendedForPause = false;
        this.handheldRestored = false;
        WalkmanStates.saveNow();
        EntityProgress.saveNow();
    }

    // ---------------------------------------------------------------- 游戏暂停

    /**
     * 现在是不是“游戏暂停”状态。
     * 单人里按 Esc 打开暂停界面、或者别的会把游戏停住的界面，都算；
     * 多人游戏里按 Esc 不会真暂停世界，但也按暂停处理，免得退出时声音还在响。
     */
    private static boolean isGamePaused(MinecraftClient client) {
        if (client == null) {
            return false;
        }
        if (client.isPaused()) {
            return true;
        }
        return client.currentScreen instanceof GameMenuScreen;
    }

    /** 暂停游戏：把正在出声的每一路都停住（原地暂停，不是关掉）。 */
    private void suspendForPause() {
        this.suspendedForPause = true;
        for (Session session : allSessions()) {
            if (session.playback.isActive()) {
                session.playback.pause();
                this.pausedByGame.add(session);
            }
        }
    }

    /** 退出暂停：只把刚才被暂停游戏停住的那几路继续放，别的状态一概不动。 */
    private void resumeAfterPause() {
        this.suspendedForPause = false;
        for (Session session : this.pausedByGame) {
            if (isLive(session)) {
                session.playback.resume();
            }
        }
        this.pausedByGame.clear();
    }

    private List<Session> allSessions() {
        List<Session> all = new ArrayList<>(1 + this.jukeboxes.size() + this.entities.size());
        if (this.handheld != null) {
            all.add(this.handheld);
        }
        all.addAll(this.jukeboxes.values());
        all.addAll(this.entities.values());
        return all;
    }

    /** 这一路还在用吗（没有被停掉、也没有跟着唱片机或实体一起消失）。 */
    private boolean isLive(Session session) {
        return session == this.handheld
            || this.jukeboxes.containsValue(session)
            || this.entities.containsValue(session);
    }

    // ---------------------------------------------------------------- 工具

    /** 从播放序列当前这首开始放；序列里的音频在本机都找不到就返回 null。 */
    private Session startPlaylist(Playlist playlist, Vec3d pos, boolean followPlayer, boolean loop) {
        return startPlaylist(playlist, pos, followPlayer, loop, null);
    }

    /**
     * 从播放序列当前这首开始放。
     *
     * @param avoidTrackId 万一序列要“从头开始”，尽量挑一首不是它的（随机模式用）
     */
    private Session startPlaylist(Playlist playlist, Vec3d pos, boolean followPlayer, boolean loop,
                                  String avoidTrackId) {
        if (playlist == null || playlist.isEmpty()) {
            return null;
        }
        if (playlist.current() == null) {
            playlist.reset(avoidTrackId);
        }
        for (int attempt = 0; attempt <= playlist.size(); attempt++) {
            Session session = playlistTrack(playlist, pos, followPlayer, loop);
            if (session != null) {
                return session;
            }
            if (!playlist.advance()) {
                break;
            }
        }
        return null;
    }

    /** 按顺序规则换下一首；顺序播放走到最后一首就返回 null。 */
    private Session nextPlaylistSession(Playlist playlist, Vec3d pos, boolean followPlayer, boolean loop,
                                        String avoidTrackId) {
        if (playlist == null || playlist.isEmpty()) {
            return null;
        }
        if (playlist.current() == null) {
            // 上一轮已经放完（或被清空过）：整条序列从头来
            return startPlaylist(playlist, pos, followPlayer, loop, avoidTrackId);
        }
        for (int attempt = 0; attempt < playlist.size(); attempt++) {
            if (!playlist.advance()) {
                return null;
            }
            Session session = playlistTrack(playlist, pos, followPlayer, loop);
            if (session != null) {
                return session;
            }
        }
        return null;
    }

    /** 把序列当前这一首装成一路播放；音频没了就返回 null。 */
    private Session playlistTrack(Playlist playlist, Vec3d pos, boolean followPlayer, boolean loop) {
        String trackId = playlist.current();
        if (trackId == null) {
            return null;
        }
        Session session = createSession(new DiscSource(trackId, ""), pos, followPlayer, loop);
        if (session != null) {
            // 记下“真的开始放的是哪一首”，随机挑下一首时排掉它
            playlist.markPlaying(trackId);
            session.playlist = playlist;
        }
        return session;
    }

    /** 状态里记的那一首（上次放到一半的）；没记过就返回 null。 */
    private static DiscSource rememberedSource(WalkmanStates.State state) {
        if (state == null) {
            return null;
        }
        DiscSource source = new DiscSource(state.trackId, state.songId);
        return source.isEmpty() ? null : source;
    }

    private static boolean sameSource(DiscSource first, DiscSource second) {
        if (first == null || second == null) {
            return false;
        }
        return first.trackId().equals(second.trackId()) && first.songId().equals(second.songId());
    }

    /** 某个曲目在序列里排第几；不在序列里就返回 -1。 */
    private static int indexOf(Playlist playlist, String trackId) {
        if (playlist == null || trackId == null || trackId.isEmpty()) {
            return -1;
        }
        List<String> ids = playlist.trackIds();
        for (int i = 0; i < ids.size(); i++) {
            if (trackId.equals(ids.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /** 恢复进度：位置得合理（在曲子中间，不是只剩最后两秒）才跳过去。 */
    private static void seekToStart(Playback playback, long positionMs) {
        if (playback == null || positionMs <= 0 || !playback.canSeek()) {
            return;
        }
        long duration = playback.durationMs();
        if (duration > 0 && positionMs > duration - 1500L) {
            return;
        }
        playback.seekTo(positionMs);
    }

    /** 这张唱片对应哪个曲目编号（本地曲目用 trackId，原版唱片用 songId），用来排除重复。 */
    private static String trackIdOf(DiscSource source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        return source.trackId().isEmpty() ? source.songId() : source.trackId();
    }

    /** 按“这张唱片能放什么”造一路播放；放不了就返回 null。 */
    private Session createSession(DiscSource source, Vec3d pos, boolean followPlayer, boolean loop) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        if (!source.trackId().isEmpty()) {
            Track track = TrackStore.get(source.trackId());
            if (track == null) {
                return null;
            }
            Path file = TrackStore.fileOf(track);
            StreamPlayback playback = new StreamPlayback(() -> new WavSource(file), track.durationMs);
            playback.setLooping(loop);
            playback.setFadeEnabled(Settings.fadeEnabled());
            return new Session(source, playback);
        }
        JukeboxSong song = resolveSong(source.songId());
        if (song == null) {
            return null;
        }
        long durationMs = Math.max(0, song.getLengthInTicks() * 50L);
        byte[] ogg = recordAudio(source.songId(), song);
        if (ogg != null && OggAssetSource.probe(ogg)) {
            StreamPlayback playback = new StreamPlayback(
                () -> new OggAssetSource(new ByteArrayInputStream(ogg)), durationMs);
            playback.setLooping(loop);
            playback.setFadeEnabled(Settings.fadeEnabled());
            return new Session(source, playback);
        }
        // 实在读不到音频文件，退回到游戏自己的声音系统（没有进度和拖动）
        return new Session(source, new VanillaRecordPlayback(
            song.soundEvent().value(), pos, followPlayer, durationMs));
    }

    /**
     * 从游戏资源里把原版唱片的 ogg 读出来（只读一次，之后用缓存）。
     * 拿不到就返回 null，让上层退回声音系统播放。
     */
    private byte[] recordAudio(String songId, JukeboxSong song) {
        synchronized (this.recordCache) {
            byte[] cached = this.recordCache.get(songId);
            if (cached != null) {
                return cached;
            }
        }
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            Identifier eventId = song.soundEvent().value().getId();
            WeightedSoundSet set = client.getSoundManager().get(eventId);
            if (set == null) {
                return null;
            }
            Sound sound = set.getSound(Random.create());
            if (sound == null) {
                return null;
            }
            Identifier location = sound.getLocation();
            Resource resource = client.getResourceManager().getResource(location).orElse(null);
            if (resource == null) {
                DiscCrafting.LOGGER.info("找不到原版唱片音频 {}（{}），退回声音系统播放", location, songId);
                return null;
            }
            byte[] bytes;
            try (InputStream input = resource.getInputStream()) {
                bytes = input.readAllBytes();
            }
            synchronized (this.recordCache) {
                this.recordCache.put(songId, bytes);
            }
            return bytes;
        } catch (Throwable throwable) {
            DiscCrafting.LOGGER.warn("读取原版唱片音频失败：{}", songId, throwable);
            return null;
        }
    }

    /** 按编号查原版唱片曲目；查不到返回 null。 */
    public static JukeboxSong resolveSong(String songId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || songId == null || songId.isEmpty()) {
            return null;
        }
        Identifier id = Identifier.tryParse(songId);
        if (id == null) {
            return null;
        }
        try {
            RegistryEntry<JukeboxSong> entry = client.world.getRegistryManager()
                .get(RegistryKeys.JUKEBOX_SONG).getEntry(id).orElse(null);
            return entry == null ? null : entry.value();
        } catch (Throwable throwable) {
            DiscCrafting.LOGGER.debug("查不到原版唱片曲目 {}", songId, throwable);
            return null;
        }
    }

    /** 界面显示用：这张唱片叫什么。 */
    public static String discTitle(DiscSource source) {
        if (source == null || source.isEmpty()) {
            return "-";
        }
        if (!source.trackId().isEmpty()) {
            Track track = TrackStore.get(source.trackId());
            return track != null ? track.title : source.trackId();
        }
        JukeboxSong song = resolveSong(source.songId());
        return song != null ? song.description().getString() : source.songId();
    }

    /** 界面显示用：这张唱片多长（毫秒）。 */
    public static long discDurationMs(DiscSource source) {
        if (source == null || source.isEmpty()) {
            return 0;
        }
        if (!source.trackId().isEmpty()) {
            Track track = TrackStore.get(source.trackId());
            return track != null ? track.durationMs : 0;
        }
        JukeboxSong song = resolveSong(source.songId());
        return song != null ? Math.max(0, song.getLengthInTicks() * 50L) : 0;
    }

    /** 唱片机用的音量：模组设置里的默认音量 x 游戏自己的“唱片 / 音乐”音量。 */
    private static float masterGain(MinecraftClient client) {
        return Settings.volumeGain() * client.options.getSoundVolume(SoundCategory.RECORDS);
    }

    /** 手持随身听用的音量：这支随身听自己的 0~200% x 游戏自己的“唱片 / 音乐”音量。 */
    private static float handheldGain(MinecraftClient client, WalkmanStates.State state) {
        int percent = state == null ? 100 : Math.max(0, Math.min(200, state.volumePercent));
        return (percent / 100.0f) * client.options.getSoundVolume(SoundCategory.RECORDS);
    }

    /**
     * 放在地上的随身听用的基准音量：只算游戏自己的“唱片 / 音乐”音量，
     * 不再乘模组的全局随身听音量 —— 那台设备有自己的 0~200% 音量。
     */
    private static float deviceGain(MinecraftClient client) {
        return client.options.getSoundVolume(SoundCategory.RECORDS);
    }

    /** 声卡打不开（比如没装音频驱动）时，至少让玩家知道原因。 */
    private static void reportError(Session session) {
        String error = session.playback.error();
        if (error != null && !session.errorReported) {
            session.errorReported = true;
            actionBar(Lang.t("message.disc_crafting.audio_error", error));
        }
    }

    /** 玩家本人泡在水里（手持的随身听跟着人走，所以看人就行）。 */
    private static boolean playerInWater(PlayerEntity player) {
        return player != null && player.isTouchingWater();
    }

    /**
     * 地上那台随身听是不是被水盖住了。
     * 它本身不放水里（右键会拒绝），但水可以流过来把它淹掉，这时候也要闷住。
     */
    private static boolean entityInWater(ClientWorld world, WalkmanEntity entity) {
        if (entity.isTouchingWater()) {
            return true;
        }
        BlockPos pos = entity.getBlockPos();
        return world.getFluidState(pos).isIn(FluidTags.WATER)
            || world.getFluidState(pos.up()).isIn(FluidTags.WATER);
    }

    private static double distanceGain(Vec3d source, Vec3d listener, double range) {
        double distance = source.distanceTo(listener);
        if (distance >= range) {
            return 0.0;
        }
        double factor = 1.0 - distance / range;
        return factor * factor;
    }

    /** 玩家头顶的位置：音符粒子从这儿往上飘，不再挡着眼睛。 */
    private static Vec3d headTop(PlayerEntity player) {
        return player.getPos().add(0.0, player.getHeight() + 0.12, 0.0);
    }

    /**
     * 按模组设置决定这会儿冒不冒音符粒子、冒什么样子的：
     * 速度决定隔几 tick 冒一次，样式决定是单个音符、绕一圈还是星尘。
     */
    private void spawnNotesIfDue(ClientWorld world, Vec3d pos) {
        if (!Settings.noteParticles()) {
            return;
        }
        if (world.getTime() % Math.max(1, Settings.particleIntervalTicks()) != 0) {
            return;
        }
        switch (Settings.particleStyle()) {
            case 1 -> {
                // 环绕：在头顶绕着小圈飘
                double angle = (world.getTime() % 80) / 80.0 * Math.PI * 2.0;
                for (int i = 0; i < 3; i++) {
                    double a = angle + i * (Math.PI * 2.0 / 3.0);
                    world.addParticle(ParticleTypes.NOTE,
                        pos.x + Math.cos(a) * 0.42,
                        pos.y + 0.05 + i * 0.1,
                        pos.z + Math.sin(a) * 0.42,
                        world.random.nextDouble(), 0.02, 0.0);
                }
            }
            case 2 -> {
                // 星尘：细细的点，比音符低调
                for (int i = 0; i < 2; i++) {
                    world.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        pos.x + (world.random.nextDouble() - 0.5) * 0.5,
                        pos.y + world.random.nextDouble() * 0.3,
                        pos.z + (world.random.nextDouble() - 0.5) * 0.5,
                        0.0, 0.015, 0.0);
                }
            }
            default -> {
                // 经典音符：一个音符号往上飘
                double random = world.random.nextDouble();
                world.addParticle(ParticleTypes.NOTE,
                    pos.x + (world.random.nextDouble() - 0.5) * 0.4,
                    pos.y + world.random.nextDouble() * 0.25,
                    pos.z + (world.random.nextDouble() - 0.5) * 0.4,
                    random, random, random);
            }
        }
    }

    private static void actionBar(Text text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(text, true);
        } else {
            DiscCrafting.LOGGER.info(text.getString());
        }
    }

}
