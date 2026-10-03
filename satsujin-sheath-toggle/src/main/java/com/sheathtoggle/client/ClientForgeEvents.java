package com.sheathtoggle.client;

import com.sheathtoggle.SheathToggle;
import com.sheathtoggle.network.NetworkHandler;
import com.sheathtoggle.network.ToggleSheathPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SheathToggle.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientForgeEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (ClientModEvents.TOGGLE_KEY.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                NetworkHandler.CHANNEL.sendToServer(new ToggleSheathPacket());
            }
        }
    }
}
