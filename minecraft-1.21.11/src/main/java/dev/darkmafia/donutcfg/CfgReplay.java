package dev.darkmafia.donutcfg;

import dev.darkmafia.donutcfg.mixin.SignScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Idempotent replay of the final settings and Quick Buy layout. */
final class CfgReplay {
    private static final long CLICK_DELAY = 350L;
    private static final long OPEN_TIMEOUT = 6_000L;

    record Setting(String prefix, String wanted) { }

    record ShopEntry(int preferredSlot, String query, Item item,
                             int amount, String potion,
                             Map<String, Integer> enchants) { }

    private enum Stage {
        OPEN_SETTINGS, WAIT_SETTINGS, SETTINGS,
        OPEN_SHOP, WAIT_SHOP, START_EDIT, WAIT_EDIT, REMOVE_WRONG,
        WAIT_REMOVE, SAVE_EDIT, WAIT_SAVE, SHOP,
        WAIT_SELECT, WAIT_SEARCH_SIGN, SEARCH_RESULTS,
        WAIT_ITEM_RESULT, ENCHANTS, WAIT_AMOUNT_SIGN,
        WAIT_QUICK_VERIFY
    }

    private static final List<Setting> SETTINGS = List.of(
            new Setting("Public Chat:", "OFF"),
            new Setting("Private Messages:", "Anyone"),
            new Setting("Advancement Messages:", "OFF"),
            new Setting("Item Worth Lore:", "OFF"),
            new Setting("Teleport Confirm Menus:", "OFF"),
            new Setting("Private Transactions:", "ON"),
            new Setting("Show Shards:", "ON"),
            new Setting("Show Kills:", "ON"),
            new Setting("Show Deaths:", "ON"),
            new Setting("Show Playtime:", "ON"),
            new Setting("Auction Quick Buy:", "ON"),
            new Setting("Auction Quick Sell:", "ON"),
            new Setting("Mob Spawns:", "OFF"),
            new Setting("Phantom Spawning:", "OFF")
    );

    private static final Map<String, Integer> PMU = Map.of(
            "protection", 4, "mending", 1, "unbreaking", 3);
    private static final Map<String, Integer> BMU = Map.of(
            "blast_protection", 4, "mending", 1, "unbreaking", 3);
    private static final Map<String, Integer> BFMU = Map.of(
            "blast_protection", 4, "feather_falling", 4,
            "mending", 1, "unbreaking", 3);
    private static final Map<String, Integer> SWORD = Map.of(
            "sharpness", 5, "knockback", 1);
    private static final Map<String, Integer> EFFICIENCY_PICKAXE = Map.of(
            "efficiency", 5);
    private static final Map<String, Integer> SILK_PICKAXE = Map.of(
            "efficiency", 5, "silk_touch", 1, "unbreaking", 3, "mending", 1);
    private static final Map<String, Integer> NETHERITE_PICKAXE = Map.of(
            "efficiency", 5, "unbreaking", 3, "fortune", 3, "mending", 1);
    private static final Map<String, Integer> TRIDENT = Map.of(
            "riptide", 3, "unbreaking", 3, "mending", 1);
    private static final Map<String, Integer> MACE = Map.of(
            "breach", 4, "unbreaking", 3, "mending", 1);

