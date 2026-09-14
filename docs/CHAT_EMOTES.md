# Chat emotes

`/kung` > **Util > Chat Emotes** converts both `:iman:` and its alias `:ironman:`
to `♲` (U+2672). The menu lists these two short mappings without a history hint.
This independent feature defaults off, including for existing configs;
Chat Commands and the Hypixel API are not required.

Replacement applies to outgoing chat and command text, including `/pc`, `/gc` and
`/msg`. It is case-sensitive, replaces every occurrence, and leaves other aliases
and already converted symbols unchanged. No Kung prefix is added.

`ChatEmotesFeature` uses Fabric `MODIFY_CHAT` and `MODIFY_COMMAND` in the existing
send path. Minecraft 26.1.2 `ChatScreen.handleChatInput` adds the original normalized
input to `ChatComponent.addRecentChat` before calling `sendChat` or `sendCommand`.
Fabric's message callbacks run at those connection methods, before signing. Kung
does not cancel, resend or manually insert history entries. Up arrow therefore
recalls the original input, including either alias, and sending it again converts it again.

Validation: `ChatEmotesFeatureTest` exercises the registered outgoing events,
disabled/enabled states, both aliases and mixed/repeated occurrences in chat and
commands, case sensitivity, config replacement and shutdown.
`ConfigRegressionTest` checks the default and saved toggle. The local Minecraft
bytecode and Fabric message API source establish the history/send order above.

Live check still needed: enable the feature, send both aliases in chat and `/pc`, reopen
chat, press Up and resend. Verify one message per send, the recalled original input,
and the displayed `♲` with the user's other chat mods enabled. Another mod that
cancels a message earlier can prevent Fabric's modification callbacks from running.
