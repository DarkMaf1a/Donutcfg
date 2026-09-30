package dev.darkmafia.donutcfg;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** No client/server launch: exercises the real command grammar and recoverable file operations. */
public final class CommandsAndProfilesTest {
    public static void main(String[] args) throws Exception {
        var calls = new ArrayList<String>();
        var dispatcher = new CommandDispatcher<Object>();
        CfgCommands.register(dispatcher, new CfgCommands.Actions() {
            public void start(CfgProfiles.Mode mode, String name) { calls.add("start:" + mode + ":" + name); }
            public void select(String name) { calls.add("select:" + name); }
            public void save(String name) { calls.add("save:" + name); }
            public void delete(String name) { calls.add("delete:" + name); }
            public void mode(CfgProfiles.Mode mode) { calls.add("mode:" + mode); }
            public void stop() { calls.add("stop"); }
            public void clean() { calls.add("clean"); }
            public void help() { calls.add("help"); }
            public void list() { calls.add("list"); }
            public List<String> names() { return List.of("default", "Alice", "pvp"); }
        });
        assertCommand(dispatcher, calls, "setcfg default", "select:default");
        assertCommand(dispatcher, calls, "startcfg all default", "start:ALL:default");
        assertCommand(dispatcher, calls, "startcfg settings Alice", "start:SETTINGS:Alice");
        assertCommand(dispatcher, calls, "startcfg shop pvp", "start:SHOP:pvp");
        assertCommand(dispatcher, calls, "startcfg", "start:null:null");
        assertCommand(dispatcher, calls, "startcfg shop", "start:SHOP:null");
        assertCommand(dispatcher, calls, "loadcfg all pvp", "start:ALL:pvp");
        assertCommand(dispatcher, calls, "loadcfg", "start:null:null");
        assertCommand(dispatcher, calls, "cfgmode settings", "mode:SETTINGS");
        assertCommand(dispatcher, calls, "cfglist", "list");
        assertCommand(dispatcher, calls, "del cfg Alice", "delete:Alice");
        assertCommand(dispatcher, calls, "delcfg pvp", "delete:pvp");
        assertCommand(dispatcher, calls, "stopcfg", "stop");
        assertCommand(dispatcher, calls, "cfghelp", "help");
        assertCommand(dispatcher, calls, "cfg help", "help");
        assertCommand(dispatcher, calls, "cfg", "help");
        assertCommand(dispatcher, calls, "cfg clean", "clean");
        assertCommand(dispatcher, calls, "cleanshop", "clean");
        assertCommand(dispatcher, calls, "savecfg", "save:null");
        assertCommand(dispatcher, calls, "savecfg Alice", "save:Alice");
        for (String invalid : List.of("startcfg pvp", "startcfg wrong pvp", "setcfg", "del cfg")) {
            calls.clear();
            try { dispatcher.execute(invalid, new Object()); throw new AssertionError("accepted " + invalid); }
            catch (CommandSyntaxException expected) { require(calls.isEmpty(), "invalid command had side effects"); }
        }
        var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("startcfg all d", new Object())).get();
        require(suggestions.getList().stream().anyMatch(s -> s.getText().equals("default")), "profile suggestions missing");

        Path temp = Files.createTempDirectory("donutcfg-profiles-test-");
        try {
            require(ProfileFiles.list(temp).isEmpty(), "empty folder");
            Files.writeString(temp.resolve("default.json"), "default fixture");
            Files.writeString(temp.resolve("Alice.json"), "Alice fixture");
            Files.writeString(temp.resolve("pvp.json"), "pvp fixture");
            Files.writeString(temp.resolve(".selection.json"), "state fixture");
            Files.writeString(temp.resolve("readme.txt"), "not a profile");
            Files.writeString(temp.resolve("invalid name.json"), "invalid name");
            Files.createDirectory(temp.resolve("directory.json"));
            require(ProfileFiles.list(temp).equals(List.of("default", "Alice", "pvp")), "listing filters/order");
            Path backup = ProfileFiles.archive(temp, "Alice");
            require(!Files.exists(temp.resolve("Alice.json")), "profile not removed from root");
            require(Files.readString(backup).equals("Alice fixture"), "backup data lost");
            Files.writeString(temp.resolve("Alice.json"), "second fixture");
            Path secondBackup = ProfileFiles.archive(temp, "Alice");
            require(!backup.equals(secondBackup), "old backup overwritten");
            require(Files.readString(backup).equals("Alice fixture"), "old backup modified");
            require(ProfileFiles.list(temp).equals(List.of("default", "pvp")), "deleted files still listed");
            for (String name : List.of("default", "DEFAULT", "../escape", "..", "a/b", "")) {
                try { ProfileFiles.archive(temp, name); throw new AssertionError("unsafe delete accepted: " + name); }
                catch (IllegalArgumentException expected) { }
            }
            require(Files.readString(temp.resolve("default.json")).equals("default fixture"), "default changed");
            require(!ProfileFiles.validName(null), "null name accepted");
            require(!ProfileFiles.validName("a".repeat(49)), "oversized name accepted");
        } finally {
            // Only this test's explicit temporary fixtures are removed.
            try (var files = Files.walk(temp)) {
                for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        System.out.println("Command grammar and profile file regression checks passed.");
    }

    private static void assertCommand(CommandDispatcher<Object> dispatcher, List<String> calls,
                                      String command, String expected) throws Exception {
        calls.clear();
        dispatcher.execute(command, new Object());
        require(calls.equals(List.of(expected)), command + " => " + calls);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