    private static final List<ShopEntry> SHOP = List.of(
            item(0, "totem", Items.TOTEM_OF_UNDYING, 1),
            item(1, "xp", Items.EXPERIENCE_BOTTLE, 64),
            item(2, "crystal", Items.END_CRYSTAL, 64),
            item(3, "obsidian", Items.OBSIDIAN, 64),
            item(4, "pearl", Items.ENDER_PEARL, 16),
            item(5, "glowstone", Items.GLOWSTONE, 64),
            item(6, "anchor", Items.RESPAWN_ANCHOR, 64),
            item(7, "ender chest", Items.ENDER_CHEST, 1),
            item(8, "golden apple", Items.GOLDEN_APPLE, 64),
            armor(9, "diamond helmet", Items.DIAMOND_HELMET, PMU),
            armor(10, "netherite helmet", Items.NETHERITE_HELMET, PMU),
            potion(11, "strength", "strong_strength"),
            potion(12, "turtle", "strong_turtle_master"),
            item(13, "bucket of puffer", Items.PUFFERFISH_BUCKET, 1),
            item(14, "torch", Items.TORCH, 64),
            item(15, "chest", Items.CHEST, 64),
            item(16, "oak sign", Items.OAK_SIGN, 16),
            item(17, "water", Items.WATER_BUCKET, 1),
            armor(18, "diamond chestplate", Items.DIAMOND_CHESTPLATE, PMU),
            armor(19, "netherite chestplate", Items.NETHERITE_CHESTPLATE, PMU),
            armor(20, "diamond sword", Items.DIAMOND_SWORD, SWORD),
            item(21, "flint and steel", Items.FLINT_AND_STEEL, 1),
            item(22, "totem", Items.TOTEM_OF_UNDYING, 1),
            item(23, "totem", Items.TOTEM_OF_UNDYING, 1),
            item(24, "totem", Items.TOTEM_OF_UNDYING, 1),
            item(25, "book and quill", Items.WRITABLE_BOOK, 1),
            item(26, "lava", Items.LAVA_BUCKET, 1),
            armor(27, "diamond leggings", Items.DIAMOND_LEGGINGS, BMU),
            armor(28, "netherite leggings", Items.NETHERITE_LEGGINGS, BMU),
            armor(29, "diamond pickaxe", Items.DIAMOND_PICKAXE, EFFICIENCY_PICKAXE),
            armor(30, "netherite pickaxe", Items.NETHERITE_PICKAXE, NETHERITE_PICKAXE),
            armor(31, "diamond pickaxe", Items.DIAMOND_PICKAXE, SILK_PICKAXE),
            armor(32, "trident", Items.TRIDENT, TRIDENT),
            armor(33, "mace", Items.MACE, MACE),
            item(34, "shulker box", Items.SHULKER_BOX, 1),
            item(35, "bucket", Items.BUCKET, 16),
            armor(36, "diamond boots", Items.DIAMOND_BOOTS, BFMU),
            armor(37, "netherite boots", Items.NETHERITE_BOOTS, BFMU),
            armor(38, "netherite pickaxe", Items.NETHERITE_PICKAXE, SILK_PICKAXE),
            armor(39, "diamond leggings", Items.DIAMOND_LEGGINGS, PMU),
            armor(40, "diamond boots", Items.DIAMOND_BOOTS, PMU),
            armor(41, "netherite leggings", Items.NETHERITE_LEGGINGS, PMU),
            armor(42, "netherite boots", Items.NETHERITE_BOOTS, PMU)
    );

    private static final List<String> KNOWN_ENCHANTS = List.of(
            "protection", "blast_protection", "feather_falling",
            "mending", "unbreaking", "aqua_affinity", "respiration",
            "sharpness", "looting", "fire_aspect", "knockback",
            "sweeping_edge", "efficiency", "fortune", "silk_touch",
            "impaling", "loyalty", "channeling", "riptide",
            "density", "wind_burst", "breach");

    private static boolean active;
    private static Stage stage;
    private static int settingIndex;
    private static int settingClicks;
    private static int shopIndex;
    private static int editIndex;
    private static int removeSlot;
    private static int completedShop;
    private static int skippedOccupied;
    private static int removedShop;
    private static int recoveries;
    private static long nextAction;
    private static long deadline;
    private static List<Setting> activeSettings = SETTINGS;
    private static List<ShopEntry> activeShop = SHOP;
    private static boolean applySettings = true;
    private static boolean applyShop = true;
    private static boolean exactLayout;

    private CfgReplay() { }

    static void start(MinecraftClient client) {
        start(client, SETTINGS, SHOP, true, true, false);
    }

    static void start(MinecraftClient client, List<Setting> settings,
                      List<ShopEntry> shop, boolean settingsEnabled,
                      boolean shopEnabled, boolean exact) {
        if (ShopClean.isActive()) {
            DonutCfg.notifyUser(client, "Donutcfg: finish /cleanshop before /startcfg.");
            return;
        }
        if (CfgProfiles.isSaving()) {
            DonutCfg.notifyUser(client, "Donutcfg: finish /savecfg first.");
            return;
        }
        if (active) {
            DonutCfg.notifyUser(client, "Donutcfg is already running; /stopcfg cancels it.");
            return;
        }
        if (client.player == null || client.getNetworkHandler() == null) {
            DonutCfg.notifyUser(client,
                    "Donutcfg: join DonutSMP before running /startcfg.");
            return;
        }
        activeSettings = List.copyOf(settings);
        activeShop = List.copyOf(shop);
        applySettings = settingsEnabled;
        applyShop = shopEnabled;
        exactLayout = exact;
        active = true;
        stage = applySettings ? Stage.OPEN_SETTINGS : Stage.OPEN_SHOP;
        settingIndex = 0;
        settingClicks = 0;
        shopIndex = 0;
        editIndex = 0;
        removeSlot = -1;
        completedShop = 0;
        skippedOccupied = 0;
        removedShop = 0;
        recoveries = 0;
        nextAction = 0L;
        deadline = 0L;
        CfgMessages.cancellable(client,
                "Donutcfg started. Do not interact until it finishes; /stopcfg cancels it.");
    }

    static void stop(MinecraftClient client, String reason) {
        if (!active) {
            DonutCfg.notifyUser(client, "Donutcfg is not running.");
            return;
        }
        active = false;
        if (client.player != null && client.currentScreen instanceof HandledScreen<?>)
            client.player.closeHandledScreen();
        DonutCfg.notifyUser(client, "Donutcfg stopped: " + reason + ".");
    }

