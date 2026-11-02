package com.sheathtoggle.network;

import com.sheathtoggle.compat.EpicBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: "o jogador apertou a tecla V". Sem dados; o servidor decide pela arma na mao. */
public class ToggleSheathPacket {

    public static void encode(ToggleSheathPacket msg, FriendlyByteBuf buf) {
    }

    public static ToggleSheathPacket decode(FriendlyByteBuf buf) {
        return new ToggleSheathPacket();
    }

    public static void handle(ToggleSheathPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                EpicBridge.toggleStance(sender);
            }
        });
        context.setPacketHandled(true);
    }
}
