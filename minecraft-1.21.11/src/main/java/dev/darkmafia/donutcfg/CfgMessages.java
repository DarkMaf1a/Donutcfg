package dev.darkmafia.donutcfg;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;
import java.util.regex.Pattern;

/** One restrained palette for chat: cyan commands, gold names, grey explanations. */
final class CfgMessages {
    private static final Pattern TOKENS = Pattern.compile(
            "'[^'\\r\\n]+'|/[a-z]+(?:[ \\t]+(?:\\[[^]\\r\\n]+]|<[^>\\r\\n]+>))*");

    private CfgMessages() { }

    static void send(MinecraftClient client, Text body) {
        MutableText line = Text.literal("[").formatted(Formatting.DARK_GRAY)
                .append(Text.literal("Donutcfg").formatted(Formatting.AQUA, Formatting.BOLD))
                .append(Text.literal("] ").formatted(Formatting.DARK_GRAY)).append(body);
        if (client.player != null) client.player.sendMessage(line, false);
        else DonutCfg.logProfileInfo(line.getString());
    }

    static MutableText format(String message) {
        if (message.startsWith("Donutcfg: ")) message = message.substring(10);
        else if (message.startsWith("Donutcfg ")) message = message.substring(9);
        String lower = message.toLowerCase(Locale.ROOT);
        Formatting base = lower.contains("cancelled") || lower.contains("retry ")
                || lower.contains("skipping") ? Formatting.YELLOW
                : lower.startsWith("error:") || lower.contains("cannot ")
                || lower.contains("could not ") || lower.contains("stopped:")
                || lower.contains("not found") ? Formatting.RED
                : lower.startsWith("saved ") || lower.startsWith("complete")
                || lower.contains(" complete;") ? Formatting.GREEN : Formatting.GRAY;
        MutableText result = Text.empty();
        var matcher = TOKENS.matcher(message);
        int end = 0;
        while (matcher.find()) {
            result.append(Text.literal(message.substring(end, matcher.start())).formatted(base));
            String token = matcher.group();
            result.append(Text.literal(token).formatted(token.startsWith("/")
                    ? Formatting.AQUA : Formatting.GOLD));
            end = matcher.end();
        }
        return result.append(Text.literal(message.substring(end)).formatted(base));
    }

    static MutableText command(String label, String suggestion) {
        return Text.literal(label).formatted(Formatting.AQUA)
                .styled(style -> style.withClickEvent(new ClickEvent.SuggestCommand(suggestion))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to put this command in chat"))));
    }

    static MutableText profile(String name) {
        return Text.literal(name).formatted(Formatting.GOLD)
                .styled(style -> style.withClickEvent(new ClickEvent.SuggestCommand("/setcfg " + name))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("Select with /setcfg " + name))));
    }

    static void cancellable(MinecraftClient client, String message) {
        send(client, format(message).append(Text.literal("  ").formatted(Formatting.GRAY))
                .append(Text.literal("[Cancel]").formatted(Formatting.RED, Formatting.BOLD)
                        .styled(style -> style.withClickEvent(new ClickEvent.RunCommand("/stopcfg"))
                                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Stop Donutcfg now"))))));
    }

    static void helpLine(MinecraftClient client, String command, String suggestion, String description) {
        send(client, command(command, suggestion)
                .append(Text.literal(" — " + description).formatted(Formatting.GRAY)));
    }
}
