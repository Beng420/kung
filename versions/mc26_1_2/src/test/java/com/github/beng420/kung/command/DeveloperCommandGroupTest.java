package com.github.beng420.kung.command;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
import static org.junit.Assert.*;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.junit.Test;

public final class DeveloperCommandGroupTest {
    @Test public void missingDeveloperAccountCannotDiscoverOrParseTheDeveloperBranch() {
        var dispatcher = new CommandDispatcher<FabricClientCommandSource>();
        var root = literal("kung");
        DeveloperCommandGroup.register(root);
        var command = dispatcher.register(root);
        assertNull(command.getChild("dev"));
        assertTrue(dispatcher.getSmartUsage(command, null).isEmpty());
        assertTrue(dispatcher.getCompletionSuggestions(dispatcher.parse("kung ", null))
            .join().getList().isEmpty());
        for (String action : new String[] {"on", "off", "sample", "copy"}) {
            var parsed = dispatcher.parse("kung dev dragons " + action, null);
            assertTrue(parsed.getReader().canRead());
            assertNull(parsed.getContext().getCommand());
        }
    }
}
