package com.sheathtoggle;

import com.sheathtoggle.compat.EpicBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/**
 * Mantem o timer de auto-guardar da SatsujinPassive sempre zerado,
 * assim a espada nunca e guardada sozinha.
 */
@Mod.EventBusSubscriber(modid = SheathToggle.MOD_ID)
public class ServerEvents {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        // O timer sobe ~1 por segundo e dispara em 4; zerar a cada 10 ticks (0,5 s) basta.
        if (player.tickCount % 10 != 0) return;
        EpicBridge.suppressAutoSheath(player);
    }
}
