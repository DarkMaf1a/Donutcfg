package dev.darkmafia.donutcfg;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Named, human-readable snapshots of the server's Settings and Quick Buy menus. */
final class CfgProfiles {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting()
            .disableHtmlEscaping().create();
    private static final Path DIR = FabricLoader.getInstance().getConfigDir()
            .resolve("donutcfg-configs");
    private static final Path STATE = DIR.resolve(".selection.json");
    private static final long TIMEOUT = 6_000L;

    private enum SaveStage { OPEN_SETTINGS, WAIT_SETTINGS, OPEN_SHOP, WAIT_SHOP }
    enum Mode {
        ALL, SETTINGS, SHOP;

        boolean settings() { return this != SHOP; }
        boolean shop() { return this != SETTINGS; }
    }

    private record StoredEntry(int slot, String item, int amount, String query,
                               String potion, Map<String, Integer> enchants) { }
    private record StoredProfile(int schemaVersion, String minecraft, String savedAt,
                                 List<CfgReplay.Setting> settings,
                                 List<StoredEntry> shop) { }
    private record Selection(String name, Mode mode) { }

    private static String selectedName = "default";
    private static Mode selectedMode = Mode.ALL;
    private static boolean saving;
    private static SaveStage saveStage;
    private static String saveName;
    private static List<CfgReplay.Setting> capturedSettings;
    private static long nextAction;
    private static long deadline;
    private static final SnapshotStability stability = new SnapshotStability();

    private CfgProfiles() { }

    static void initialize() {
        try {
            ensureDefaultProfile();
        } catch (IOException | RuntimeException error) {
            DonutCfg.logProfileError("Could not create default config", error);
        }
        if (!Files.isRegularFile(STATE)) return;
        try {
            Selection selection = JSON.fromJson(Files.readString(STATE), Selection.class);
            if (selection != null && validName(selection.name())
                    && selection.mode() != null
                    && Files.isRegularFile(profilePath(selection.name()))) {
                selectedName = selection.name().equalsIgnoreCase("default")
                        ? "default" : selection.name();
                selectedMode = selection.mode();
            }
        } catch (IOException | RuntimeException error) {
            DonutCfg.logProfileError("Could not read selected config", error);
        }
    }

    static boolean isSaving() { return saving; }

    static void save(Minecraft client, String name) {
        if (name == null) {
            name = selectedName.equalsIgnoreCase("default")
                    ? client.getUser().getName() : selectedName;
            if (!validName(name) || name.equalsIgnoreCase("default")) name = "player";
        }
        if (name.equalsIgnoreCase("default")) {
            DonutCfg.notifyUser(client, "Donutcfg: default is the built-in preset. Use /savecfg <your-name> for a personal config.");
            return;
        }
        if (!validName(name)) {
            DonutCfg.notifyUser(client, "Donutcfg: name must be 1-48 letters, digits, _ or -.");
            return;
        }
        if (saving || CfgReplay.isActive() || ShopClean.isActive()) {
            DonutCfg.notifyUser(client, "Donutcfg: finish the current operation first.");
            return;
        }
        if (client.player == null || client.getConnection() == null) {
            DonutCfg.notifyUser(client, "Donutcfg: join the server before /savecfg.");
            return;
        }
        saveName = name;
        capturedSettings = null;
        saveStage = SaveStage.OPEN_SETTINGS;
        saving = true;
        nextAction = 0L;
        stability.reset();
        CfgMessages.cancellable(client, "Donutcfg: saving /settings and Quick Buy as '"
                + name + "'. Do not interact with the menus.");
    }

    static void cancelSave(Minecraft client) {
        if (!saving) return;
        saving = false;
        closeMenu(client);
        DonutCfg.notifyUser(client, "Donutcfg: /savecfg cancelled; no config was written.");
    }

