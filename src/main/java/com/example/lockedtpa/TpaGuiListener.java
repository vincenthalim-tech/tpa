package com.example.lockedtpa;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.UUID;

public class TpaGuiListener implements Listener {

    private final LockedTPAPlugin plugin;

    public TpaGuiListener(LockedTPAPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player target)) {
            return;
        }

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!TpaCommand.GUI_TITLE.equals(title)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        UUID senderId = plugin.getOpenGuiTargets().get(target.getUniqueId());
        if (senderId == null) {
            target.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        if (slot == 11) {
            target.closeInventory();
            plugin.acceptRequestWithLockAndCountdown(target, senderId);
        } else if (slot == 15) {
            target.closeInventory();
            plugin.denyRequest(target, senderId);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player target) {
            String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
            if (TpaCommand.GUI_TITLE.equals(title)) {
                plugin.getOpenGuiTargets().remove(target.getUniqueId());
            }
        }
    }
}
