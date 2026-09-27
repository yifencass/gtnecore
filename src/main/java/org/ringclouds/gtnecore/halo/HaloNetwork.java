package org.ringclouds.gtnecore.halo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.ringclouds.gtnecore.Gtnecore;

import java.util.function.Supplier;

/**
 * GT之环按键网络：回城（H）/ 紫颂果传送（V）。
 * 两个包均为空载荷，服务端按消息类型执行对应传送逻辑。
 */
public final class HaloNetwork {

    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Gtnecore.MODID, "halo"),
            () -> VERSION, VERSION::equals, VERSION::equals);
    private static int nextId = 0;

    private HaloNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(nextId++, HomePacket.class, HomePacket::encode, HomePacket::decode, HomePacket::handle);
        CHANNEL.registerMessage(nextId++, ChorusPacket.class, ChorusPacket::encode, ChorusPacket::decode, ChorusPacket::handle);
    }

    public static void sendHome() {
        CHANNEL.sendToServer(new HomePacket());
    }

    public static void sendChorus() {
        CHANNEL.sendToServer(new ChorusPacket());
    }

    /** 回城：传送到出生点（床/重生锚点，未设则世界出生点），跨维度直接传送。 */
    public record HomePacket() {
        public static void encode(HomePacket msg, FriendlyByteBuf buf) {
        }

        public static HomePacket decode(FriendlyByteBuf buf) {
            return new HomePacket();
        }

        public static void handle(HomePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null || player.getServer() == null) {
                    return;
                }
                ResourceKey<Level> dim = player.getRespawnDimension();
                ServerLevel level = player.getServer().getLevel(dim);
                if (level == null) {
                    dim = Level.OVERWORLD;
                    level = player.getServer().overworld();
                }
                BlockPos pos = player.getRespawnPosition();
                if (pos == null) {
                    pos = player.getServer().overworld().getSharedSpawnPos();
                }
                player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                        player.getRespawnAngle(), 0.0F);
                playTeleportEffects(level, player);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 紫颂果传送：套用原版 ChorusFruitItem 的传送逻辑（16 次尝试、半径 8、随机落点）。 */
    public record ChorusPacket() {
        public static void encode(ChorusPacket msg, FriendlyByteBuf buf) {
        }

        public static ChorusPacket decode(FriendlyByteBuf buf) {
            return new ChorusPacket();
        }

        public static void handle(ChorusPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) {
                    return;
                }
                ServerLevel level = player.serverLevel();
                RandomSource rand = player.getRandom();
                for (int i = 0; i < 16; i++) {
                    double dx = player.getX() + (rand.nextDouble() - 0.5) * 16.0;
                    double dy = Mth.clamp(player.getY() + (double) (rand.nextInt(16) - 8),
                            level.getMinBuildHeight(),
                            (double) (level.getMinBuildHeight() + level.getLogicalHeight() - 1));
                    double dz = player.getZ() + (rand.nextDouble() - 0.5) * 16.0;
                    if (player.isPassenger()) {
                        player.stopRiding();
                    }
                    if (player.randomTeleport(dx, dy, dz, true)) {
                        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
                        player.playSound(SoundEvents.CHORUS_FRUIT_TELEPORT, 1.0F, 1.0F);
                        return;
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 传送落地效果：紫颂果音效 + 传送门粒子。 */
    private static void playTeleportEffects(ServerLevel level, ServerPlayer player) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.playSound(SoundEvents.CHORUS_FRUIT_TELEPORT, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.PORTAL,
                player.getX(), player.getY(), player.getZ(), 32, 1.0, 1.0, 1.0, 0.5);
    }
}