    static void tick(Minecraft client) {
        if (!saving || System.currentTimeMillis() < nextAction) return;
        if (client.player == null || client.getConnection() == null) {
            fail(client, "disconnected while saving");
            return;
        }
        long now = System.currentTimeMillis();
        try {
            switch (saveStage) {
                case OPEN_SETTINGS -> {
                    closeMenu(client);
                    client.player.connection.sendCommand("settings");
                    saveStage = SaveStage.WAIT_SETTINGS;
                    deadline = now + TIMEOUT;
                    nextAction = now + 500L;
                    stability.reset();
                }
                case WAIT_SETTINGS -> {
                    if (menu(client, "Settings")) {
                        List<CfgReplay.Setting> settings = CfgReplay.captureSettings(client);
                        nextAction = now + 200L;
                        if (stability.observe(settings, now, 600L)) {
                            capturedSettings = settings;
                            closeMenu(client);
                            saveStage = SaveStage.OPEN_SHOP;
                            nextAction = now + 350L;
                        }
                        if (now > deadline && saving
                                && saveStage == SaveStage.WAIT_SETTINGS)
                            fail(client, "/settings kept changing; retry without editing settings");
                    } else {
                        stability.reset();
                        if (now > deadline) failGui(client, "/settings did not open");
                    }
                }
                case OPEN_SHOP -> {
                    client.player.connection.sendCommand("shop");
                    saveStage = SaveStage.WAIT_SHOP;
                    deadline = now + TIMEOUT;
                    nextAction = now + 600L;
                    stability.reset();
                }
                case WAIT_SHOP -> {
                    if (menu(client, "Quick Buy")) {
                        List<CfgReplay.ShopEntry> shop = CfgReplay.captureShop(client);
                        nextAction = now + 200L;
                        if (stability.observe(shop, now, shop.isEmpty() ? 2_000L : 600L)) {
                            writeProfile(saveName, capturedSettings, shop);
                            saving = false;
                            selectedName = saveName;
                            closeMenu(client);
                            try {
                                writeSelection();
                            } catch (IOException error) {
                                DonutCfg.logProfileError("Config saved, but selection was not persisted", error);
                                DonutCfg.notifyUser(client, "Donutcfg: config saved, but selection may reset on restart.");
                            }
                            DonutCfg.notifyUser(client, "Donutcfg: saved '" + saveName
                                    + "' (" + capturedSettings.size() + " settings, "
                                    + shop.size() + " Quick Buy entries) in " + DIR + ".");
                        }
                        if (now > deadline && saving)
                            fail(client, "/shop items kept changing; retry without editing Quick Buy");
                    } else {
                        stability.reset();
                        if (now > deadline) failGui(client, "/shop did not open");
                    }
                }
            }
        } catch (DonutCfg.GuiAccessException error) {
            // A title can arrive before its slot data. Retry incomplete menus until timeout.
            stability.reset();
            nextAction = now + 200L;
            if (now > deadline) {
                failGui(client, error.getMessage());
                DonutCfg.logProfileError("Could not read config menus", error);
            }
        } catch (IOException | RuntimeException error) {
            fail(client, error.getMessage() == null ? error.toString() : error.getMessage());
            DonutCfg.logProfileError("Could not save config", error);
        }
    }

    static void select(Minecraft client, String name) {
        if ("default".equalsIgnoreCase(name)) name = "default";
        if (!validName(name)) {
            DonutCfg.notifyUser(client, "Donutcfg: config '" + name
                    + "' not found in " + DIR + ".");
            return;
        }
        if (saving || CfgReplay.isActive() || ShopClean.isActive()) {
            DonutCfg.notifyUser(client, "Donutcfg: finish the current operation first.");
            return;
        }
        try {
            readProfile(name, selectedMode);
            String previous = selectedName;
            selectedName = name;
            try {
                writeSelection();
            } catch (IOException error) {
                selectedName = previous;
                throw error;
            }
            CfgMessages.send(client, Component.literal("Selected ").withStyle(ChatFormatting.GRAY)
                    .append(CfgMessages.profile(name))
                    .append(Component.literal(". Not started; use ").withStyle(ChatFormatting.GRAY))
                    .append(CfgMessages.command("/startcfg", "/startcfg"))
                    .append(Component.literal(" to load it.").withStyle(ChatFormatting.GRAY)));
        } catch (IOException | RuntimeException error) {
            DonutCfg.notifyUser(client, "Donutcfg: cannot select '" + name + "': "
                    + error.getMessage());
            DonutCfg.logProfileError("Could not select config", error);
        }
    }

