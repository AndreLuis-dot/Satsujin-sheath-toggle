package com.sheathtoggle;

import com.mojang.logging.LogUtils;
import com.sheathtoggle.network.NetworkHandler;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(SheathToggle.MOD_ID)
public class SheathToggle {
    public static final String MOD_ID = "sheathtoggle";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SheathToggle() {
        NetworkHandler.register();
    }
}
