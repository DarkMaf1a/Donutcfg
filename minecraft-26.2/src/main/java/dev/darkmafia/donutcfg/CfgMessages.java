package dev.darkmafia.donutcfg;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.Locale;
import java.util.regex.Pattern;

/** One restrained palette for chat: cyan commands, gold names, grey explanations. */
final class CfgMessages {
    private static final Pattern TOKENS = Pattern.compile(
            "'[^'\\r\\n]+'|/[a-z]+(?:[ \\t]+(?:\\[[^]\\r\\n]+]|<[^>\\r\\n]+>))*");

    private CfgMessages() { }

    static void send(Minecraft client, Component body) {
        MutableComponent line = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("Donutcfg").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY)).append(body);
        if (client.player != null) client.player.sendSystemMessage(line);
        else DonutCfg.logProfileInfo(line.getString());
    }

    static MutableComponent format(String message) {
        if (message.startsWith("Donutcfg: ")) message = message.substring(10);
        else if (message.startsWith("Donutcfg ")) message = message.substring(9);
        String lower = message.toLowerCase(Locale.ROOT);
        ChatFormatting base = lower.contains("cancelled") || lower.contains("retry ")
                || lower.contains("skipping") ? ChatFormatting.YELLOW
                : lower.startsWith("error:") || lower.contains("cannot ")
                || lower.contains("could not ") || lower.contains("stopped:")
                || lower.contains("not found") ? ChatFormatting.RED
                : lower.startsWith("saved ") || lower.startsWith("complete")
                || lower.contains(" complete;") ? ChatFormatting.GREEN : ChatFormatting.GRAY;
        MutableComponent result = Component.empty();
        var matcher = TOKENS.matcher(message);
        int end = 0;
        while (matcher.find()) {
            result.append(Component.literal(message.substring(end, matcher.start())).withStyle(base));
            String token = matcher.group();
            result.append(Component.literal(token).withStyle(token.startsWith("/")
                    ? ChatFormatting.AQUA : ChatFormatting.GOLD));
            end = matcher.end();
        }
        return result.append(Component.literal(message.substring(end)).withStyle(base));
    }

    static MutableComponent command(String label, String suggestion) {
        return Component.literal(label).withStyle(ChatFormatting.AQUA)
                .withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand(suggestion))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to put this command in chat"))));
    }

    static MutableComponent profile(String name) {
        return Component.literal(name).withStyle(ChatFormatting.GOLD)
                .withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand("/setcfg " + name))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Select with /setcfg " + name))));
    }

    static void cancellable(Minecraft client, String message) {
        send(client, format(message).append(Component.literal("  ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("[Cancel]").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                        .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/stopcfg"))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Stop Donutcfg now"))))));
    }

    static void helpLine(Minecraft client, String command, String suggestion, String description) {
        send(client, command(command, suggestion)
                .append(Component.literal(" — " + description).withStyle(ChatFormatting.GRAY)));
    }
}
