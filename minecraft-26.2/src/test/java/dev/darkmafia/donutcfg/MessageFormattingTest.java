package dev.darkmafia.donutcfg;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public final class MessageFormattingTest {
    public static void main(String[] args) {
        var saved = CfgMessages.format("Donutcfg: saved 'pvp' with /savecfg [name].");
        require(saved.getString().equals("saved 'pvp' with /savecfg [name]."), "prefix/text changed");
        boolean command = false;
        boolean profile = false;
        for (Component part : saved.getSiblings()) {
            if (part.getString().equals("'pvp'")) {
                profile = true;
                require(part.getStyle().getColor().getValue() == net.minecraft.network.chat.TextColor.GOLD.getValue(), "profile color");
            }
            if (part.getString().equals("/savecfg [name]")) {
                command = true;
                require(part.getStyle().getColor().getValue() == net.minecraft.network.chat.TextColor.AQUA.getValue(), "command color");
            }
        }
        require(command && profile, "command/name tokens missing");
        require(CfgMessages.profile("pvp").getStyle().getClickEvent()
                instanceof ClickEvent.SuggestCommand suggest && suggest.command().equals("/setcfg pvp"),
                "name click must suggest only, never load a config");
        require(CfgMessages.command("/startcfg", "/startcfg all ").getStyle().getClickEvent()
                instanceof ClickEvent.SuggestCommand, "help click should not execute");
        var error = CfgMessages.format("Donutcfg: cannot load 'pvp': file not found.");
        require(error.getSiblings().getFirst().getStyle().getColor().getValue() == net.minecraft.network.chat.TextColor.RED.getValue(), "error color");
        System.out.println("Chat formatting regression checks passed.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
