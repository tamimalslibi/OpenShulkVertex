package com.openshulk.plugin;

import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

/**
 * Represents one player's currently-open shulker box edit.
 *
 * trackedItemId  - the UUID we stamped into the shulker item's PersistentDataContainer.
 *                  This is the anti-dupe anchor: on close we re-check that the item
 *                  still sitting in `hand` carries this exact UUID before writing
 *                  contents back. If it doesn't match, the item was swapped/moved
 *                  and we refuse to write (which is what stops the dupe).
 * hand           - which hand (MAIN_HAND or OFF_HAND) the shulker was held in.
 * guiInventory   - the virtual 27-slot inventory shown to the player.
 */
public class ShulkerSession {

    private final UUID trackedItemId;
    private final EquipmentSlot hand;
    private final Inventory guiInventory;

    public ShulkerSession(UUID trackedItemId, EquipmentSlot hand, Inventory guiInventory) {
        this.trackedItemId = trackedItemId;
        this.hand = hand;
        this.guiInventory = guiInventory;
    }

    public UUID getTrackedItemId() {
        return trackedItemId;
    }

    public EquipmentSlot getHand() {
        return hand;
    }

    public Inventory getGuiInventory() {
        return guiInventory;
    }
}
