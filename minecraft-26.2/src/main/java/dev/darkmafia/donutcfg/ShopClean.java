package dev.darkmafia.donutcfg;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;

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

    static void start(Minecraft client) {
        if (client.player == null || client.getConnection() == null) {
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

    static void stop(Minecraft client) {
        if (!active) return;
        active = false;
        DonutCfg.notifyUser(client, "Donutcfg: /cleanshop cancelled. Save Edit manually if it is open.");
    }

    static void tick(Minecraft client) {
        if (!active || System.currentTimeMillis() < nextAction) return;
        if (client.player == null || client.getConnection() == null) {
            active = false;
            DonutCfg.notifyUser(client, "Donutcfg: /cleanshop stopped on disconnect.");
            return;
        }
        long now = System.currentTimeMillis();
        switch (stage) {
            case OPEN_SHOP -> {
                if (client.gui.screen() instanceof AbstractContainerScreen<?>)
                    client.player.closeContainer();
                else if (client.gui.screen() != null)
                    client.gui.setScreen(null);
                client.player.connection.sendCommand("shop");
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
                if (edit == null || !lore(edit.getItem(), "Click to edit")) {
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
                    ItemStack stack = slot.getItem();
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
                if (slot != null && lore(slot.getItem(), "Click to add back")) {
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
                        if (slot == null || !emptyEntry(slot.getItem())) {
                            empty = false;
                            break;
                        }
                    }
                    if (empty) {
                        active = false;
                        client.player.closeContainer();
                        DonutCfg.notifyUser(client, "Donutcfg: /cleanshop complete; "
                                + removed + " Quick Buy entries removed.");
                    } else if (now > deadline)
                        fail(client, "save finished but some Quick Buy slots are not empty");
                } else if (now > deadline) fail(client, "Edit changes were not saved");
            }
        }
    }

    private static boolean click(Minecraft client, Slot slot) {
        AbstractContainerMenu handler = client.player.containerMenu;
        if (!handler.getCarried().isEmpty()) {
            fail(client, "cursor is not empty; no click sent", false);
            return false;
        }
        client.gameMode.handleContainerInput(handler.containerId, slot.index, 0,
                ContainerInput.PICKUP, client.player);
        return true;
    }

    private static void fail(Minecraft client, String reason) {
        fail(client, reason, true);
    }

    private static void fail(Minecraft client, String reason, boolean guiError) {
        active = false;
        DonutCfg.notifyUser(client, "Donutcfg: /cleanshop stopped: " + reason
                + ". Save Edit manually if it is open.");
        if (guiError) DonutCfg.notifyGuiCompatibilityHint(client);
    }

    private static boolean quickBuy(Minecraft client) {
        return client.gui.screen() instanceof AbstractContainerScreen<?> screen
                && screen.getTitle().getString().equalsIgnoreCase("Quick Buy");
    }

    private static Slot slot(Minecraft client, int id) {
        for (Slot slot : client.player.containerMenu.slots)
            if (slot.index == id) return slot;
        return null;
    }

    private static Slot editSlot(Minecraft client) {
        Slot slot = slot(client, 53);
        return slot != null && slot.getItem().getHoverName().getString()
                .equalsIgnoreCase("Edit") ? slot : null;
    }

    private static boolean editMode(Minecraft client) {
        Slot edit = editSlot(client);
        return edit != null && lore(edit.getItem(), "Click to save changes");
    }

    private static boolean isItem(ItemStack stack, Item item) {
        return stack.getItem() == item;
    }

    private static boolean emptyEntry(ItemStack stack) {
        return stack.isEmpty() || (isItem(stack, Items.STAINED_GLASS_PANE.gray())
                && stack.getHoverName().getString().equalsIgnoreCase("Empty"));
    }

    private static boolean lore(ItemStack stack, String text) {
        var lore = stack.get(DataComponents.LORE);
        return lore != null && lore.lines().stream()
                .anyMatch(line -> line.getString().equalsIgnoreCase(text));
    }
}
