package com.sheathtoggle.network;

import com.sheathtoggle.SheathToggle;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SheathToggle.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    public static void register() {
        CHANNEL.registerMessage(0, ToggleSheathPacket.class,
                ToggleSheathPacket::encode,
                ToggleSheathPacket::decode,
                ToggleSheathPacket::handle);
    }
}
