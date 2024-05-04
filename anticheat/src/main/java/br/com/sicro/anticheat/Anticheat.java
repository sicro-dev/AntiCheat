package br.com.sicro.anticheat;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Anticheat extends JavaPlugin implements Listener {

    private final Map<UUID, Integer> hackCountMap = new HashMap<>();
    private final Map<UUID, Long> lastCheckedMap = new HashMap<>();
    private final Map<UUID, Long> noBlockBelowTimeMap = new HashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("Plugin Anticheat ativado!");
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::removeNonBannedLogs, 20 * 30, 20 * 30);
    }

    @Override
    public void onDisable() {
        getLogger().info("Plugin Anticheat desativado!");
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Player) {
            Player player = (Player) damager;
            Entity entity = event.getEntity();
            double distance = player.getLocation().distance(entity.getLocation());
            if (distance > 3.6) {
                getLogger().info(player.getName() + " acertou " + entity.getType() + " a uma distância de " + distance + " blocos.");
                UUID playerId = player.getUniqueId();
                int hackCount = hackCountMap.getOrDefault(playerId, 0);
                hackCount++;
                hackCountMap.put(playerId, hackCount);
                lastCheckedMap.put(playerId, System.currentTimeMillis());

                if (hackCount >= 6) {
                    banPlayer(player);
                    hackCountMap.remove(playerId);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (event.getNewGameMode() == GameMode.CREATIVE || event.getNewGameMode() == GameMode.SPECTATOR) {
            noBlockBelowTimeMap.remove(playerId);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (player.getGameMode() == GameMode.SURVIVAL) {
            Location location = player.getLocation().subtract(0, 1, 0);
            Material blockBelow = location.getBlock().getType();

            if (blockBelow == Material.AIR) {
                long noBlockBelowTime = noBlockBelowTimeMap.getOrDefault(playerId, 0L);
                long currentTime = System.currentTimeMillis();

                if (noBlockBelowTime == 0) {
                    noBlockBelowTimeMap.put(playerId, currentTime);
                } else {
                    long timeWithoutBlock = currentTime - noBlockBelowTime;

                    if (timeWithoutBlock >= 6 * 1000) {
                        getLogger().info(player.getName() + " banido por ficar voando.");
                        banPlayer(player);
                        noBlockBelowTimeMap.remove(playerId);
                    }
                }
            } else {
                noBlockBelowTimeMap.remove(playerId);
            }
        } else {
            noBlockBelowTimeMap.remove(playerId);
        }
    }

    private void banPlayer(Player player) {
        Bukkit.getBanList(BanList.Type.IP).addBan(player.getAddress().getAddress().getHostAddress(), "Hack", null, null);
        player.kickPlayer(ChatColor.RED + "Você foi banido por usar hack!");
        new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.getBanList(BanList.Type.IP).pardon(player.getAddress().getAddress().getHostAddress());
            }
        }.runTaskLater(this, 20 * 10); // Desbanir o jogador após 10 segundos
    }

    private void removeNonBannedLogs() {
        long currentTime = System.currentTimeMillis();
        for (UUID playerId : lastCheckedMap.keySet()) {
            long lastCheckedTime = lastCheckedMap.get(playerId);
            if (currentTime - lastCheckedTime >= 30 * 60 * 1000) { // 30 minutos em milissegundos
                hackCountMap.remove(playerId);
                lastCheckedMap.remove(playerId);
            }
        }
    }
}
