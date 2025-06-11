package org.purple.purpleTags;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;

import java.util.*;
import java.util.regex.Pattern;

public final class PurpleTags extends JavaPlugin implements Listener, TabExecutor {
    private static final Pattern TAG_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");
    public class Tag {
        public final String displayName;
        Tag(String name) { this.displayName = name; }
    }

    Map<String, Tag> tagDefs = new HashMap<>();
    Map<UUID, String> playerTags = new HashMap<>();
    private ScoreboardManager sbm;
    private Scoreboard board;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigData();

        sbm = Bukkit.getScoreboardManager();
        board = sbm.getMainScoreboard();

        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("tags").setExecutor(this);
    }

    @Override
    public void onDisable() {
        saveConfigData();
    }

    private void loadConfigData() {
        FileConfiguration cfg = getConfig();
        if (cfg.isConfigurationSection("defs")) {
            for (String id : cfg.getConfigurationSection("defs").getKeys(false)) {
                tagDefs.put(id, new Tag(cfg.getString("defs." + id)));
            }
        }
        if (cfg.isConfigurationSection("playerTags")) {
            for (String u : cfg.getConfigurationSection("playerTags").getKeys(false)) {
                playerTags.put(UUID.fromString(u), cfg.getString("playerTags." + u));
            }
        }
    }

    private void saveConfigData() {
        FileConfiguration cfg = getConfig();
        cfg.set("defs", null);
        tagDefs.forEach((id, tag) -> cfg.set("defs." + id, tag.displayName));
        cfg.set("playerTags", null);
        playerTags.forEach((u, id) -> cfg.set("playerTags." + u.toString(), id));
        saveConfig();
    }

    private void applyTag(Player p, String tagId) {
        board.getTeams().forEach(t -> t.removeEntry(p.getName()));

        String disp = null;
        if (tagId != null && tagDefs.containsKey(tagId)) {
            disp = tagDefs.get(tagId).displayName;
        }

        if (disp != null) {
            String teamName = "tag_" + p.getUniqueId().toString().substring(0, 16);
            Team team = board.getTeam(teamName);
            if (team == null) team = board.registerNewTeam(teamName);
            team.setPrefix(disp);
            team.addEntry(p.getName());
        }

        p.setScoreboard(board);
        p.setPlayerListName((disp != null ? disp : "") + p.getName());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        String tagId = playerTags.get(e.getPlayer().getUniqueId());
        applyTag(e.getPlayer(), tagId);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        String id = playerTags.get(e.getPlayer().getUniqueId());
        if (id != null && tagDefs.containsKey(id)) {
            String disp = tagDefs.get(id).displayName;
            e.setFormat(disp + "%s: %s");
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] args) {
        if (!(s instanceof Player) || !s.hasPermission("purpletags.use")) {
            s.sendMessage("§cVocê precisa ser OP para usar este comando.");
            return true;
        }

        if (args.length < 1) return false;

        switch (args[0].toLowerCase()) {
            case "create":
                if (args.length < 3) {
                    s.sendMessage("Uso: /tags create <tagID> <nome>");
                    break;
                }
                String id = args[1];
                if (!TAG_ID_PATTERN.matcher(id).matches()) {
                    s.sendMessage("§cTagID inválido! Apenas A-Z, a-z, 0-9 e _.");
                    break;
                }
                String nome = ChatColor.translateAlternateColorCodes('&',
                        String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                tagDefs.put(id, new Tag(nome));
                saveConfigData();
                s.sendMessage("§aTag criada: ID=" + id + " nome=" + nome);
                break;

            case "list":
                if (tagDefs.isEmpty()) {
                    s.sendMessage("§eNenhuma tag registrada.");
                } else {
                    s.sendMessage("§dTags disponíveis:");
                    tagDefs.forEach((tid, t) ->
                            s.sendMessage("§7- " + tid + ": " + t.displayName));
                }
                break;

            case "delete":
                if (args.length != 2) {
                    s.sendMessage("Uso: /tags delete <tagID>");
                    break;
                }
                String del = args[1];
                if (tagDefs.remove(del) != null) {
                    playerTags.entrySet().removeIf(en -> en.getValue().equals(del));
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (!playerTags.containsKey(p.getUniqueId())) {
                            applyTag(p, null);
                        }
                    }
                    saveConfigData();
                    s.sendMessage("§aTag removida: " + del);
                } else {
                    s.sendMessage("§cTag não encontrada: " + del);
                }
                break;

            case "set":
                if (args.length != 3) {
                    s.sendMessage("Uso: /tags set <player> <tagID>");
                    break;
                }
                Player tgt = Bukkit.getPlayer(args[1]);
                if (tgt == null) {
                    s.sendMessage("§cJogador não encontrado.");
                    break;
                }
                String tid = args[2];
                if (!tagDefs.containsKey(tid)) {
                    s.sendMessage("§cTagID não existe.");
                    break;
                }
                playerTags.put(tgt.getUniqueId(), tid);
                applyTag(tgt, tid);
                saveConfigData();
                s.sendMessage("§aTag aplicada em §f" + tgt.getName() + "§a: " + tid);
                break;

            case "clear":
                if (args.length != 2) {
                    s.sendMessage("Uso: /tags clear <player>");
                    break;
                }
                Player tgt2 = Bukkit.getPlayer(args[1]);
                if (tgt2 == null) {
                    s.sendMessage("§cJogador não encontrado.");
                    break;
                }
                playerTags.remove(tgt2.getUniqueId());
                applyTag(tgt2, null);
                saveConfigData();
                s.sendMessage("§aTag removida de §f" + tgt2.getName());
                break;

            default:
                return false;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "list", "delete", "set", "clear");
        }
        if (args.length == 2 &&
                Arrays.asList("delete", "set", "clear").contains(args[0].toLowerCase())) {
            if (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("clear")) {
                List<String> names = new ArrayList<>();
                Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
                return names;
            } else {
                return new ArrayList<>(tagDefs.keySet());
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return new ArrayList<>(tagDefs.keySet());
        }
        return Collections.emptyList();
    }

    // API pública para outros plugins
    public static String getTagOf(OfflinePlayer player) {
        PurpleTags plugin = JavaPlugin.getPlugin(PurpleTags.class);
        if (plugin == null) return null;
        String tagId = plugin.playerTags.get(player.getUniqueId());
        if (tagId == null) return null;
        Tag t = plugin.tagDefs.get(tagId);
        return t != null ? t.displayName : null;
    }
}
