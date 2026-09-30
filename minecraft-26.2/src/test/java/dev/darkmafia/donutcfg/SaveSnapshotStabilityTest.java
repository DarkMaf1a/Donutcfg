package dev.darkmafia.donutcfg;

import java.util.List;
import java.util.Map;

/** Standalone regression check; run with java, no Minecraft client is started. */
public final class SaveSnapshotStabilityTest {
    public static void main(String[] args) {
        var stability = new CfgProfiles.SnapshotStability();
        var first = List.of(entry(1, Map.of()));
        // Server refreshes make fresh snapshots. Equal profile values must keep the timer.
        require(!stability.observe(first, 100L, 600L), "first snapshot must wait");
        require(!stability.observe(List.of(entry(1, Map.of())), 300L, 600L), "must wait 600 ms");
        require(stability.observe(List.of(entry(1, Map.of())), 700L, 600L),
                "equal refreshed snapshots must become ready");
        require(!stability.observe(List.of(entry(2, Map.of())), 800L, 600L),
                "amount change must restart waiting");
        require(stability.observe(List.of(entry(2, Map.of())), 1_400L, 600L),
                "stable new amount must become ready");
        require(!stability.observe(List.of(entry(2, Map.of("unbreaking", 3))),
                1_500L, 600L), "enchantment change must restart waiting");
        stability.reset();
        require(!stability.observe(first, 3_000L, 600L), "new menu must wait again");
        stability.reset();
        require(!stability.observe(List.of(), 4_000L, 2_000L), "empty menu must wait");
        require(!stability.observe(List.of(), 4_600L, 2_000L), "empty wait must be longer");
        require(stability.observe(List.of(), 6_000L, 2_000L), "stable empty layout must save");
        System.out.println("Save snapshot stability regression checks passed.");
    }

    private static CfgReplay.ShopEntry entry(int amount, Map<String, Integer> enchants) {
        // Item identity is unchanged; null avoids bootstrapping Minecraft's item registry.
        return new CfgReplay.ShopEntry(0, "totem", null, amount, "", enchants);
    }

    private static void require(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
