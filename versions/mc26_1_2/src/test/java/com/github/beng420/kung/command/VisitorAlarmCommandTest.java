package com.github.beng420.kung.command;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
import static org.junit.Assert.*;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.junit.Test;

public class VisitorAlarmCommandTest {
    @Test
    public void muteClickResolvesToAnExecutableClientCommand() {
        var dispatcher = new CommandDispatcher<FabricClientCommandSource>();
        var root = literal("kung");
        UserCommandGroup.register(root, null);
        dispatcher.register(root);
        var parsed = dispatcher.parse("kung visitors mute", null);
        assertTrue(parsed.getExceptions().isEmpty());
        assertFalse(parsed.getReader().canRead());
        assertNotNull(parsed.getContext().getCommand());
    }
}
