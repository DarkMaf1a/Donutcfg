package dev.darkmafia.donutcfg;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Local-only Cancel widget; never clicks a server inventory slot or submits a sign. */
final class CfgControls {
    private static Screen attachedScreen;
    private static ButtonWidget cancelButton;

    private CfgControls() { }

    static void initialize() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> attach(client, screen));
    }

    static void tick(MinecraftClient client) {
        Screen screen = client.currentScreen;
        if (screen != attachedScreen) {
            attachedScreen = null;
            cancelButton = null;
        }
        if (!DonutCfg.isWorking()) {
            if (attachedScreen != null && cancelButton != null)
                Screens.getButtons(attachedScreen).remove(cancelButton);
            attachedScreen = null;
            cancelButton = null;
            return;
        }
        attach(client, screen);
    }

    private static void attach(MinecraftClient client, Screen screen) {
        if (!DonutCfg.isWorking() || !(screen instanceof HandledScreen<?>
                || screen instanceof AbstractSignEditScreen)) return;
        var buttons = Screens.getButtons(screen);
        if (attachedScreen == screen && cancelButton != null && buttons.contains(cancelButton)) return;
        cancelButton = ButtonWidget.builder(Text.literal("Cancel Donutcfg")
                        .formatted(Formatting.RED), button -> {
                    // Runs synchronously on the client thread, before another automation tick.
                    DonutCfg.cancel(client);
                    button.active = false;
                    button.visible = false;
                })
                .dimensions(Math.max(4, screen.width - 120), 6, 114, 20)
                .tooltip(Tooltip.of(Text.literal("Stop immediately. Applied server changes are not rolled back.")))
                .build();
        attachedScreen = screen;
        buttons.add(cancelButton);
    }
}
