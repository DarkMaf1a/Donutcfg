package dev.darkmafia.donutcfg;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

/** Local-only Cancel widget; never clicks a server inventory slot or submits a sign. */
final class CfgControls {
    private static Screen attachedScreen;
    private static Button cancelButton;

    private CfgControls() { }

    static void initialize() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> attach(client, screen));
    }

    static void tick(Minecraft client) {
        Screen screen = client.gui.screen();
        if (screen != attachedScreen) {
            attachedScreen = null;
            cancelButton = null;
        }
        if (!DonutCfg.isWorking()) {
            if (attachedScreen != null && cancelButton != null)
                Screens.getWidgets(attachedScreen).remove(cancelButton);
            attachedScreen = null;
            cancelButton = null;
            return;
        }
        attach(client, screen);
    }

    private static void attach(Minecraft client, Screen screen) {
        if (!DonutCfg.isWorking() || !(screen instanceof AbstractContainerScreen<?>
                || screen instanceof AbstractSignEditScreen)) return;
        var buttons = Screens.getWidgets(screen);
        if (attachedScreen == screen && cancelButton != null && buttons.contains(cancelButton)) return;
        cancelButton = Button.builder(Component.literal("Cancel Donutcfg")
                        .withStyle(ChatFormatting.RED), button -> {
                    // Runs synchronously on the client thread, before another automation tick.
                    DonutCfg.cancel(client);
                    button.active = false;
                    button.visible = false;
                })
                .bounds(Math.max(4, screen.width - 120), 6, 114, 20)
                .tooltip(Tooltip.create(Component.literal("Stop immediately. Applied server changes are not rolled back.")))
                .build();
        attachedScreen = screen;
        buttons.add(cancelButton);
    }
}
