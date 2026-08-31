package com.example.openshulk;

import org.bukkit.plugin.java.JavaPlugin;

public class OpenShulk extends JavaPlugin {
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new ShulkerListener(), this);
        getLogger().info("OpenShulk with Anti-Dupe protection enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("OpenShulk disabled.");
    }
}
