package com.openshulk.plugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShulkerGuiListener implements Listener {

    private final OpenShulk plugin;
    private final NamespacedKey idKey;
    private final Map<UUID, ShulkerSession> openSessions = new HashMap<>();

    public ShulkerGuiListener(OpenShulk plugin, NamespacedKey idKey) {
        this.plugin = plugin;
        this.idKey = idKey;
    }

    // ---------------------------------------------------------------------
    // Opening the shulker
    // ---------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("openshulk.use")) {
            return;
        }

        // Don't hijack right-clicks on interactable blocks (chests, doors, etc.)
        // unless the player is sneaking, same convention vanilla uses.
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && event.getClickedBlock().getType().isInteractable()
                && !player.isSneaking()) {
            return;
        }

        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) {
            return;
        }

        ItemStack item = (hand == EquipmentSlot.HAND)
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();

        if (!isShulkerBox(item)) {
            return;
        }

        event.setCancelled(true);

        if (openSessions.containsKey(player.getUniqueId())) {
            player.sendMessage("\u00A7cYou already have a shulker box open.");
            return;
        }

        openShulker(player, item, hand);
    }

    private void openShulker(Player player, ItemStack item, EquipmentSlot hand) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta blockStateMeta)) {
            return;
        }
        if (!(blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return;
        }

        // Stamp a unique id on this exact physical item if it doesn't have one yet.
        // This UUID - not the slot it happens to sit in - is what we trust later.
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        UUID trackedId;
        String existing = pdc.get(idKey, PersistentDataType.STRING);
        if (existing != null) {
            trackedId = UUID.fromString(existing);
        } else {
            trackedId = UUID.randomUUID();
            pdc.set(idKey, PersistentDataType.STRING, trackedId.toString());
            item.setItemMeta(meta);
            writeItemToHand(player, hand, item);
        }

        Inventory gui = Bukkit.createInventory(null, shulkerBox.getInventory().getSize(), guiTitle(item));
        gui.setContents(shulkerBox.getInventory().getContents());

        openSessions.put(player.getUniqueId(), new ShulkerSession(trackedId, hand, gui));
        player.openInventory(gui);
    }

    private String guiTitle(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return "Shulker Box";
    }

    // ---------------------------------------------------------------------
    // Anti-dupe guards while the GUI is open
    // ---------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ShulkerSession session = openSessions.get(player.getUniqueId());
        if (session == null || !event.getView().getTopInventory().equals(session.getGuiInventory())) {
            return;
        }

        // Guard 1: the classic "hover the GUI, press a hotbar number key" swap.
        // This swaps the hovered slot's item with player.getInventory() slot
        // [hotbarButton] WITHOUT clickedInventory ever being the player's own
        // inventory - so a naive "block clicks in the bottom inventory" check
        // misses it entirely. This is almost certainly the exact glitch you
        // described: open shulker, take stuff, then "change the slot of the
        // shulker" via 1-9 while still hovering the GUI.
        if (event.getClick() == ClickType.NUMBER_KEY) {
            ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
            if (isBlockedNested(hotbarItem)) {
                event.setCancelled(true);
                return;
            }
        }

        // Guard 2: the offhand-swap key (F) has the same "silent bottom-inventory
        // mutation" property as the hotbar-number swap above.
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (isBlockedNested(offhand) || isBlockedNested(event.getCurrentItem())) {
                event.setCancelled(true);
                return;
            }
        }

        Inventory clicked = event.getClickedInventory();
        boolean clickedBottom = clicked != null && clicked.equals(event.getView().getBottomInventory());

        // Guard 3: lock the player's own inventory entirely while editing.
        // This is what stops ordinary drag/click slot-swapping of the shulker
        // (or anything else) while its contents are being edited.
        if (clickedBottom) {
            event.setCancelled(true);
            return;
        }

        // Guard 4: no nesting shulker boxes or bundles inside the open shulker,
        // same rule vanilla enforces for real shulker boxes.
        if (clicked != null && clicked.equals(session.getGuiInventory())) {
            if (isBlockedNested(event.getCursor())) {
                event.setCancelled(true);
                player.sendMessage("\u00A7cYou can't put that inside a shulker box.");
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ShulkerSession session = openSessions.get(player.getUniqueId());
        if (session == null || !event.getView().getTopInventory().equals(session.getGuiInventory())) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= topSize) {
                // The drag touches the player's own inventory - block it entirely.
                event.setCancelled(true);
                return;
            }
        }

        if (isBlockedNested(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ShulkerSession session = openSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (matchesTrackedId(dropped, session)) {
            event.setCancelled(true);
            player.sendMessage("\u00A7cClose the shulker box before dropping it.");
        }
    }

    // ---------------------------------------------------------------------
    // Closing: verify identity, THEN write back. This is the final backstop.
    // ---------------------------------------------------------------------

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        ShulkerSession session = openSessions.get(player.getUniqueId());
        if (session == null || !event.getView().getTopInventory().equals(session.getGuiInventory())) {
            return;
        }
        openSessions.remove(player.getUniqueId());
        writeBack(player, session);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        ShulkerSession session = openSessions.remove(player.getUniqueId());
        if (session != null) {
            writeBack(player, session);
        }
    }

    private void writeBack(Player player, ShulkerSession session) {
        ItemStack current = (session.getHand() == EquipmentSlot.HAND)
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();

        if (!isShulkerBox(current) || !matchesTrackedId(current, session)) {
            // The item that's now in that hand isn't the same physical shulker
            // we opened (moved, swapped, dropped and re-picked-up, etc).
            // Refusing to write here is the step that actually kills the dupe -
            // it means we never create a "second copy" of the contents.
            player.sendMessage("\u00A7cThat shulker box was moved while open - changes were not saved.");
            plugin.getLogger().warning(player.getName() + "'s shulker box moved mid-edit; write-back was blocked.");
            return;
        }

        ItemMeta meta = current.getItemMeta();
        if (!(meta instanceof BlockStateMeta blockStateMeta)) {
            return;
        }
        if (!(blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return;
        }

        shulkerBox.getInventory().setContents(session.getGuiInventory().getContents());
        blockStateMeta.setBlockState(shulkerBox);
        current.setItemMeta(blockStateMeta);

        writeItemToHand(player, session.getHand(), current);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private void writeItemToHand(Player player, EquipmentSlot hand, ItemStack item) {
        if (hand == EquipmentSlot.HAND) {
            player.getInventory().setItemInMainHand(item);
        } else {
            player.getInventory().setItemInOffHand(item);
        }
    }

    private boolean isShulkerBox(ItemStack item) {
        return item != null && item.getType() != Material.AIR && Tag.SHULKER_BOXES.isTagged(item.getType());
    }

    private boolean isBlockedNested(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        return isShulkerBox(item) || item.getType().name().endsWith("BUNDLE");
    }

    private boolean matchesTrackedId(ItemStack item, ShulkerSession session) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        String id = meta.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        return id != null && id.equals(session.getTrackedItemId().toString());
    }
}
