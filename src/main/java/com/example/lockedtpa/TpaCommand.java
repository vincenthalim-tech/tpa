package com.example.lockedtpa;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TpaCommand implements CommandExecutor, TabCompleter {

    public static final String GUI_TITLE = "CONFIRM REQUEST";
    private static final long REQUEST_TIMEOUT_MS = 60_000L;
    private final LockedTPAPlugin plugin;

    public TpaCommand(LockedTPAPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Perintah ini hanya bisa dipakai oleh player.", NamedTextColor.RED));
            return true;
        }

        switch (command.getName().toLowerCase()) {
            case "tpa" -> handleTpa(player, args);
            case "tpaccept" -> handleTpAccept(player, args);
            case "tpdeny" -> handleTpDeny(player, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void handleTpa(Player sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text("Gunakan: /tpa <player>", NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(Component.text("Player tidak ditemukan atau sedang offline.", NamedTextColor.RED));
            return;
        }

        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage(Component.text("Kamu tidak bisa mengirim TPA ke diri sendiri.", NamedTextColor.RED));
            return;
        }

        Map<UUID, LockedTPAPlugin.TpaRequest> requests = plugin.getIncomingRequests();
        LockedTPAPlugin.TpaRequest existing = requests.get(target.getUniqueId());
        if (existing != null && !existing.isExpired() && existing.senderId.equals(sender.getUniqueId())) {
            sender.sendMessage(Component.text("CONFIRM REQUEST sudah dikirim ke " + target.getName() + ".", NamedTextColor.YELLOW));
            return;
        }

        requests.put(target.getUniqueId(), new LockedTPAPlugin.TpaRequest(
                sender.getUniqueId(),
                target.getUniqueId(),
                System.currentTimeMillis() + REQUEST_TIMEOUT_MS
        ));

        sender.sendMessage(Component.text("CONFIRM REQUEST dikirim ke " + target.getName() + ".", NamedTextColor.GREEN));

        Component msg = Component.text("CONFIRM REQUEST dari " + sender.getName() + " ", NamedTextColor.GOLD)
                .append(Component.text("[ACCEPT]")
                        .color(NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/tpaccept " + sender.getName())))
                .append(Component.text(" "))
                .append(Component.text("[DENY]")
                        .color(NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/tpdeny " + sender.getName())));

        target.sendMessage(msg);
    }

    private void handleTpAccept(Player target, String[] args) {
        Map<UUID, LockedTPAPlugin.TpaRequest> requests = plugin.getIncomingRequests();
        LockedTPAPlugin.TpaRequest req = requests.get(target.getUniqueId());

        if (req == null || req.isExpired()) {
            requests.remove(target.getUniqueId());
            target.sendMessage(Component.text("CONFIRM REQUEST tidak ditemukan atau sudah expired.", NamedTextColor.RED));
            return;
        }

        Player sender = Bukkit.getPlayer(req.senderId);
        if (args.length >= 1 && (sender == null || !sender.getName().equalsIgnoreCase(args[0]))) {
            target.sendMessage(Component.text("CONFIRM REQUEST dari player tersebut tidak ada.", NamedTextColor.RED));
            return;
        }

        if (sender == null || !sender.isOnline()) {
            requests.remove(target.getUniqueId());
            target.sendMessage(Component.text("CONFIRM REQUEST: Pengirim sudah offline.", NamedTextColor.RED));
            return;
        }

        openConfirmGui(target, sender);
    }

    private void handleTpDeny(Player target, String[] args) {
        Map<UUID, LockedTPAPlugin.TpaRequest> requests = plugin.getIncomingRequests();
        LockedTPAPlugin.TpaRequest req = requests.get(target.getUniqueId());

        if (req == null || req.isExpired()) {
            requests.remove(target.getUniqueId());
            target.sendMessage(Component.text("CONFIRM REQUEST tidak ditemukan.", NamedTextColor.RED));
            return;
        }

        Player sender = Bukkit.getPlayer(req.senderId);
        if (args.length >= 1 && (sender == null || !sender.getName().equalsIgnoreCase(args[0]))) {
            target.sendMessage(Component.text("CONFIRM REQUEST dari player tersebut tidak ada.", NamedTextColor.RED));
            return;
        }

        plugin.denyRequest(target, req.senderId);
    }

    private void openConfirmGui(Player target, Player sender) {
        Inventory gui = Bukkit.createInventory(null, 27, Component.text(GUI_TITLE));

        ItemStack glassPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glassPane.getItemMeta();
        if (glassMeta != null) {
            glassMeta.displayName(Component.text(" "));
            glassPane.setItemMeta(glassMeta);
        }
        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, glassPane);
        }

        Location previewLoc = target.getLocation();

        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.displayName(Component.text("CONFIRM REQUEST", NamedTextColor.GOLD, TextDecoration.BOLD));
            infoMeta.lore(List.of(
                    Component.text("Player: " + sender.getName(), NamedTextColor.WHITE),
                    Component.text("Lock Lokasi: " + previewLoc.getBlockX() + ", " + previewLoc.getBlockY() + ", " + previewLoc.getBlockZ(), NamedTextColor.AQUA),
                    Component.text("Delay: 5 detik setelah di-ACC", NamedTextColor.YELLOW)
            ));
            infoItem.setItemMeta(infoMeta);
        }

        ItemStack acceptItem = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta acceptMeta = acceptItem.getItemMeta();
        if (acceptMeta != null) {
            acceptMeta.displayName(Component.text("CONFIRM REQUEST - ACCEPT", NamedTextColor.GREEN, TextDecoration.BOLD));
            acceptMeta.lore(List.of(
                    Component.text("Klik untuk mengunci lokasi saat ini", NamedTextColor.GRAY),
                    Component.text("dan mulai countdown 5 detik.", NamedTextColor.GRAY)
            ));
            acceptItem.setItemMeta(acceptMeta);
        }

        ItemStack denyItem = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta denyMeta = denyItem.getItemMeta();
        if (denyMeta != null) {
            denyMeta.displayName(Component.text("CONFIRM REQUEST - DENY", NamedTextColor.RED, TextDecoration.BOLD));
            denyMeta.lore(List.of(
                    Component.text("Klik untuk menolak request.", NamedTextColor.GRAY)
            ));
            denyItem.setItemMeta(denyMeta);
        }

        gui.setItem(11, acceptItem);
        gui.setItem(13, infoItem);
        gui.setItem(15, denyItem);

        plugin.getOpenGuiTargets().put(target.getUniqueId(), sender.getUniqueId());
        target.openInventory(gui);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("tpa") && args.length == 1) {
            String prefix = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> !name.equals(sender.getName()))
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
              }
