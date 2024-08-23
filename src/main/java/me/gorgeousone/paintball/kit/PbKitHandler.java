package me.gorgeousone.paintball.kit;

import me.gorgeousone.paintball.ConfigSettings;
import me.gorgeousone.paintball.Message;
import me.gorgeousone.paintball.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Class for creating a kit selection UI and storing the selected kits of players.
 */
public class PbKitHandler {
	
	private static final int KITS_START_SLOT = 10;
	private static Map<KitType, AbstractKit> KITS;
	private static ItemStack WATER_BOMBS;
	
	private final Map<UUID, KitType> playerKits;
	
	public PbKitHandler() {
		this.playerKits = new HashMap<>();
	}
	
	public static void createKits(JavaPlugin plugin) {
		KITS = new HashMap<>();
		KITS.put(KitType.RIFLE, new RifleKit());
		KITS.put(KitType.SHOTGUN, new ShotgunKit(plugin));
		KITS.put(KitType.MACHINE_GUN, new MachineGunKit(plugin));
		KITS.put(KitType.SUBMACHINE_GUN, new SubMachineGunKit(plugin));
		KITS.put(KitType.PAINTBUKKET, new PaintBucketKit(plugin));
		KITS.put(KitType.HANDGUN, new HandGunKit(plugin));
		//		KITS.put(KitType.SNIPER, new SniperKit());
	}
	
	public void updateConfigKitVals() {
		getKit(KitType.RIFLE).updateSpecs(1, ConfigSettings.RIFLE_BULLET_DMG, ConfigSettings.RIFLE_BULLET_SPEED, ConfigSettings.RIFLE_BULLET_SPREAD, ConfigSettings.RIFLE_BULLET_MAXDIST);
		getKit(KitType.SHOTGUN).updateSpecs(ConfigSettings.SHOTGUN_BULLET_COUNT, ConfigSettings.SHOTGUN_BULLET_DMG, ConfigSettings.SHOTGUN_BULLET_SPEED, ConfigSettings.SHOTGUN_BULLET_SPREAD, ConfigSettings.SHOTGUN_BULLET_MAXDIST);
		getKit(KitType.MACHINE_GUN).updateSpecs(1, ConfigSettings.MACHINE_GUN_BULLET_DMG, ConfigSettings.MACHINE_GUN_BULLET_SPEED, ConfigSettings.MACHINE_GUN_MAX_BULLET_SPREAD, ConfigSettings.MACHINE_GUN_BULLET_MAXDIST);
		getKit(KitType.SUBMACHINE_GUN).updateSpecs(1, ConfigSettings.SUB_MACHINE_GUN_BULLET_DMG, ConfigSettings.SUB_MACHINE_GUN_BULLET_SPEED, ConfigSettings.SUB_MACHINE_GUN_BULLET_SPREAD, ConfigSettings.SUB_MACHINE_GUN_BULLET_MAXDIST);
		WATER_BOMBS = createWaterBombs();
	}
	
	public static AbstractKit getKit(KitType kitType) {
		return KITS.get(kitType);
	}
	
	public KitType getKitType(UUID playerId) {
		playerKits.putIfAbsent(playerId, KitType.RIFLE);
		return playerKits.get(playerId);
	}
	
	public void setKit(UUID playerId, KitType kitType) {
		removePlayer(playerId);
		playerKits.put(playerId, kitType);
	}
	
	public void removePlayer(UUID playerId) {
		if (!playerKits.containsKey(playerId)) {
			return;
		}
		getKit(playerKits.get(playerId)).removePlayer(playerId);
		playerKits.remove(playerId);
	}
	
	public static ItemStack getWaterBombs() {
		return WATER_BOMBS.clone();
	}
	
	private static ItemStack createWaterBombs() {
		ItemStack waterBombs = new ItemStack(Material.SPLASH_POTION, ConfigSettings.WATER_BOMB_COUNT);
		PotionMeta meta = (PotionMeta) waterBombs.getItemMeta();
		meta.setDisplayName(Message.UI_WATER_BOMB);
		meta.setBasePotionData(new PotionData(PotionType.AWKWARD));
		ItemUtil.setModelData(meta, 1337);
		waterBombs.setItemMeta(meta);
		return waterBombs;
	}
	
	public void openKitSelectUI(Player player) {
		Inventory selector = Bukkit.createInventory(null, 3 * 9, Message.UI_KIT_SELECT);
		int itemSlot = KITS_START_SLOT;
		
		for (KitType kitType : KitType.values()) {
			ItemStack gunItem = kitType.getGun();
			selector.setItem(itemSlot, gunItem);
			++itemSlot;
		}
		highlightKit(selector, null, getKitType(player.getUniqueId()));
		player.openInventory(selector);
	}
	
	public boolean onSelectKit(Player player, Inventory inv, int slot) {
		int kitIndex = slot - KITS_START_SLOT;
		
		if (kitIndex < 0 || kitIndex >= KitType.values().length) {
			return false;
		}
		UUID playerId = player.getUniqueId();
		KitType oldKitType = getKitType(playerId);
		KitType newKitType = KitType.values()[kitIndex];
		player.playSound(player.getEyeLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 2f);
		
		if (newKitType == oldKitType) {
			return false;
		}
		setKit(playerId, newKitType);
		highlightKit(inv, oldKitType, newKitType);
		return true;
	}
	
	private static void highlightKit(Inventory inv, KitType oldKit, KitType newKit) {
		if (oldKit != null) {
			int oldSlot = KITS_START_SLOT + oldKit.ordinal();
			ItemUtil.removeMagicGlow(inv.getItem(oldSlot));
		}
		int newSlot = KITS_START_SLOT + newKit.ordinal();
		ItemUtil.addMagicGlow(inv.getItem(newSlot));
	}
}
