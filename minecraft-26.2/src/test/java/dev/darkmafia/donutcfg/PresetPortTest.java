package dev.darkmafia.donutcfg;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.Map;

/** Verifies the real 26.2 registries and approved preset, without starting a client or connecting. */
public final class PresetPortTest {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var settings = CfgReplay.defaultSettings();
        var shop = CfgReplay.defaultShop();
        require(settings.size() == 14, "default settings changed");
        require(shop.size() == 43, "default must have 43 entries");
        var slots = new HashSet<Integer>();
        for (var entry : shop) {
            require(slots.add(entry.preferredSlot()), "duplicate slot");
            require(entry.amount() >= 1 && entry.amount() <= 64, "invalid amount");
            require(!BuiltInRegistries.ITEM.getKey(entry.item()).getPath().equals("air"), "unregistered item");
        }
        for (int slot = 0; slot < 43; slot++) require(slots.contains(slot), "missing slot " + slot);
        require(shop.get(13).query().equals("bucket of puffer"), "puffer search changed");
        require(shop.get(21).item() == Items.FLINT_AND_STEEL && shop.get(21).enchants().isEmpty(), "plain flint and steel");
        require(shop.get(27).enchants().get("blast_protection") == 4, "diamond leggings blast protection");
        require(shop.get(36).enchants().get("blast_protection") == 4
                && shop.get(36).enchants().get("feather_falling") == 4, "diamond boots blast/falling");
        require(shop.get(33).enchants().equals(Map.of("breach", 4, "unbreaking", 3, "mending", 1)), "mace preset");
        require(shop.get(30).enchants().get("fortune") == 3, "fortune pickaxe");
        require(shop.get(38).enchants().get("silk_touch") == 1, "separate silk pickaxe");
        for (int slot = 39; slot <= 42; slot++) {
            require(shop.get(slot).enchants().get("protection") == 4, "extra protection armor " + slot);
            require(!shop.get(slot).enchants().containsKey("feather_falling"), "extra boots should not have feather falling");
        }
        System.out.println("Minecraft 26.2 registry and 43-item preset checks passed.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
