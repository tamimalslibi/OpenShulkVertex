package com.openshulk.plugin;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public class OpenShulk extends JavaPlugin {

    private NamespacedKey shulkerIdKey;

    @Override
    public void onEnable() {
        // Key used to stamp a per-item UUID into the shulker box's PersistentDataContainer.
        // This is how we recognize "the exact same physical item" later, rather than
        // trusting a slot index (which is what makes the slot-swap dupe possible).
        this.shulkerIdKey = new NamespacedKey(this, "openshulk_id");

        getServer().getPluginManager().registerEvents(new ShulkerGuiListener(this, shulkerIdKey), this);

        getLogger().info("OpenShulk enabled - anti-dupe protections active.");
    }

    public NamespacedKey getShulkerIdKey() {
        return shulkerIdKey;
    }
}
