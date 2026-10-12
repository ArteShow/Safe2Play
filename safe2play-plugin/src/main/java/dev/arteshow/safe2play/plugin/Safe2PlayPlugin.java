package dev.arteshow.safe2play.plugin;

import org.bukkit.plugin.java.JavaPlugin;

public final class Safe2PlayPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Safe2Play plugin enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Safe2Play plugin disabled.");
    }
}
