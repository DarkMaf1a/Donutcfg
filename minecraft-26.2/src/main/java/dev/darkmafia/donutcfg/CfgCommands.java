package dev.darkmafia.donutcfg;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import java.util.List;

/** Command grammar is independent of the client so selection/start can be regression-tested. */
final class CfgCommands {
    interface Actions {
        void start(CfgProfiles.Mode mode, String name);
        void select(String name);
        void save(String name);
        void delete(String name);
        void mode(CfgProfiles.Mode mode);
        void stop();
        void clean();
        void help();
        void list();
        List<String> names();
    }

    private CfgCommands() { }

    static <S> void register(CommandDispatcher<S> dispatcher, Actions actions) {
        dispatcher.register(startCommand("startcfg", actions));
        dispatcher.register(startCommand("loadcfg", actions));
        dispatcher.register(CfgCommands.<S>literal("setcfg")
                .then(CfgCommands.<S>name(actions).executes(context -> {
                    actions.select(StringArgumentType.getString(context, "name"));
                    return 1;
                })));
        dispatcher.register(CfgCommands.<S>literal("savecfg")
                .executes(context -> { actions.save(null); return 1; })
                .then(CfgCommands.<S>name(actions).executes(context -> {
                    actions.save(StringArgumentType.getString(context, "name"));
                    return 1;
                })));
        var modes = CfgCommands.<S>literal("cfgmode");
        for (CfgProfiles.Mode mode : CfgProfiles.Mode.values())
            modes.then(CfgCommands.<S>literal(mode.name().toLowerCase(java.util.Locale.ROOT))
                    .executes(context -> { actions.mode(mode); return 1; }));
        dispatcher.register(modes);
        dispatcher.register(CfgCommands.<S>literal("stopcfg")
                .executes(context -> { actions.stop(); return 1; }));
        dispatcher.register(CfgCommands.<S>literal("cleanshop")
                .executes(context -> { actions.clean(); return 1; }));
        dispatcher.register(CfgCommands.<S>literal("cfghelp")
                .executes(context -> { actions.help(); return 1; }));
        dispatcher.register(CfgCommands.<S>literal("cfglist")
                .executes(context -> { actions.list(); return 1; }));
        dispatcher.register(CfgCommands.<S>literal("cfg")
                .executes(context -> { actions.help(); return 1; })
                .then(CfgCommands.<S>literal("help")
                        .executes(context -> { actions.help(); return 1; }))
                .then(CfgCommands.<S>literal("clean")
                        .executes(context -> { actions.clean(); return 1; })));
        dispatcher.register(CfgCommands.<S>literal("delcfg")
                .then(deleteArgument(actions)));
        dispatcher.register(CfgCommands.<S>literal("del")
                .then(CfgCommands.<S>literal("cfg").then(deleteArgument(actions))));
    }

    private static <S> LiteralArgumentBuilder<S> startCommand(String command, Actions actions) {
        var root = CfgCommands.<S>literal(command)
                .executes(context -> { actions.start(null, null); return 1; });
        for (CfgProfiles.Mode mode : CfgProfiles.Mode.values())
            root.then(CfgCommands.<S>literal(mode.name().toLowerCase(java.util.Locale.ROOT))
                    .executes(context -> { actions.start(mode, null); return 1; })
                    .then(CfgCommands.<S>name(actions).executes(context -> {
                        actions.start(mode, StringArgumentType.getString(context, "name"));
                        return 1;
                    })));
        return root;
    }

    private static <S> RequiredArgumentBuilder<S, String> deleteArgument(Actions actions) {
        return CfgCommands.<S>name(actions).executes(context -> {
            actions.delete(StringArgumentType.getString(context, "name"));
            return 1;
        });
    }

    private static <S> LiteralArgumentBuilder<S> literal(String value) {
        return LiteralArgumentBuilder.literal(value);
    }

    private static <S> RequiredArgumentBuilder<S, String> name(Actions actions) {
        return RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.word())
                .suggests((context, builder) -> {
                    String prefix = builder.getRemainingLowerCase();
                    for (String name : actions.names())
                        if (name.toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
                            builder.suggest(name);
                    return builder.buildFuture();
                });
    }
}