    static void load(Minecraft client, Mode override) {
        load(client, override, null);
    }

    static void load(Minecraft client, Mode override, String name) {
        if (saving || CfgReplay.isActive() || ShopClean.isActive()) {
            DonutCfg.notifyUser(client, "Donutcfg: finish the current operation first.");
            return;
        }
        if (client.player == null || client.getConnection() == null) {
            DonutCfg.notifyUser(client, "Donutcfg: join the server before /startcfg.");
            return;
        }
        String requested = name == null ? selectedName
                : "default".equalsIgnoreCase(name) ? "default" : name;
        try {
            Mode mode = override == null ? selectedMode : override;
            Loaded loaded = readProfile(requested, mode);
            if (!requested.equals(selectedName) || mode != selectedMode) {
                String previousName = selectedName;
                Mode previousMode = selectedMode;
                selectedName = requested;
                selectedMode = mode;
                try {
                    writeSelection();
                } catch (IOException error) {
                    selectedName = previousName;
                    selectedMode = previousMode;
                    throw error;
                }
            }
            startLoaded(client, loaded, mode);
        } catch (IOException | RuntimeException error) {
            DonutCfg.notifyUser(client, "Donutcfg: cannot load '" + requested
                    + "': " + error.getMessage());
            DonutCfg.logProfileError("Could not load config", error);
        }
    }

    static void setMode(Minecraft client, Mode mode) {
        Mode previous = selectedMode;
        selectedMode = mode;
        try {
            writeSelection();
            DonutCfg.notifyUser(client, "Donutcfg: load mode "
                    + mode.name().toLowerCase(Locale.ROOT) + " for '"
                    + selectedName + "'.");
        } catch (IOException error) {
            selectedMode = previous;
            DonutCfg.notifyUser(client, "Donutcfg: could not save mode: "
                    + error.getMessage());
        }
    }

    static String status() {
        return "Selected config: " + selectedName + ", mode: "
                + selectedMode.name().toLowerCase(Locale.ROOT)
                + (Files.isRegularFile(profilePath(selectedName))
                    ? " (file)" : " (file missing)") + ".";
    }

    static String selectedName() { return selectedName; }

    static String selectedModeName() { return selectedMode.name().toLowerCase(Locale.ROOT); }

    static List<String> names() {
        try {
            return ProfileFiles.list(DIR);
        } catch (IOException error) {
            DonutCfg.logProfileError("Could not list configs", error);
            return List.of();
        }
    }

