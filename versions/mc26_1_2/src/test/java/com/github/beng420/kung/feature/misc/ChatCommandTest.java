package com.github.beng420.kung.feature.misc;

import com.github.beng420.kung.config.category.MiscConfig;
import com.github.beng420.kung.feature.misc.ChatCommandsFeature.ChatChannel;
import com.github.beng420.kung.util.ServerTpsTracker;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ChatCommandTest {
    @Test
    public void patternsKeepOptionalTargetsAndRejectExtraArguments() {
        assertTarget(ChatCommand.C50, "!c50", null);
        assertTarget(ChatCommand.C50, "!C50  Player_123\t", "Player_123");
        assertTarget(ChatCommand.CA50, "!ca50 ", null);
        assertTarget(ChatCommand.CA50, "!CA50 A", "A");
        assertTarget(ChatCommand.TPS, "!TpS\t", null);
        for (ChatCommand command : ChatCommand.values()) {
            String name = "!" + command.name().toLowerCase(java.util.Locale.ROOT);
            assertFalse(command.pattern.matcher(name + "extra").matches());
            assertFalse(command.pattern.matcher(name + " Player Second").matches());
            assertFalse(command.pattern.matcher("hello " + name).matches());
        }
        assertFalse(ChatCommand.TPS.pattern.matcher("!tps Player").matches());
        assertFalse(ChatCommand.C50.pattern.matcher("!c50 invalid-name").matches());
        assertFalse(ChatCommand.CA50.pattern.matcher("!ca50 abcdefghijklmnopq").matches());
    }

    @Test
    public void eachCommandKeepsItsOwnLiveChannelAndMasterSwitches() {
        MiscConfig config = new MiscConfig();
        for (ChatCommand command : ChatCommand.values()) assertChannels(config, command, false, false, false, false);
        config.setChatCommandsEnabled(true);
        for (ChatCommand command : ChatCommand.values()) assertChannels(config, command, true, true, false, false);

        config.setC50PartyCommandsEnabled(false);
        config.setC50PrivateCommandsEnabled(true);
        config.setCa50GuildCommandsEnabled(false);
        config.setCa50AllChatCommandsEnabled(true);
        config.setTpsPartyCommandsEnabled(false);
        config.setTpsGuildCommandsEnabled(false);
        config.setTpsAllChatCommandsEnabled(true);
        config.setTpsPrivateCommandsEnabled(true);
        assertChannels(config, ChatCommand.C50, false, true, false, true);
        assertChannels(config, ChatCommand.CA50, true, false, true, false);
        assertChannels(config, ChatCommand.TPS, false, false, true, true);

        config.setC50ChatCommandEnabled(false);
        assertChannels(config, ChatCommand.C50, false, false, false, false);
        assertChannels(config, ChatCommand.CA50, true, false, true, false);
        config.setCa50ChatCommandEnabled(false);
        assertChannels(config, ChatCommand.CA50, false, false, false, false);
        assertChannels(config, ChatCommand.TPS, false, false, true, true);
        config.setTpsChatCommandEnabled(false);
        assertChannels(config, ChatCommand.TPS, false, false, false, false);

        config.setC50ChatCommandEnabled(true);
        config.setCa50ChatCommandEnabled(true);
        config.setTpsChatCommandEnabled(true);
        config.setChatCommandsEnabled(false);
        for (ChatCommand command : ChatCommand.values()) assertChannels(config, command, false, false, false, false);
    }

    @Test
    public void tpsRepliesSynchronouslyOnceWithoutAPlayerOrCalculation() {
        List<String> responses = new ArrayList<>();
        String expected = ServerTpsTracker.INSTANCE.snapshot().message();
        ChatCommand.TPS.execute(null, ChatChannel.PRIVATE, "Player", responses::add);
        assertEquals(List.of(expected), responses);
    }

    private static void assertTarget(ChatCommand command, String text, String expected) {
        var match = command.pattern.matcher(text);
        assertTrue(text, match.matches());
        assertEquals(expected, command.target(match));
    }

    private static void assertChannels(MiscConfig config, ChatCommand command, boolean party, boolean guild,
                                       boolean all, boolean direct) {
        assertEquals(command + " party", party, command.isEnabled(config, ChatChannel.PARTY));
        assertEquals(command + " guild", guild, command.isEnabled(config, ChatChannel.GUILD));
        assertEquals(command + " all", all, command.isEnabled(config, ChatChannel.ALL));
        assertEquals(command + " private", direct, command.isEnabled(config, ChatChannel.PRIVATE));
    }
}