    static boolean isActive() {
        return active;
    }

    static List<Setting> defaultSettings() { return SETTINGS; }

    static List<ShopEntry> defaultShop() { return SHOP; }

    static List<Setting> captureSettings(MinecraftClient client) {
        if (!screen(client, "Settings"))
            throw new DonutCfg.GuiAccessException("/settings menu is not open");
        List<Setting> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Slot slot : handler(client).slots) {
            if (slot.inventory == client.player.getInventory()) continue;
            ItemStack stack = slot.getStack();
            if (!hasLore(stack, "Click to toggle")
                    && !hasLore(stack, "Click to change")) continue;
            String label = stack.getName().getString();
            int colon = label.indexOf(':');
            if (colon < 1 || colon + 1 >= label.length()) continue;
            String prefix = label.substring(0, colon + 1).trim();
            String value = label.substring(colon + 1).trim();
            if (!value.isEmpty() && seen.add(prefix.toLowerCase(Locale.ROOT)))
                result.add(new Setting(prefix, value));
        }
        if (result.size() < 30)
            throw new DonutCfg.GuiAccessException("/settings snapshot is incomplete ("
                    + result.size() + " entries)");
        return List.copyOf(result);
    }

    static List<ShopEntry> captureShop(MinecraftClient client) {
        if (!screen(client, "Quick Buy") || editMode(client))
            throw new DonutCfg.GuiAccessException("Quick Buy must be open outside Edit mode");
        List<ShopEntry> result = new ArrayList<>();
        for (int index = 0; index < 45; index++) {
            int slotId = index;
            Slot slot = shopSlot(client, index);
            if (slot == null)
                throw new DonutCfg.GuiAccessException("Quick Buy slot " + index + " is missing");
            ItemStack stack = slot.getStack();
            if (isEmptyShop(stack)) continue;
            if (stack.isEmpty())
                throw new DonutCfg.GuiAccessException("Quick Buy slot " + index + " is not loaded");
            if (stack.isOf(Items.SHULKER_BOX) && !isEmptyShulker(stack))
                throw new IllegalStateException("filled shulker at slot " + index
                        + " cannot be replayed exactly");
            Map<String, Integer> enchants = new HashMap<>();
            for (RegistryEntry<Enchantment> enchant :
                    stack.getEnchantments().getEnchantments()) {
                String key = enchant.getKey().orElseThrow(() ->
                        new IllegalStateException("unregistered enchantment at slot "
                                + slotId)).getValue().getPath();
                if (!KNOWN_ENCHANTS.contains(key))
                    throw new IllegalStateException("unsupported enchantment "
                            + key + " at slot " + index);
                enchants.put(key, stack.getEnchantments().getLevel(enchant));
            }
            String potion = "";
            PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (contents != null) {
                if (!contents.customEffects().isEmpty() || contents.customColor().isPresent()
                        || contents.customName().isPresent())
                    throw new IllegalStateException("custom potion at slot " + index
                            + " cannot be replayed exactly");
                potion = contents.potion().flatMap(RegistryEntry::getKey)
                        .map(key -> key.getValue().getPath()).orElse("");
                if (potion.isEmpty())
                    throw new IllegalStateException("potion ID missing at slot " + index);
            }
            result.add(new ShopEntry(index, searchQuery(stack.getItem(), potion),
                    stack.getItem(), stack.getCount(), potion, Map.copyOf(enchants)));
        }
        return List.copyOf(result);
    }

    static boolean knownEnchant(String name) {
        return KNOWN_ENCHANTS.contains(name);
    }

    private static String searchQuery(Item item, String potion) {
        for (ShopEntry entry : SHOP)
            if (entry.item() == item && entry.potion().equals(potion))
                return entry.query();
        String path = Registries.ITEM.getId(item).getPath().replace('_', ' ');
        return path.substring(0, Math.min(path.length(), 15)).trim();
    }

    private static void complete(MinecraftClient client) {
        active = false;
        if (client.player != null && client.currentScreen instanceof HandledScreen<?>)
            client.player.closeHandledScreen();
        DonutCfg.notifyUser(client, "Donutcfg complete: "
                + (applySettings ? activeSettings.size() + " settings; " : "")
                + (applyShop ? completedShop + " Quick Buy entries added, "
                    + skippedOccupied + " kept, " + removedShop + " removed."
                    : "Quick Buy unchanged."));
    }

    static void tick(MinecraftClient client) {
        if (!active || System.currentTimeMillis() < nextAction) return;
        if (client.player == null || client.getNetworkHandler() == null) {
            stop(client, "connection lost");
            return;
        }
        long now = System.currentTimeMillis();
        switch (stage) {
            case OPEN_SETTINGS -> openCommand(client, "settings",
                    Stage.WAIT_SETTINGS);
            case WAIT_SETTINGS -> {
                if (screen(client, "Settings")) {
                    stage = Stage.SETTINGS;
                    nextAction = now + CLICK_DELAY;
                } else if (now > deadline) recover(client,
                        Stage.OPEN_SETTINGS, "settings menu did not open");
            }
            case SETTINGS -> configureSettings(client);
            case OPEN_SHOP -> openCommand(client, "shop", Stage.WAIT_SHOP);
            case WAIT_SHOP -> {
                if (screen(client, "Quick Buy")) {
                    stage = Stage.START_EDIT;
                    nextAction = now + CLICK_DELAY;
                } else if (now > deadline)
                    recover(client, Stage.OPEN_SHOP, "Quick Buy did not open");
            }
            case START_EDIT -> startEdit(client);
            case WAIT_EDIT -> waitEdit(client);
            case REMOVE_WRONG -> removeWrong(client);
            case WAIT_REMOVE -> waitRemove(client);
            case SAVE_EDIT -> saveEdit(client);
            case WAIT_SAVE -> waitSave(client);
            case SHOP -> configureShop(client);
            case WAIT_SELECT -> waitSelect(client);
            case WAIT_SEARCH_SIGN -> waitSearchSign(client);
            case SEARCH_RESULTS -> searchResults(client);
            case WAIT_ITEM_RESULT -> waitItemResult(client);
            case ENCHANTS -> configureEnchants(client);
            case WAIT_AMOUNT_SIGN -> waitAmountSign(client);
            case WAIT_QUICK_VERIFY -> waitQuickVerify(client);
        }
    }

    private static void configureSettings(MinecraftClient client) {
        if (!screen(client, "Settings")) {
            recover(client, Stage.OPEN_SETTINGS, "settings menu closed");
            return;
        }
        if (settingIndex >= activeSettings.size()) {
            client.player.closeHandledScreen();
            if (!applyShop) {
                complete(client);
                return;
            }
            stage = Stage.OPEN_SHOP;
            nextAction = System.currentTimeMillis() + 700L;
            DonutCfg.notifyUser(client,
                    "Donutcfg: settings complete; configuring Quick Buy.");
            return;
        }
        Setting wanted = activeSettings.get(settingIndex);
        Slot slot = findNamedPrefix(client, wanted.prefix());
        if (slot == null) {
            fail(client, "setting missing: " + wanted.prefix());
            return;
        }
        String label = slot.getStack().getName().getString();
        String target = wanted.prefix() + " " + wanted.wanted();
        if (label.equalsIgnoreCase(target)) {
            settingIndex++;
            settingClicks = 0;
            recoveries = 0;
            return;
        }
        if (++settingClicks > 5) {
            fail(client, "could not set " + wanted.prefix()
                    + " to " + wanted.wanted());
            return;
        }
        click(client, slot);
        nextAction = System.currentTimeMillis() + CLICK_DELAY;
        deadline = System.currentTimeMillis() + OPEN_TIMEOUT;
    }

    private static void configureShop(MinecraftClient client) {
        if (!screen(client, "Quick Buy")) {
            recover(client, Stage.OPEN_SHOP, "Quick Buy closed");
            return;
        }
        if (shopIndex >= activeShop.size()) {
            complete(client);
            return;
        }
        ShopEntry wanted = activeShop.get(shopIndex);
        Slot target = shopSlot(client, wanted.preferredSlot());
        if (target == null) {
            fail(client, "Quick Buy slot " + wanted.preferredSlot() + " is missing");
            return;
        }
        if (matches(target.getStack(), wanted)) {
            skippedOccupied++;
            shopIndex++;
            recoveries = 0;
            return;
        }
        if (!isEmptyShop(target.getStack())) {
            fail(client, "Quick Buy slot " + wanted.preferredSlot()
                    + " changed while configuring " + wanted.query(), false);
            return;
        }
        click(client, target);
        stage = Stage.WAIT_SELECT;
        deadline = System.currentTimeMillis() + OPEN_TIMEOUT;
        nextAction = System.currentTimeMillis() + CLICK_DELAY;
    }

    private static void startEdit(MinecraftClient client) {
        if (!screen(client, "Quick Buy")) {
            recover(client, Stage.OPEN_SHOP, "Quick Buy closed before edit");
            return;
        }
        Slot edit = findNamed(client, "Edit");
        if (edit == null || !hasLore(edit.getStack(), "Click to edit")) {
            fail(client, "Quick Buy Edit button is missing or already active");
            return;
        }
        click(client, edit);
        stage = Stage.WAIT_EDIT;
        deadline = System.currentTimeMillis() + OPEN_TIMEOUT;
        nextAction = System.currentTimeMillis() + CLICK_DELAY;
    }

    private static void waitEdit(MinecraftClient client) {
        if (screen(client, "Quick Buy") && editMode(client)) {
            editIndex = 0;
            stage = Stage.REMOVE_WRONG;
        } else if (System.currentTimeMillis() > deadline)
            fail(client, "Quick Buy did not enter edit mode");
    }

    private static void removeWrong(MinecraftClient client) {
        if (!screen(client, "Quick Buy") || !editMode(client)) {
            fail(client, "Quick Buy edit mode closed unexpectedly");
            return;
        }
        while (editIndex < 45) {
            Slot slot = shopSlot(client, editIndex);
            if (slot == null) {
                fail(client, "Quick Buy slot " + editIndex + " is missing");
                return;
            }
            ItemStack stack = slot.getStack();
            ShopEntry expected = entryAt(editIndex);
            if (!shouldClearSlot(editIndex, stack, expected)
                    || hasLore(stack, "Click to add back")) {
                editIndex++;
                continue;
            }
            if (!hasLore(stack, "Click to remove")) {
                fail(client, "cannot identify removal action at Quick Buy slot "
                        + editIndex);
                return;
            }
            if (!exactLayout && !isKnownOldEntry(editIndex, stack)
                    && (expected == null || !stack.isOf(expected.item()))) {
                fail(client, "Quick Buy slot " + editIndex
                        + " contains an unknown personal entry; left untouched", false);
                return;
            }
            removeSlot = editIndex;
            click(client, slot);
            stage = Stage.WAIT_REMOVE;
            deadline = System.currentTimeMillis() + OPEN_TIMEOUT;
            nextAction = System.currentTimeMillis() + CLICK_DELAY;
            return;
        }
        stage = Stage.SAVE_EDIT;
    }

    private static void waitRemove(MinecraftClient client) {
        if (!screen(client, "Quick Buy") || !editMode(client)) {
            fail(client, "Quick Buy edit mode closed during removal");
            return;
        }
        Slot slot = shopSlot(client, removeSlot);
        if (slot != null && hasLore(slot.getStack(), "Click to add back")) {
            removedShop++;
            editIndex = removeSlot + 1;
            stage = Stage.REMOVE_WRONG;
        } else if (System.currentTimeMillis() > deadline)
            fail(client, "Quick Buy did not acknowledge removal at slot " + removeSlot);
    }

    private static void saveEdit(MinecraftClient client) {
        Slot edit = findNamed(client, "Edit");
        if (!screen(client, "Quick Buy") || edit == null || !editMode(client)) {
            fail(client, "Quick Buy Edit button missing before save");
            return;
        }
        click(client, edit);
        stage = Stage.WAIT_SAVE;
        deadline = System.currentTimeMillis() + OPEN_TIMEOUT;
        nextAction = System.currentTimeMillis() + CLICK_DELAY;
    }

    private static void waitSave(MinecraftClient client) {
        if (screen(client, "Quick Buy") && !editMode(client)) {
            for (int index = 0; index < 45; index++) {
                Slot slot = shopSlot(client, index);
                ShopEntry expected = entryAt(index);
                if (slot == null || shouldClearSlot(index, slot.getStack(), expected)) {
                    if (System.currentTimeMillis() > deadline)
                        fail(client, "Quick Buy did not save edit at slot " + index);
                    return;
                }
            }
            stage = Stage.SHOP;
            nextAction = System.currentTimeMillis() + CLICK_DELAY;
        } else if (System.currentTimeMillis() > deadline)
            fail(client, "Quick Buy did not save edit mode");
    }

    private static void waitSelect(MinecraftClient client) {
        if (screen(client, "Orders -> Select Item")) {
            Slot search = findNamed(client, "Search");
            if (search == null) {
                fail(client, "Search button missing");
                return;
            }
            click(client, search);
            stage = Stage.WAIT_SEARCH_SIGN;
            deadline = System.currentTimeMillis() + OPEN_TIMEOUT;
            nextAction = System.currentTimeMillis() + CLICK_DELAY;
        } else if (System.currentTimeMillis() > deadline)
            recover(client, Stage.OPEN_SHOP, "item picker did not open");
    }

    private static void waitSearchSign(MinecraftClient client) {
        if (client.currentScreen instanceof AbstractSignEditScreen) {
            submitSign(client, activeShop.get(shopIndex).query());
            stage = Stage.SEARCH_RESULTS;
            deadline = System.currentTimeMillis() + OPEN_TIMEOUT;
            nextAction = System.currentTimeMillis() + 600L;
        } else if (System.currentTimeMillis() > deadline)
            recover(client, Stage.OPEN_SHOP, "search sign did not open");
    }

    private static void searchResults(MinecraftClient client) {
        if (!screen(client, "Orders -> Select Item")) {
            if (System.currentTimeMillis() > deadline)
                recover(client, Stage.OPEN_SHOP, "search results did not open");
            return;
        }
        ShopEntry wanted = activeShop.get(shopIndex);
        Slot result = findShopResult(client, wanted);
        if (result == null) {
            if (System.currentTimeMillis() > deadline)
                skipUnavailable(client, wanted);
            return;
        }
        click(client, result);
        stage = Stage.WAIT_ITEM_RESULT;
        deadline = System.currentTimeMillis() + OPEN_TIMEOUT;
        nextAction = System.currentTimeMillis() + CLICK_DELAY;
    }

    private static void waitItemResult(MinecraftClient client) {
        ShopEntry wanted = activeShop.get(shopIndex);
        if (screen(client, "Pick Enchantments")) {
            stage = Stage.ENCHANTS;
            nextAction = System.currentTimeMillis() + CLICK_DELAY;
        } else if (client.currentScreen instanceof AbstractSignEditScreen) {
            submitSign(client, Integer.toString(wanted.amount()));
            stage = Stage.WAIT_QUICK_VERIFY;
            deadline = System.currentTimeMillis() + OPEN_TIMEOUT;
            nextAction = System.currentTimeMillis() + 600L;
        } else if (System.currentTimeMillis() > deadline)
            recover(client, Stage.OPEN_SHOP,
                    "item selection did not continue for " + wanted.query());
    }

    private static void configureEnchants(MinecraftClient client) {
        if (!screen(client, "Pick Enchantments")) {
            if (System.currentTimeMillis() > deadline)
                recover(client, Stage.OPEN_SHOP, "enchantment menu closed");
            return;
        }
        ShopEntry wanted = activeShop.get(shopIndex);
        if (wanted.enchants().isEmpty()) {
            for (Slot slot : handler(client).slots) {
                if (slot.getStack().isOf(Items.ENCHANTED_BOOK)
                        && hasLore(slot.getStack(), "Selected")) {
                    click(client, slot);
                    nextAction = System.currentTimeMillis() + CLICK_DELAY;
                    return;
                }
            }
        }
        for (Map.Entry<String, Integer> enchant : wanted.enchants().entrySet()) {
            Slot book = findEnchant(client, enchant.getKey(), enchant.getValue());
            if (book == null) {
                fail(client, "enchantment missing: " + enchant.getKey()
                        + " " + enchant.getValue()
                        + " for " + wanted.query());
                return;
            }
            if (!hasLore(book.getStack(), "Selected")) {
                click(client, book);
                nextAction = System.currentTimeMillis() + CLICK_DELAY;
                deadline = System.currentTimeMillis() + OPEN_TIMEOUT;
                return;
            }
        }
        Slot confirm = findNamed(client, "Confirm");
        if (confirm == null) {
            fail(client, "enchantment Confirm button missing");
            return;
        }
        click(client, confirm);
        stage = Stage.WAIT_AMOUNT_SIGN;
        deadline = System.currentTimeMillis() + OPEN_TIMEOUT;
        nextAction = System.currentTimeMillis() + CLICK_DELAY;
    }

    private static void waitAmountSign(MinecraftClient client) {
        if (client.currentScreen instanceof AbstractSignEditScreen) {
            submitSign(client, Integer.toString(activeShop.get(shopIndex).amount()));
            stage = Stage.WAIT_QUICK_VERIFY;
            deadline = System.currentTimeMillis() + OPEN_TIMEOUT;
            nextAction = System.currentTimeMillis() + 600L;
        } else if (System.currentTimeMillis() > deadline)
            recover(client, Stage.OPEN_SHOP, "amount sign did not open");
    }

    private static void waitQuickVerify(MinecraftClient client) {
        if (screen(client, "Quick Buy")) {
            ShopEntry wanted = activeShop.get(shopIndex);
            Slot target = shopSlot(client, wanted.preferredSlot());
            if (target != null && matches(target.getStack(), wanted)) {
                completedShop++;
                shopIndex++;
                recoveries = 0;
                stage = Stage.SHOP;
                nextAction = System.currentTimeMillis() + CLICK_DELAY;
            } else if (System.currentTimeMillis() > deadline)
                recover(client, Stage.OPEN_SHOP,
                        "Quick Buy did not save " + wanted.query());
        } else if (System.currentTimeMillis() > deadline)
            recover(client, Stage.OPEN_SHOP, "Quick Buy did not return");
    }

    private static void openCommand(MinecraftClient client, String command,
                                    Stage waiting) {
        closeCurrentMenu(client);
        client.player.networkHandler.sendChatCommand(command);
        stage = waiting;
        deadline = System.currentTimeMillis() + OPEN_TIMEOUT;
        nextAction = System.currentTimeMillis() + 600L;
    }

    private static void recover(MinecraftClient client, Stage reopen,
                                String reason) {
        if (++recoveries > 3) {
            fail(client, reason + " after 3 retries");
            return;
        }
        closeCurrentMenu(client);
        stage = reopen;
        nextAction = System.currentTimeMillis() + 800L;
        deadline = 0L;
        DonutCfg.notifyUser(client, "Donutcfg: " + reason
                + "; retry " + recoveries + "/3.");
    }

    private static void skipUnavailable(MinecraftClient client, ShopEntry wanted) {
        DonutCfg.notifyUser(client, "Donutcfg: no shop result for "
                + new ItemStack(wanted.item()).getName().getString() + " (search: "
                + wanted.query() + "); skipping slot " + wanted.preferredSlot() + ".");
        closeCurrentMenu(client);
        shopIndex++;
        recoveries = 0;
        stage = Stage.OPEN_SHOP;
        nextAction = System.currentTimeMillis() + 800L;
    }

    private static void closeCurrentMenu(MinecraftClient client) {
        if (client.player != null && client.currentScreen instanceof HandledScreen<?>)
            client.player.closeHandledScreen();
        else if (client.currentScreen != null)
            client.setScreen(null);
    }

    private static void fail(MinecraftClient client, String reason) {
        fail(client, reason, true);
    }

    private static void fail(MinecraftClient client, String reason, boolean guiError) {
        active = false;
        if (client.player != null && client.currentScreen instanceof HandledScreen<?>)
            client.player.closeHandledScreen();
        DonutCfg.notifyUser(client, "Donutcfg stopped: " + reason + ".");
        if (guiError) DonutCfg.notifyGuiCompatibilityHint(client);
    }

    private static void submitSign(MinecraftClient client, String value) {
        String[] messages = ((SignScreenAccessor) client.currentScreen)
                .donutcfg$getMessages();
        Arrays.fill(messages, "");
        messages[0] = value;
        client.setScreen(null);
    }

    private static boolean screen(MinecraftClient client, String title) {
        return client.currentScreen instanceof HandledScreen<?> screen
                && screen.getTitle().getString().equalsIgnoreCase(title);
    }

    private static ScreenHandler handler(MinecraftClient client) {
        return client.player.currentScreenHandler;
    }

    private static void click(MinecraftClient client, Slot slot) {
        client.interactionManager.clickSlot(handler(client).syncId, slot.id,
                0, SlotActionType.PICKUP, client.player);
    }

    private static Slot findNamed(MinecraftClient client, String name) {
        for (Slot slot : handler(client).slots)
            if (slot.getStack().getName().getString().equalsIgnoreCase(name))
                return slot;
        return null;
    }

    private static Slot findNamedPrefix(MinecraftClient client, String prefix) {
        for (Slot slot : handler(client).slots)
            if (slot.getStack().getName().getString().toLowerCase(Locale.ROOT)
                    .startsWith(prefix.toLowerCase(Locale.ROOT))) return slot;
        return null;
    }

    private static Slot shopSlot(MinecraftClient client, int wantedId) {
        for (Slot slot : handler(client).slots) {
            if (slot.id == wantedId) return slot;
        }
        return null;
    }

    private static ShopEntry entryAt(int slotId) {
        for (ShopEntry entry : activeShop)
            if (entry.preferredSlot() == slotId) return entry;
        return null;
    }

    private static boolean shouldClearSlot(int slotId, ItemStack stack,
                                           ShopEntry expected) {
        if (isEmptyShop(stack)) return false;
        if (expected != null) return !matches(stack, expected);
        if (exactLayout) return true;
        // Remove only superseded leftovers from the old preset, not personal entries.
        return (slotId >= 22 && slotId <= 24
                && stack.isOf(Items.TOTEM_OF_UNDYING))
                || (slotId == 44 && stack.isOf(Items.DIAMOND_BOOTS));
    }

    private static boolean isKnownOldEntry(int slotId, ItemStack stack) {
        Item oldItem = switch (slotId) {
            case 13, 14, 15, 22, 23, 24 -> Items.TOTEM_OF_UNDYING;
            case 21 -> Items.NETHERITE_SWORD;
            case 28 -> Items.DIAMOND_PICKAXE;
            case 29 -> Items.NETHERITE_PICKAXE;
            case 30 -> Items.TRIDENT;
            case 31 -> Items.MACE;
            case 32 -> Items.SHULKER_BOX;
            case 33 -> Items.BUCKET;
            case 34 -> Items.NETHERITE_LEGGINGS;
            case 35, 37 -> Items.NETHERITE_BOOTS;
            case 36 -> Items.DIAMOND_BOOTS;
            case 44 -> Items.DIAMOND_BOOTS;
            default -> null;
        };
        return (oldItem != null && stack.isOf(oldItem))
                || (slotId == 28 && stack.isOf(Items.NETHERITE_LEGGINGS));
    }

    private static boolean editMode(MinecraftClient client) {
        Slot edit = findNamed(client, "Edit");
        return edit != null && hasLore(edit.getStack(), "Click to save changes");
    }

    private static boolean isEmptyShop(ItemStack stack) {
        return stack.isOf(Items.GRAY_STAINED_GLASS_PANE)
                && stack.getName().getString().equalsIgnoreCase("Empty");
    }

    private static Slot findShopResult(MinecraftClient client,
                                       ShopEntry wanted) {
        for (Slot slot : handler(client).slots) {
            if (slot.id < 0 || slot.id >= 45
                    || slot.inventory == client.player.getInventory()) continue;
            ItemStack stack = slot.getStack();
            if (stack.isOf(wanted.item())
                    && (!stack.isOf(Items.SHULKER_BOX) || isEmptyShulker(stack))
                    && (wanted.potion().isEmpty()
                        || stack.getComponents().toString()
                            .contains("minecraft:" + wanted.potion())))
                return slot;
        }
        return null;
    }

    private static Slot findEnchant(MinecraftClient client, String enchant,
                                    int wanted) {
        RegistryKey<Enchantment> key = enchantKey(enchant);
        for (Slot slot : handler(client).slots) {
            if (slot.inventory == client.player.getInventory()) continue;
            if (!slot.getStack().isOf(Items.ENCHANTED_BOOK)) continue;
            ItemEnchantmentsComponent stored = slot.getStack().get(
                    DataComponentTypes.STORED_ENCHANTMENTS);
            if (enchantLevel(stored, key) == wanted) return slot;
        }
        return null;
    }

    private static RegistryKey<Enchantment> enchantKey(String name) {
        return switch (name) {
            case "protection" -> Enchantments.PROTECTION;
            case "blast_protection" -> Enchantments.BLAST_PROTECTION;
            case "feather_falling" -> Enchantments.FEATHER_FALLING;
            case "mending" -> Enchantments.MENDING;
            case "unbreaking" -> Enchantments.UNBREAKING;
            case "aqua_affinity" -> Enchantments.AQUA_AFFINITY;
            case "respiration" -> Enchantments.RESPIRATION;
            case "sharpness" -> Enchantments.SHARPNESS;
            case "looting" -> Enchantments.LOOTING;
            case "fire_aspect" -> Enchantments.FIRE_ASPECT;
            case "knockback" -> Enchantments.KNOCKBACK;
            case "sweeping_edge" -> Enchantments.SWEEPING_EDGE;
            case "efficiency" -> Enchantments.EFFICIENCY;
            case "fortune" -> Enchantments.FORTUNE;
            case "silk_touch" -> Enchantments.SILK_TOUCH;
            case "impaling" -> Enchantments.IMPALING;
            case "loyalty" -> Enchantments.LOYALTY;
            case "channeling" -> Enchantments.CHANNELING;
            case "riptide" -> Enchantments.RIPTIDE;
            case "density" -> Enchantments.DENSITY;
            case "wind_burst" -> Enchantments.WIND_BURST;
            case "breach" -> Enchantments.BREACH;
            default -> throw new IllegalArgumentException(name);
        };
    }

    private static int enchantLevel(ItemEnchantmentsComponent enchants,
                                    RegistryKey<Enchantment> key) {
        if (enchants == null) return 0;
        return enchants.getEnchantments().stream()
                .filter(entry -> entry.matchesKey(key))
                .mapToInt(enchants::getLevel).findFirst().orElse(0);
    }

    private static boolean hasLore(ItemStack stack, String text) {
        var lore = stack.get(net.minecraft.component.DataComponentTypes.LORE);
        return lore != null && lore.lines().stream().anyMatch(line ->
                line.getString().equalsIgnoreCase(text));
    }

    private static boolean matches(ItemStack stack, ShopEntry wanted) {
        if (!stack.isOf(wanted.item()) || stack.getCount() != wanted.amount())
            return false;
        if (stack.isOf(Items.SHULKER_BOX) && !isEmptyShulker(stack))
            return false;
        String components = stack.getComponents().toString();
        if (!wanted.potion().isEmpty()
                && !components.contains("minecraft:" + wanted.potion()))
            return false;
        Set<String> actual = new HashSet<>();
        for (String known : KNOWN_ENCHANTS)
            if (enchantLevel(stack.getEnchantments(), enchantKey(known)) > 0)
                actual.add(known);
        if (stack.getEnchantments().getEnchantments().size() != actual.size())
            return false;
        if (!actual.equals(wanted.enchants().keySet())) return false;
        for (Map.Entry<String, Integer> enchant : wanted.enchants().entrySet())
            if (enchantLevel(stack.getEnchantments(), enchantKey(enchant.getKey()))
                    != enchant.getValue()) return false;
        return true;
    }

    private static boolean isEmptyShulker(ItemStack stack) {
        ContainerComponent contents = stack.get(DataComponentTypes.CONTAINER);
        return contents == null || contents.streamNonEmpty().findAny().isEmpty();
    }

    private static ShopEntry item(int slot, String query, Item item,
                                  int amount) {
        return new ShopEntry(slot, query, item, amount, "", Map.of());
    }

    private static ShopEntry armor(int slot, String query, Item item,
                                   Map<String, Integer> enchants) {
        return new ShopEntry(slot, query, item, 1, "", enchants);
    }

    private static ShopEntry potion(int slot, String query, String potion) {
        return new ShopEntry(slot, query, Items.SPLASH_POTION, 1,
                potion, Map.of());
    }
}
