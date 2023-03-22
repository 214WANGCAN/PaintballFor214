package me.gorgeousone.superpaintball.arena;

import me.gorgeousone.superpaintball.util.ConfigUtil;
import me.gorgeousone.superpaintball.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PbArenaHandler {

	private final JavaPlugin plugin;
	private final Map<String, PbArena> arenas;
	private final YamlConfiguration backupConfig;

	public PbArenaHandler(JavaPlugin plugin) {
		this.plugin = plugin;
		this.backupConfig = ConfigUtil.loadConfig("arenas", plugin);
		this.arenas = new HashMap<>();
	}
	
	public boolean containsArena(String arenaName) {
		return arenas.containsKey(arenaName);
	}
	
	public PbArena createArena(String name, File schemFile, Location schemPos) {
		if (arenas.containsKey(name)) {
			throw new IllegalArgumentException(String.format("Arena '%s' already exists!", name));
		}
		PbArena arena = new PbArena(name, schemFile, schemPos, this);
		arenas.put(name, arena);
		arena.reset();
		saveArena(arena);
		return arena;
	}
	
	//TODO overthink this weird duplicate code? Is that wrapper needed?
	
	public PbArena createArena(PbArena oldArena, String name, Location schemPos) {
		if (arenas.containsKey(name)) {
			throw new IllegalArgumentException(String.format("Arena '%s' already exists!", name));
		}
		PbArena arena = new PbArena(oldArena, name, schemPos);
		arenas.put(name, arena);
		arena.reset();
		return arena;
	}
	private void registerArena(PbArena arena) {
		if (arenas.containsKey(arena.getName())) {
			throw new IllegalArgumentException(String.format("Arena '%s' already exists!", arena.getName()));
		}
		arenas.put(arena.getName(), arena);
		ConfigurationSection arenasSection = backupConfig.getConfigurationSection("arenas");
		arena.toYml(arenasSection);
		ConfigUtil.saveConfig(backupConfig, "arenas", plugin);
	}
	
	public PbArena getArena(String name) {
		return arenas.getOrDefault(name, null);
	}
	
	public Collection<PbArena> getArenas() {
		return arenas.values();
	}
	
	public void removeArena(String name) {
		if (!arenas.containsKey(name)) {
			throw new IllegalArgumentException(String.format("Arena '%s' does not exist!", name));
		}
		arenas.remove(name);
		ConfigurationSection arenasSection = backupConfig.getConfigurationSection("arenas");
		arenasSection.set(name, null);
		ConfigUtil.saveConfig(backupConfig, "arenas", plugin);
	}
	
	
	
	public void saveArenas() {
		Logger logger = Bukkit.getLogger();
		logger.log(Level.INFO, "  Saving arenas:");
		
		if (!backupConfig.contains("arenas")) {
			backupConfig.createSection("arenas");
		}
		ConfigurationSection arenasSection = backupConfig.getConfigurationSection("arenas");
		arenas.values().forEach(l -> l.toYml(arenasSection));
		ConfigUtil.saveConfig(backupConfig, "arenas", plugin);
		logger.log(Level.INFO, String.format("  Saved %d arenas", arenas.size()));
	}
	
	void saveArena(PbArena arena) {
		if (!backupConfig.contains("arenas")) {
			backupConfig.createSection("arenas");
		}
		ConfigurationSection arenasSection = backupConfig.getConfigurationSection("arenas");
		arena.toYml(arenasSection);
		ConfigUtil.saveConfig(backupConfig, "arenas", plugin);
	}
	
	public void loadArenas(String schemFolder) {
		Logger logger = Bukkit.getLogger();
		logger.log(Level.INFO, "  Loading arenas:");

		ConfigUtil.assertKeyExists(backupConfig, "arenas");
		ConfigurationSection arenaSection = backupConfig.getConfigurationSection("arenas");

		for (String name : arenaSection.getKeys(false)) {
			try {
				PbArena arena = PbArena.fromYml(name, arenaSection, schemFolder, this);
				registerArena(arena);
			} catch (IllegalArgumentException e) {
				logger.log(Level.WARNING, e.getMessage());
			}
		}
		logger.log(Level.INFO, String.format("  Loaded %d arenas", arenas.size()));
	}
}
