package cn.nightmare9948.disccrafting.entity;

import cn.nightmare9948.disccrafting.ClientBridge;
import cn.nightmare9948.disccrafting.ModItems;
import cn.nightmare9948.disccrafting.component.DiscData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * 放置在地上的随身听：一个不会动的实体，附近玩家会听到它正在放的曲子，
 * 同时飘出音符粒子。
 * 右键打开它自己的控制界面（播放 / 暂停 / 音量），左键敲一下收回物品。
 */
public class WalkmanEntity extends Entity {
    private static final TrackedData<String> TRACK_ID =
        DataTracker.registerData(WalkmanEntity.class, TrackedDataHandlerRegistry.STRING);
    /** 原版唱片曲目编号（比如 minecraft:cat），用原版唱片时才有值。 */
    private static final TrackedData<String> SONG_ID =
        DataTracker.registerData(WalkmanEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Boolean> PLAYING =
        DataTracker.registerData(WalkmanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> VOLUME =
        DataTracker.registerData(WalkmanEntity.class, TrackedDataHandlerRegistry.FLOAT);
    /** 单曲循环开关，跟着这台随身听走。 */
    private static final TrackedData<Boolean> LOOP =
        DataTracker.registerData(WalkmanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    public WalkmanEntity(EntityType<? extends Entity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(TRACK_ID, "");
        builder.add(SONG_ID, "");
        builder.add(PLAYING, true);
        builder.add(VOLUME, 1.0f);
        builder.add(LOOP, false);
    }

    public String getTrackId() {
        return this.dataTracker.get(TRACK_ID);
    }

    public void setTrackId(String trackId) {
        this.dataTracker.set(TRACK_ID, trackId == null ? "" : trackId);
    }

    public String getSongId() {
        return this.dataTracker.get(SONG_ID);
    }

    public void setSongId(String songId) {
        this.dataTracker.set(SONG_ID, songId == null ? "" : songId);
    }

    public boolean isPlaying() {
        return this.dataTracker.get(PLAYING);
    }

    public void setPlaying(boolean playing) {
        this.dataTracker.set(PLAYING, playing);
    }

    /** 0~2 倍音量，只影响这一台随身听，不动全局设置。 */
    public float getVolume() {
        return this.dataTracker.get(VOLUME);
    }

    public void setVolume(float volume) {
        this.dataTracker.set(VOLUME, Math.max(0.0f, Math.min(2.0f, volume)));
    }

    /** 这一台随身听是不是单曲循环。 */
    public boolean isLooping() {
        return this.dataTracker.get(LOOP);
    }

    public void setLooping(boolean looping) {
        this.dataTracker.set(LOOP, looping);
    }

    // 音符粒子不在这里冒：改由客户端音频层在“确实在出声”时触发，
    // 这样暂停、静音、放完、离得太远都不会再飘音符。

    /** 右键：客户端打开这台随身听自己的控制界面。 */
    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (this.getWorld().isClient) {
            ClientBridge.openWalkmanScreen(this);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (!this.getWorld().isClient && source.getAttacker() != null) {
            ItemStack stack = new ItemStack(ModItems.WALKMAN);
            this.dropStack(stack);
            this.discard();
        }
        return true;
    }

    @Override
    public boolean canHit() {
        return true;
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    /** 用来给渲染层和音频层判断当前曲目是否还在。 */
    public DiscData peekDiscData() {
        String id = this.getTrackId();
        return id.isEmpty() ? null : new DiscData(id, "", 1, 0);
    }

    /** 这台随身听正在放的是不是原版唱片。 */
    public boolean isVanillaRecord() {
        return !this.getSongId().isEmpty();
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.setTrackId(nbt.getString("Track"));
        this.setSongId(nbt.getString("Song"));
        this.setPlaying(!nbt.contains("Playing") || nbt.getBoolean("Playing"));
        this.setVolume(nbt.contains("Volume") ? nbt.getFloat("Volume") : 1.0f);
        this.setLooping(nbt.getBoolean("Loop"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putString("Track", this.getTrackId());
        nbt.putString("Song", this.getSongId());
        nbt.putBoolean("Playing", this.isPlaying());
        nbt.putFloat("Volume", this.getVolume());
        nbt.putBoolean("Loop", this.isLooping());
    }
}
