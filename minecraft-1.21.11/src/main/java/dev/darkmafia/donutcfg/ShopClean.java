package dev.darkmafia.donutcfg;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

/** Removes every Quick Buy entry through the server's Edit UI. */
final class ShopClean {
    private static final long CLICK_DELAY = 350L;
    private static final long TIMEOUT = 6_000L;

    private enum Stage {
        OPEN_SHOP, WAIT_SHOP, START_EDIT, WAIT_EDIT,
        REMOVE, WAIT_REMOVE, SAVE, WAIT_SAVE
    }

    private static boolean active;
    private static Stage stage;
    private static int index;
    private static int removed;
    private static long nextAction;
    private static long deadline;

    private ShopClean() { }

    static boolean isActive() {
        return active;
    }

    static void start(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) {
            DonutCfg.notifyUser(client, "Donutcfg: join the server before /cleanshop.");
            return;
        }
        if (CfgReplay.isActive()) {
            DonutCfg.notifyUser(client, "Donutcfg: finish /startcfg before /cleanshop.");
            return;
        }
        if (CfgProfiles.isSaving()) {
            DonutCfg.notifyUser(client, "Donutcfg: finish /savecfg before /cleanshop.");
            return;
        }
        if (active) {
            DonutCfg.notifyUser(client, "Donutcfg: /cleanshop is already running.");
            return;
        }
        active = true;
        stage = Stage.OPEN_SHOP;
        index = 0;
        removed = 0;
        nextAction = 0L;
        deadline = 0L;
        CfgMessages.cancellable(client, "Donutcfg: clearing Quick Buy; do not interact until finished.");
    }

    static void stop(MinecraftClient client) {
        if (!active) return;
        active = false;
        DonutCfg.notifyUser(client, "Donutcfg: /cleanshop cancelled. Save Edit manually if it is open.");
    }

    static void tick(MinecraftClient client) {
        if (!active || System.currentTimeMillis() < nextAction) return;
        if (client.player == null || client.getNetworkHandler() == null) {
            active = false;
            DonutCfg.notifyUser(client, "Donutcfg: /cleanshop stopped on disconnect.");
            return;
        }
        long now = System.currentTimeMillis();
        switch (stage) {
            case OPEN_SHOP -> {
                if (client.currentScreen instanceof HandledScreen<?>)
                    client.player.closeHandledScreen();
                else if (client.currentScreen != null)
                    client.setScreen(null);
                client.player.networkHandler.sendChatCommand("shop");
                stage = Stage.WAIT_SHOP;
                deadline = now + TIMEOUT;
                nextAction = now + 600L;
            }
            case WAIT_SHOP -> {
                if (quickBuy(client)) {
                    stage = Stage.START_EDIT;
                    nextAction = now + CLICK_DELAY;
                } else if (now > deadline) fail(client, "Quick Buy did not open");
            }
            case START_EDIT -> {
                if (!quickBuy(client)) {
                    fail(client, "Quick Buy closed before Edit");
                    return;
                }
                Slot edit = editSlot(client);
                if (edit == null || !lore(edit.getStack(), "Click to edit")) {
                    fail(client, "Edit button missing or already active");
                    return;
                }
                if (!click(client, edit)) return;
                stage = Stage.WAIT_EDIT;
                deadline = now + TIMEOUT;
                nextAction = now + CLICK_DELAY;
            }
            case WAIT_EDIT -> {
                if (!quickBuy(client)) {
                    fail(client, "Quick Buy closed while entering Edit");
                } else if (editMode(client)) {
                    stage = Stage.REMOVE;
                    index = 0;
                } else if (now > deadline) fail(client, "Edit mode did not open");
            }
            case REMOVE -> {
                if (!quickBuy(client) || !editMode(client)) {
                    fail(client, "Quick Buy Edit closed unexpectedly");
                    return;
                }
                while (index < 45) {
                    Slot slot = slot(client, index);
                    if (slot == null) {
                        fail(client, "slot " + index + " is missing");
                        return;
                    }
                    ItemStack stack = slot.getStack();
                    if (stack.isEmpty() || emptyEntry(stack)
                            || lore(stack, "Click to add back")) {
                        index++;
                        continue;
                    }
                    if (!lore(stack, "Click to remove")) {
                        fail(client, "cannot identify removal action in slot " + index);
                        return;
                    }
                    if (!click(client, slot)) return;
                    stage = Stage.WAIT_REMOVE;
                    deadline = now + TIMEOUT;
                    nextAction = now + CLICK_DELAY;
                    return;
                }
                stage = Stage.SAVE;
            }
            case WAIT_REMOVE -> {
                if (!quickBuy(client) || !editMode(client)) {
                    fail(client, "Edit closed while removing slot " + index);
                    return;
                }
                Slot slot = slot(client, index);
                if (slot != null && lore(slot.getStack(), "Click to add back")) {
                    removed++;
                    index++;
                    stage = Stage.REMOVE;
                } else if (now > deadline)
                    fail(client, "server did not confirm removal at slot " + index);
            }
            case SAVE -> {
                Slot edit = editSlot(client);
                if (!quickBuy(client) || edit == null || !editMode(client)) {
                    fail(client, "Edit button missing before save");
                    return;
                }
                if (!click(client, edit)) return;
                stage = Stage.WAIT_SAVE;
                deadline = now + TIMEOUT;
                nextAction = now + CLICK_DELAY;
            }
            case WAIT_SAVE -> {
                if (!quickBuy(client)) {
                    fail(client, "Quick Buy closed before save confirmation");
                    return;
                }
                if (!editMode(client)) {
                    boolean empty = true;
                    for (int i = 0; i < 45; i++) {
                        Slot slot = slot(client, i);
                        if (slot == null || !emptyEntry(slot.getStack())) {
                            empty = false;
                            break;
                        }
                    }
                    if (empty) {
                        active = false;
                        client.player.closeHandledScreen();
                        DonutCfg.notifyUser(client, "Donutcfg: /cleanshop complete; "
                                + removed + " Quick Buy entries removed.");
                    } else if (now > deadline)
                        fail(client, "save finished but some Quick Buy slots are not empty");
                } else if (now > deadline) fail(client, "Edit changes were not saved");
            }
        }
    }

    private static boolean click(MinecraftClient client, Slot slot) {
        ScreenHandler handler = client.player.currentScreenHandler;
        if (!handler.getCursorStack().isEmpty()) {
            fail(client, "cursor is not empty; no click sent", false);
            return false;
        }
        client.interactionManager.clickSlot(handler.syncId, slot.id, 0,
                SlotActionType.PICKUP, client.player);
        return true;
    }

    private static void fail(MinecraftClient client, String reason) {
        fail(client, reason, true);
    }

    private static void fail(MinecraftClient client, String reason, boolean guiError) {
        active = false;
        DonutCfg.notifyUser(client, "Donutcfg: /cleanshop stopped: " + reason
                + ". Save Edit manually if it is open.");
        if (guiError) DonutCfg.notifyGuiCompatibilityHint(client);
    }

    private static boolean quickBuy(MinecraftClient client) {
        return client.currentScreen instanceof HandledScreen<?> screen
                && screen.getTitle().getString().equalsIgnoreCase("Quick Buy");
    }

    private static Slot slot(MinecraftClient client, int id) {
        for (Slot slot : client.player.currentScreenHandler.slots)
            if (slot.id == id) return slot;
        return null;
    }

    private static Slot editSlot(MinecraftClient client) {
        Slot slot = slot(client, 53);
        return slot != null && slot.getStack().getName().getString()
                .equalsIgnoreCase("Edit") ? slot : null;
    }

    private static boolean editMode(MinecraftClient client) {
        Slot edit = editSlot(client);
        return edit != null && lore(edit.getStack(), "Click to save changes");
    }

    private static boolean emptyEntry(ItemStack stack) {
        return stack.isEmpty() || (stack.isOf(Items.GRAY_STAINED_GLASS_PANE)
                && stack.getName().getString().equalsIgnoreCase("Empty"));
    }

    private static boolean lore(ItemStack stack, String text) {
        var lore = stack.get(DataComponentTypes.LORE);
        return lore != null && lore.lines().stream()
                .anyMatch(line -> line.getString().equalsIgnoreCase(text));
    }
}
