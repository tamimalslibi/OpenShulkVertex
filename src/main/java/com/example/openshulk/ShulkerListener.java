package com.example.openshulk;

import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

public class ShulkerListener implements Listener {

    public static class ShulkerInventoryHolder implements InventoryHolder {
        private final ItemStack shulkerItem;
        private final int slot;

        public ShulkerInventoryHolder(ItemStack shulkerItem, int slot) {
            this.shulkerItem = shulkerItem;
            this.slot = slot;
        }

        public ItemStack getShulkerItem() {
            return shulkerItem;
        }

        public int getSlot() {
            return slot;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory openInventory = event.getInventory();
        
        if (openInventory.getHolder() instanceof ShulkerInventoryHolder) {
            ShulkerInventoryHolder holder = (ShulkerInventoryHolder) openInventory.getHolder();
            ItemStack currentItem = event.getCurrentItem();
            ItemStack cursorItem = event.getCursor();

            // 1. Block moving the active shulker box item slot
            if (event.getSlot() == holder.getSlot() && event.getClickedInventory() == event.getWhoClicked().getInventory()) {
                event.setCancelled(true);
                return;
            }

            // 2. Prevent putting another shulker box inside
            if (isShulkerBox(currentItem) || isShulkerBox(cursorItem)) {
                if (event.getClickedInventory() == openInventory || event.isShiftClick()) {
                    event.setCancelled(true);
                    return;
                }
            }

            // 3. Block hotbar number key swap on the shulker slot
            if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() == holder.getSlot()) {
                event.setCancelled(true);
                return;
            }

            // 4. Block offhand swap (F key)
            if (event.getClick() == ClickType.SWAP_OFFHAND) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Inventory topInventory = player.getOpenInventory().getTopInventory();

        if (topInventory.getHolder() instanceof ShulkerInventoryHolder) {
            ItemStack droppedItem = event.getItemDrop().getItemStack();
            ShulkerInventoryHolder holder = (ShulkerInventoryHolder) topInventory.getHolder();

            if (droppedItem.isSimilar(holder.getShulkerItem()) || isShulkerBox(droppedItem)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();

        if (inventory.getHolder() instanceof ShulkerInventoryHolder) {
            ShulkerInventoryHolder holder = (ShulkerInventoryHolder) inventory.getHolder();
            ItemStack shulkerItem = holder.getShulkerItem();

            if (shulkerItem != null && shulkerItem.getItemMeta() instanceof BlockStateMeta) {
                BlockStateMeta meta = (BlockStateMeta) shulkerItem.getItemMeta();
                if (meta.getBlockState() instanceof ShulkerBox) {
                    ShulkerBox shulkerBox = (ShulkerBox) meta.getBlockState();
                    shulkerBox.getInventory().setContents(inventory.getContents());
                    meta.setBlockState(shulkerBox);
                    shulkerItem.setItemMeta(meta);
                }
            }
        }
    }

    private boolean isShulkerBox(ItemStack item) {
        return item != null && item.getType().name().endsWith("SHULKER_BOX");
    }
}
