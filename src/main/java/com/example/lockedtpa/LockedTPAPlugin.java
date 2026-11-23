package com.example.lockedtpa;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LockedTPAPlugin extends JavaPlugin {

    public static class TpaRequest {
        public final UUID senderId;
        public final UUID targetId;
        public final long expireAtMillis;

        public TpaRequest(UUID senderId, UUID targetId, long expireAtMillis) {
            this.senderId = senderId;
            this.targetId = targetId;
            this.expireAtMillis = expireAtMillis;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expireAtMillis;
        }
    }

    // Key: Target UUID -> Value: Request dari Sender
    private final Map<UUID, TpaRequest> incomingRequests = new HashMap<>();

    // Key: Target UUID -> Value: Sender UUID yang sedang dikonfirmasi di GUI
    private final Map<UUID, UUID> openGuiTargets = new HashMap<>();

    @Override
    public void onEnable() {
        TpaCommand commandHandler = new TpaCommand(this);
        if (getCommand("tpa") != null) {
            getCommand("tpa").setExecutor(commandHandler);
            getCommand("tpa").setTabCompleter(commandHandler);
        }
        if (getCommand("tpaccept") != null) {
            getCommand("tpaccept").setExecutor(commandHandler);
            getCommand("tpaccept").setTabCompleter(commandHandler);
        }
        if (getCommand("tpdeny") != null) {
            getCommand("tpdeny").setExecutor(commandHandler);
            getCommand("tpdeny").setTabCompleter(commandHandler);
        }

        getServer().getPluginManager().registerEvents(new TpaGuiListener(this), this);
        getLogger().info("LockedTPA v1.1.0 enabled!");
    }

    public Map<UUID, TpaRequest> getIncomingRequests() {
        return incomingRequests;
    }

    public Map<UUID, UUID> getOpenGuiTargets() {
        return openGuiTargets;
    }

    /**
     * Mengunci lokasi target persis saat di-ACC, lalu menjalankan countdown 5 detik
     * sebelum men-teleport pengirim ke lokasi terkunci tersebut.
     */
    public void acceptRequestWithLockAndCountdown(Player target, UUID senderId) {
        incomingRequests.remove(target.getUniqueId());
        openGuiTargets.remove(target.getUniqueId());

        Player sender = Bukkit.getPlayer(senderId);
        if (sender == null || !sender.isOnline()) {
            target.sendMessage(Component.text("CONFIRM REQUEST: Pengirim sudah offline.", NamedTextColor.RED));
            return;
        }

        // Lock lokasi target tepat pada saat di-ACC
        final Location lockedLocation = target.getLocation().clone();

        target.sendMessage(Component.text(
                "CONFIRM REQUEST: Lokasi dikunci di (" +
                        lockedLocation.getBlockX() + ", " +
                        lockedLocation.getBlockY() + ", " +
                        lockedLocation.getBlockZ() + "). " + sender.getName() +
                        " akan teleport dalam 5 detik...",
                NamedTextColor.GREEN
        ));

        sender.sendMessage(Component.text(
                "CONFIRM REQUEST: " + target.getName() +
                        " menerima request! Lokasi terkunci (" +
                        lockedLocation.getBlockX() + ", " +
                        lockedLocation.getBlockY() + ", " +
                        lockedLocation.getBlockZ() + "). Teleport dalam 5 detik...",
                NamedTextColor.GOLD
        ));

        new BukkitRunnable() {
            int secondsLeft = 5;

            @Override
            public void run() {
                Player currentSender = Bukkit.getPlayer(senderId);
                if (currentSender == null || !currentSender.isOnline()) {
                    cancel();
                    return;
                }

                if (secondsLeft > 0) {
                    currentSender.sendMessage(Component.text(
                            "Teleport dalam " + secondsLeft + " detik...",
                            NamedTextColor.YELLOW
                    ));
                    secondsLeft--;
                    return;
                }

                // Eksekusi teleport ke lokasi yang sudah di-lock saat di-ACC
                currentSender.teleportAsync(lockedLocation).thenAccept(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        currentSender.sendMessage(Component.text(
                                String.format("CONFIRM REQUEST: Teleport berhasil ke lokasi terkunci (%.1f, %.1f, %.1f).",
                                        lockedLocation.getX(), lockedLocation.getY(), lockedLocation.getZ()),
                                NamedTextColor.GREEN
                        ));
                    } else {
                        currentSender.sendMessage(Component.text(
                                "CONFIRM REQUEST: Teleport gagal.",
                                NamedTextColor.RED
                        ));
                    }
                });

                cancel();
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    public void denyRequest(Player target, UUID senderId) {
        incomingRequests.remove(target.getUniqueId());
        openGuiTargets.remove(target.getUniqueId());

        target.sendMessage(Component.text("CONFIRM REQUEST: Permintaan TPA ditolak.", NamedTextColor.YELLOW));

        Player sender = Bukkit.getPlayer(senderId);
        if (sender != null && sender.isOnline()) {
            sender.sendMessage(Component.text(
                    "CONFIRM REQUEST: " + target.getName() + " menolak permintaan TPA.",
                    NamedTextColor.RED
            ));
        }
    }
                          }
