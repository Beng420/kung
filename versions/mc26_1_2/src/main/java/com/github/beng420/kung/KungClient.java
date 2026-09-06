package com.github.beng420.kung;

import com.github.beng420.kung.command.KungCommands;
import com.github.beng420.kung.feature.dungeon.DungeonMapFeature;
import com.github.beng420.kung.feature.dungeon.DungeonMapOverlayConfig;
import com.github.beng420.kung.feature.dungeon.DungeonSplitsOverlayFeature;
import com.github.beng420.kung.feature.dungeon.DungeonStateTracker;
import com.github.beng420.kung.feature.dungeon.DungeonChatFilterFeature;
import com.github.beng420.kung.feature.misc.Ca50ChatCommandFeature;
import com.github.beng420.kung.feature.misc.CustomSoundsFeature;
import com.github.beng420.kung.feature.misc.LobbyHopHelperFeature;
import com.github.beng420.kung.feature.misc.LoadoutsAutoCloseFeature;
import com.github.beng420.kung.feature.misc.SuperpairsHelperFeature;
import com.github.beng420.kung.feature.screen.ScreenTracker;
import com.github.beng420.kung.feature.slayer.TarantulaHelperFeature;
import com.github.beng420.kung.skyblock.HypixelGuildTracker;
import com.github.beng420.kung.skyblock.HypixelInstanceTracker;
import com.github.beng420.kung.skyblock.HypixelPartyTracker;
import com.github.beng420.kung.skyblock.SkyBlockMayorTracker;
import com.github.beng420.kung.update.KungUpdater;
import com.github.beng420.kung.util.CatacombsRecentXpTracker;
import net.fabricmc.api.ClientModInitializer;

public final class KungClient implements ClientModInitializer {
    private final DungeonStateTracker dungeonStateTracker = new DungeonStateTracker();

    @Override
    public void onInitializeClient() {
        DungeonMapOverlayConfig.INSTANCE.load();
        HypixelInstanceTracker.initializeClient();
        HypixelPartyTracker.initializeClient();
        HypixelGuildTracker.initializeClient();
        SkyBlockMayorTracker.initializeClient();
        CatacombsRecentXpTracker.initializeClient();
        dungeonStateTracker.initializeClient();
        DungeonChatFilterFeature.initializeClient(dungeonStateTracker);
        DungeonMapFeature.initializeClient(dungeonStateTracker);
        DungeonSplitsOverlayFeature.initializeClient(dungeonStateTracker);
        ScreenTracker.initializeClient();
        Ca50ChatCommandFeature.initializeClient();
        CustomSoundsFeature.initializeClient();
        LobbyHopHelperFeature.initializeClient();
        LoadoutsAutoCloseFeature.initializeClient();
        SuperpairsHelperFeature.initializeClient();
        TarantulaHelperFeature.initializeClient();
        KungCommands.register(dungeonStateTracker);
        KungUpdater.INSTANCE.installPendingUpdateIfReady();
        KungUpdater.INSTANCE.checkForUpdatesAsync();
        KungMod.LOGGER.info("Kung client entrypoint ready.");
    }
}
