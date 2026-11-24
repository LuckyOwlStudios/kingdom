package com.luckyowlstudios.kingdoms;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class KingdomsNeoForge {

    public KingdomsNeoForge(IEventBus eventBus) {
        CommonClass.init();
    }
}