    static void list(Minecraft client) {
        try {
            List<String> names = ProfileFiles.list(DIR);
            CfgMessages.send(client, Component.literal("━━ Configs · " + names.size() + " ━━")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            for (String name : names) {
                boolean selected = name.equalsIgnoreCase(selectedName);
                CfgMessages.send(client, Component.literal(selected ? "▶ " : "• ")
                        .withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                        .append(CfgMessages.profile(name))
                        .append(Component.literal(selected ? "  (selected)" : "")
                                .withStyle(ChatFormatting.GREEN)));
            }
            if (names.isEmpty()) DonutCfg.notifyUser(client, "Donutcfg: no configs found.");
            CfgMessages.send(client, Component.literal("Click a name to prepare /setcfg. Loading starts only with /startcfg or /loadcfg.")
                    .withStyle(ChatFormatting.GRAY));
        } catch (IOException error) {
            DonutCfg.notifyUser(client, "Donutcfg: cannot list configs: " + error.getMessage());
            DonutCfg.logProfileError("Could not list configs", error);
        }
    }

    static void delete(Minecraft client, String name) {
        if (DonutCfg.isWorking()) {
            DonutCfg.notifyUser(client, "Donutcfg: finish or cancel the current operation first.");
            return;
        }
        try {
            Path backup = ProfileFiles.archive(DIR, name);
            if (name.equalsIgnoreCase(selectedName)) {
                selectedName = "default";
                try {
                    writeSelection();
                } catch (IOException error) {
                    DonutCfg.logProfileError("Config removed, but selection was not persisted", error);
                    DonutCfg.notifyUser(client, "Donutcfg: could not persist selection; default will be used after restart.");
                }
            }
            DonutCfg.notifyUser(client, "Donutcfg: removed '" + name
                    + "' from configs. Recoverable backup: " + backup + ".");
        } catch (IOException | IllegalArgumentException error) {
            DonutCfg.notifyUser(client, "Donutcfg: cannot delete '" + name + "': " + error.getMessage());
        }
    }

    private record Loaded(List<CfgReplay.Setting> settings,
                          List<CfgReplay.ShopEntry> shop) { }

    private static void startLoaded(Minecraft client, Loaded loaded,
                                    Mode mode) {
        DonutCfg.notifyUser(client, "Donutcfg: loading '" + selectedName + "' ("
                + mode.name().toLowerCase(Locale.ROOT) + ").");
        CfgReplay.start(client, loaded.settings(), loaded.shop(),
                mode.settings(), mode.shop(), true);
    }

    private static Loaded readProfile(String name, Mode mode) throws IOException {
        if (!validName(name)) throw new IllegalArgumentException("invalid config name");
        if (name.equals("default")) ensureDefaultProfile();
        Path path = profilePath(name);
        if (!Files.isRegularFile(path)) throw new IOException("file not found: " + path);
        if (Files.size(path) > 1_000_000L)
            throw new IOException("config is too large (limit 1 MB)");
        StoredProfile file = JSON.fromJson(Files.readString(path, StandardCharsets.UTF_8),
                StoredProfile.class);
        if (file == null || file.schemaVersion() != 1
                || (!"1.21.11".equals(file.minecraft()) && !"26.2".equals(file.minecraft()))
                || (mode.settings() && file.settings() == null)
                || (mode.shop() && file.shop() == null))
            throw new IllegalArgumentException("unsupported or incomplete config file");
        if ((mode.settings() && file.settings().size() > 90)
                || (mode.shop() && file.shop().size() > 45))
            throw new IllegalArgumentException("too many settings or Quick Buy entries");
        List<CfgReplay.Setting> settings = new ArrayList<>();
        Set<String> prefixes = new HashSet<>();
        for (CfgReplay.Setting setting : mode.settings() ? file.settings()
                : List.<CfgReplay.Setting>of()) {
            if (setting == null || setting.prefix() == null || setting.wanted() == null
                    || !setting.prefix().endsWith(":") || setting.wanted().isBlank()
                    || !prefixes.add(setting.prefix().toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("invalid/duplicate setting");
            settings.add(setting);
        }
        if (mode.settings() && settings.isEmpty())
            throw new IllegalArgumentException("no settings saved");
        List<CfgReplay.ShopEntry> shop = new ArrayList<>();
        Set<Integer> slots = new HashSet<>();
        for (StoredEntry entry : mode.shop() ? file.shop()
                : List.<StoredEntry>of()) {
            if (entry == null || entry.slot() < 0 || entry.slot() >= 45
                    || !slots.add(entry.slot()) || entry.item() == null
                    || entry.query() == null || entry.query().isBlank()
                    || entry.query().length() > 32 || entry.amount() < 1
                    || entry.amount() > 64 || entry.potion() == null
                    || entry.enchants() == null)
                throw new IllegalArgumentException("invalid Quick Buy entry");
            Identifier id = Identifier.tryParse(entry.item());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id))
                throw new IllegalArgumentException("unknown item: " + entry.item());
            Item item = BuiltInRegistries.ITEM.getValue(id);
            for (Map.Entry<String, Integer> enchant : entry.enchants().entrySet())
                if (!CfgReplay.knownEnchant(enchant.getKey())
                        || enchant.getValue() == null || enchant.getValue() < 1
                        || enchant.getValue() > 10)
                    throw new IllegalArgumentException("invalid enchantment at slot "
                            + entry.slot());
            shop.add(new CfgReplay.ShopEntry(entry.slot(), entry.query(), item,
                    entry.amount(), entry.potion(), Map.copyOf(entry.enchants())));
        }
        shop.sort((left, right) -> Integer.compare(left.preferredSlot(),
                right.preferredSlot()));
        return new Loaded(List.copyOf(settings), List.copyOf(shop));
    }

    private static void writeProfile(String name, List<CfgReplay.Setting> settings,
                                     List<CfgReplay.ShopEntry> shop) throws IOException {
        atomicWrite(profilePath(name), JSON.toJson(storedProfile(settings, shop)));
    }

    private static StoredProfile storedProfile(List<CfgReplay.Setting> settings,
                                               List<CfgReplay.ShopEntry> shop) {
        List<StoredEntry> entries = new ArrayList<>();
        for (CfgReplay.ShopEntry entry : shop)
            entries.add(new StoredEntry(entry.preferredSlot(),
                    BuiltInRegistries.ITEM.getKey(entry.item()).toString(), entry.amount(),
                    entry.query(), entry.potion(), entry.enchants()));
        return new StoredProfile(1, "26.2", Instant.now().toString(),
                settings, entries);
    }

    private static void ensureDefaultProfile() throws IOException {
        StoredProfile wanted = storedProfile(CfgReplay.defaultSettings(), CfgReplay.defaultShop());
        Path target = profilePath("default");
        if (Files.exists(target)) {
            if (!Files.isRegularFile(target))
                throw new IOException("default.json is not a regular file");
            StoredProfile existing = null;
            if (Files.size(target) <= 1_000_000L) {
                try {
                    existing = JSON.fromJson(Files.readString(target, StandardCharsets.UTF_8),
                            StoredProfile.class);
                } catch (RuntimeException invalid) {
                    // Preserve malformed or personal legacy files before restoring the preset.
                }
            }
            if (existing != null && existing.schemaVersion() == wanted.schemaVersion()
                    && wanted.minecraft().equals(existing.minecraft())
                    && wanted.settings().equals(existing.settings())
                    && wanted.shop().equals(existing.shop())) return;
            int suffix = 1;
            Path backup;
            do {
                backup = profilePath("default-user-" + suffix++);
            } while (Files.exists(backup));
            Files.copy(target, backup);
            DonutCfg.logProfileInfo("Previous default config preserved as " + backup);
        }
        atomicWrite(target, JSON.toJson(wanted));
        DonutCfg.logProfileInfo("Created built-in default config: " + target);
    }

    private static void writeSelection() throws IOException {
        atomicWrite(STATE, JSON.toJson(new Selection(selectedName, selectedMode)));
    }

    private static void atomicWrite(Path target, String contents) throws IOException {
        Files.createDirectories(DIR);
        Path temp = Files.createTempFile(DIR, "donutcfg-", ".tmp");
        try {
            Files.writeString(temp, contents + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static boolean validName(String name) {
        return ProfileFiles.validName(name);
    }

    private static Path profilePath(String name) {
        return DIR.resolve(name + ".json");
    }

    private static boolean menu(Minecraft client, String title) {
        return client.gui.screen() instanceof AbstractContainerScreen<?> screen
                && screen.getTitle().getString().equalsIgnoreCase(title);
    }

    private static void closeMenu(Minecraft client) {
        if (client.player != null && client.gui.screen() instanceof AbstractContainerScreen<?>)
            client.player.closeContainer();
    }

    /** Compare only the values saved to a profile, never live prices or protocol metadata. */
    static final class SnapshotStability {
        private List<?> previous;
        private long stableSince;

        void reset() {
            previous = null;
            stableSince = 0L;
        }

        boolean observe(List<?> snapshot, long now, long requiredWait) {
            if (!snapshot.equals(previous)) {
                previous = List.copyOf(snapshot);
                stableSince = now;
            }
            return now - stableSince >= requiredWait;
        }
    }

    private static void fail(Minecraft client, String reason) {
        saving = false;
        closeMenu(client);
        DonutCfg.notifyUser(client, "Donutcfg: /savecfg stopped: " + reason
                + ". Existing config was not changed.");
    }

    private static void failGui(Minecraft client, String reason) {
        fail(client, reason);
        DonutCfg.notifyGuiCompatibilityHint(client);
    }
}
