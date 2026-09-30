package dev.darkmafia.donutcfg;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class DonutCfg implements ClientModInitializer {
    public static final String VERSION = "0.5.0";
    private static final Logger LOGGER = LoggerFactory.getLogger("Donutcfg");

    @Override
    public void onInitializeClient() {
        CfgProfiles.initialize();
        CfgControls.initialize();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                CfgCommands.register(dispatcher, new CfgCommands.Actions() {
                    public void start(CfgProfiles.Mode mode, String name) {
                        CfgProfiles.load(MinecraftClient.getInstance(), mode, name);
                    }
                    public void select(String name) { CfgProfiles.select(MinecraftClient.getInstance(), name); }
                    public void save(String name) { CfgProfiles.save(MinecraftClient.getInstance(), name); }
                    public void delete(String name) { CfgProfiles.delete(MinecraftClient.getInstance(), name); }
                    public void mode(CfgProfiles.Mode mode) { CfgProfiles.setMode(MinecraftClient.getInstance(), mode); }
                    public void stop() { cancel(MinecraftClient.getInstance()); }
                    public void clean() { ShopClean.start(MinecraftClient.getInstance()); }
                    public void help() { DonutCfg.help(MinecraftClient.getInstance()); }
                    public void list() { CfgProfiles.list(MinecraftClient.getInstance()); }
                    public List<String> names() { return CfgProfiles.names(); }
                }));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CfgReplay.tick(client);
            ShopClean.tick(client);
            CfgProfiles.tick(client);
            CfgControls.tick(client);
        });
        LOGGER.info("Donutcfg {} ready. /cfghelp shows commands.", VERSION);
    }

    static boolean isWorking() {
        return CfgReplay.isActive() || CfgProfiles.isSaving() || ShopClean.isActive();
    }

    static void cancel(MinecraftClient client) {
        boolean working = isWorking();
        // Stop every process, not just the first one, should an older state ever overlap.
        if (CfgProfiles.isSaving()) CfgProfiles.cancelSave(client);
        if (ShopClean.isActive()) ShopClean.stop(client);
        if (CfgReplay.isActive()) CfgReplay.stop(client, "cancelled by user");
        if (!working) notifyUser(client, "Donutcfg: nothing is running.");
    }

    static void notifyUser(MinecraftClient client, String message) {
        CfgMessages.send(client, CfgMessages.format(message));
    }

    static void logProfileError(String message, Throwable error) { LOGGER.error(message, error); }
    static void logProfileInfo(String message) { LOGGER.info(message); }

    static void notifyGuiCompatibilityHint(MinecraftClient client) {
        CfgMessages.send(client, Text.literal("Error: DonutSMP GUI is unavailable or incompatible. ")
                .formatted(Formatting.RED)
                .append(Text.literal("Install ViaFabricPlus and select Minecraft 1.21.5 or below, ")
                        .formatted(Formatting.YELLOW))
                .append(Text.literal("then reconnect and retry.").formatted(Formatting.RED)));
    }

    static final class GuiAccessException extends IllegalStateException {
        GuiAccessException(String message) { super(message); }
    }

    private static void help(MinecraftClient client) {
        CfgMessages.send(client, Text.literal("━━ Commands · " + VERSION + " ━━")
                .formatted(Formatting.AQUA, Formatting.BOLD));
        CfgMessages.send(client, Text.literal("Selected: ").formatted(Formatting.GRAY)
                .append(CfgMessages.profile(CfgProfiles.selectedName()))
                .append(Text.literal(" · mode: ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(CfgProfiles.selectedModeName()).formatted(Formatting.LIGHT_PURPLE)));
        CfgMessages.helpLine(client, "/cfglist", "/cfglist", "list available configs; click a name to prepare /setcfg.");
        CfgMessages.helpLine(client, "/setcfg <name>", "/setcfg ", "select only; does not change server menus.");
        CfgMessages.helpLine(client, "/startcfg <all|settings|shop> [name]", "/startcfg all ", "start loading; no args uses selected config/mode.");
        CfgMessages.helpLine(client, "/loadcfg [all|settings|shop] [name]", "/loadcfg ", "same loading behavior as /startcfg.");
        CfgMessages.helpLine(client, "/savecfg [name]", "/savecfg ", "save both menus; default is reserved.");
        CfgMessages.helpLine(client, "/cfgmode <all|settings|shop>", "/cfgmode ", "choose a mode without starting.");
        CfgMessages.helpLine(client, "/del cfg <name> · /delcfg <name>", "/del cfg ", "remove a personal config; backup kept in .deleted.");
        CfgMessages.helpLine(client, "/stopcfg", "/stopcfg", "stop now, or press Cancel in the active menu.");
        CfgMessages.helpLine(client, "/cleanshop · /cfg clean", "/cleanshop", "clear Quick Buy.");
        CfgMessages.helpLine(client, "/cfghelp · /cfg help · /cfg", "/cfghelp", "show this help.");
        CfgMessages.send(client, Text.literal("GUI: ViaFabricPlus → Minecraft 1.21.5 or below; reconnect after changing it.")
                .formatted(Formatting.DARK_GRAY));
    }
}
