package me.gorgeousone.paintball.team;

import me.gorgeousone.paintball.Message;
import me.gorgeousone.paintball.game.GameState;
import me.gorgeousone.paintball.game.PbGame;
import me.gorgeousone.paintball.kit.KitType;
import me.gorgeousone.paintball.kit.PbKitHandler;
import me.gorgeousone.paintball.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Class to manage the players of a team. It
 * equips team armor on game start,
 * tracks health (and visualizes it on armor),
 * spawns revive armorstands and
 * manages spectating states.
 */
public class PbTeam {
	
	private final TeamType teamType;
	private final JavaPlugin plugin;
	private final PbKitHandler kitHandler;
	private final PbGame game;
	private final ItemStack[] teamArmorSet;
	private final Set<UUID> players;
	private final Set<UUID> alivePlayers;
	private final Map<UUID, Float> playerHealth;
	private float maxHealthPoints;
	
	private final Map<UUID, List<Integer>> uncoloredArmorSlots;
	//key: armorstand, value: player
	private final Map<UUID, UUID> reviveSkellies;
	private final Map<UUID, BukkitRunnable> autoReviveMap;
	private Location reviveSpawn;
	private final Random rng = new Random();

	// Paint count
	private double paintNum;
	
	public PbTeam(TeamType teamType, PbGame game, JavaPlugin plugin, PbKitHandler kitHandler) {
		this.teamType = teamType;
		this.game = game;
		this.plugin = plugin;
		this.kitHandler = kitHandler;
		this.players = new HashSet<>();
		this.alivePlayers = new HashSet<>();
		this.playerHealth = new HashMap<>();
		this.uncoloredArmorSlots = new HashMap<>();
		this.reviveSkellies = new HashMap<>();
		this.autoReviveMap = new HashMap<>();
		this.teamArmorSet = TeamUtil.createColoredArmoSet(teamType.armorColor, ChatColor.WHITE + Message.UI_TEAM + " " + teamType.displayName);

		this.paintNum = 0;
	}
	
	public void startGame(List<Location> spawns, int maxHealthPoints) {
		this.maxHealthPoints = maxHealthPoints;
		int i = 0;
		
		for (UUID playerId : players) {
			Player player = Bukkit.getPlayer(playerId);
			LocationUtil.tpTick(player, spawns.get(i % spawns.size()), plugin);
			alivePlayers.add(playerId);
			healPlayer(player);
			equipPlayer(player);
			++i;
		}
		reviveSpawn = spawns.get(0);
	}
	
	public TeamType getType() {
		return teamType;
	}
	
	public Set<UUID> getPlayers() {
		return new HashSet<>(players);
	}
	
	public int size() {
		return players.size();
	}
	
	public boolean hasPlayer(UUID playerId) {
		return players.contains(playerId);
	}
	
	public Set<UUID> getAlivePlayers() {
		return new HashSet<>(alivePlayers);
	}
	
	public boolean isAlive(UUID playerId) {
		return alivePlayers.contains(playerId);
	}
	
	public void addPlayer(Player player) {
		UUID playerId = player.getUniqueId();
		players.add(playerId);
		Message.TEAM_JOIN.send(player, teamType.displayName);
	}
	
	public void removePlayer(UUID playerId) {
		if (!players.contains(playerId)) {
			throw new IllegalArgumentException("Can't remove player with id: " + playerId + ". They are not in this team.");
		}
		Player player = Bukkit.getPlayer(playerId);
		player.removePotionEffect(PotionEffectType.SPEED);
		setSpectator(player, false);
		
		players.remove(playerId);
		alivePlayers.remove(playerId);
		playerHealth.remove(playerId);
		uncoloredArmorSlots.remove(playerId);
		UUID skellyId = getReviveSkellyId(playerId);

		if(autoReviveMap.containsKey(skellyId))
			autoReviveMap.get(skellyId).cancel();
		autoReviveMap.remove(skellyId);

		if (skellyId != null) {
			Bukkit.getEntity(skellyId).remove();
			reviveSkellies.remove(skellyId);
		}
		if (players.isEmpty()) {
			game.onTeamKill(this);
		}
	}
	
	public void reset() {
		for (UUID playerId : players) {
			Player player = Bukkit.getPlayer(playerId);
			setSpectator(player, false);
			healPlayer(player);
			player.getInventory().setArmorContents(null);
			//TODO find way to let Kits reset their effects
			player.removePotionEffect(PotionEffectType.SPEED);
			player.removePotionEffect(PotionEffectType.GLOWING);
		}
		players.clear();
		playerHealth.clear();
		alivePlayers.clear();
		uncoloredArmorSlots.clear();
		reviveSkellies.keySet().forEach(id -> Bukkit.getEntity(id).remove());
		reviveSkellies.clear();
		for (BukkitRunnable ar : autoReviveMap.values()) {
			ar.cancel();
		}
		autoReviveMap.clear();
		paintNum = 0;
	}

	public void paintBlock(UUID playerId, Block shotBlock, int blockCount, boolean showParticle) {
		game.addUltimateEnergy(playerId, (double) blockCount / 60);
		TeamUtil.paintBlot(game,this,shotBlock, teamType, blockCount, 1,showParticle);
	}
	
	public void damagePlayer(Player target, Player shooter, float bulletDmg) {
		UUID targetId = target.getUniqueId();

		//System.out.println(target.getDisplayName() + bulletDmg);

		if (!alivePlayers.contains(targetId)) {
			return;
		}
		shooter.playSound(shooter.getEyeLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 2f);
		float healthPoints = playerHealth.get(targetId);
		
		if (bulletDmg < healthPoints) {
			float newHealth = healthPoints - bulletDmg;
			playerHealth.put(targetId, newHealth);
			paintArmor(targetId);
			
			if (target.getNoDamageTicks() > 0) {
				target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 1, 1);
				target.setHealth(2 * newHealth);
			} else {
				target.damage(2 * bulletDmg);
			}
			game.addUltimateEnergy(shooter.getUniqueId(),bulletDmg);
		} else {
			knockoutPlayer(target);
			game.broadcastKill(target, shooter);
			game.addUltimateEnergy(shooter.getUniqueId(),10);
		}
	}
	
	public void knockoutPlayer(Player player) {
		UUID playerId = player.getUniqueId();
		alivePlayers.remove(player.getUniqueId());
		
		setSpectator(player, true);
		healPlayer(player);
		player.addPotionEffect(TeamUtil.KNOCKOUT_BLINDNESS);
		
		ArmorStand skelly = TeamUtil.createSkelly(TeamUtil.DEATH_ARMOR_SET, player, teamType, kitHandler.getKitType(playerId));
		reviveSkellies.put(skelly.getUniqueId(), playerId);
		game.updateAliveScores();

		// auto revive after 5 sec
		autoRevive(skelly);

		// 死亡爆炸
		PbTeam killerTeam = game.getDifferentTeam(this);
		TeamUtil.paintBlot(game, killerTeam, player.getLocation().getBlock(), killerTeam.getType(), 200, 5, true);


		if (alivePlayers.isEmpty()) {
			game.allPlayers(p -> {
				p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_DEATH, .5f, 2f);
				p.sendTitle("",this.getType().prefixColor+this.getType().displayName+"§c已被团灭!");
			});
		}
	}

	private void autoRevive(ArmorStand skelly){
		UUID skellyId = skelly.getUniqueId();

		if (!reviveSkellies.containsKey(skelly.getUniqueId())) {
			return;
		}
		UUID playerId = reviveSkellies.get(skellyId);
		Player player = Bukkit.getPlayer(playerId);
		PbTeam t = this;
		BukkitRunnable ar = new BukkitRunnable() {
			int countDown = 10;
			@Override
			public void run() {
				if(countDown <= 0)
				{
					revivePlayerToSpawn(skelly);
                    autoReviveMap.remove(skellyId);
					cancel();
				}

                assert player != null;
                player.sendTitle("§c等待复活","§e请等待 "+countDown);
				countDown -= 1;
				TeamUtil.paintBlot(game,t, reviveSpawn.getBlock(), teamType, 20, 3, true);
				if(game.getState() != GameState.RUNNING){
					autoReviveMap.remove(skellyId);
					cancel();
				}
			}
		};
		ar.runTaskTimer(plugin, 0, 20);
		autoReviveMap.put(skellyId,ar);
	}

	private void paintArmor(UUID playerId) {
		Player player = Bukkit.getPlayer(playerId);
		PlayerInventory inv = player.getInventory();
		ItemStack[] playerAmor = inv.getArmorContents();
		List<Integer> uncoloredSlots = uncoloredArmorSlots.get(playerId);
		float healthPoints = playerHealth.get(playerId);
		int newSlotCount = (int) Math.ceil(4f * healthPoints / maxHealthPoints);
		int oldSlotCount = uncoloredSlots.size();
		
		for (int i = newSlotCount; i < oldSlotCount; ++i) {
			int rndSlot = uncoloredSlots.get(rng.nextInt(uncoloredSlots.size()));
			playerAmor[rndSlot] = TeamUtil.DEATH_ARMOR_SET[rndSlot];
			uncoloredSlots.remove(Integer.valueOf(rndSlot));
		}
		inv.setArmorContents(playerAmor);
	}
	
	public void setSpectator(Player player, boolean isSpectator) {
		player.setCollidable(!isSpectator);
		player.setAllowFlight(isSpectator);
		player.setFlying(isSpectator);
		
		if (isSpectator) {
			LocationUtil.tpMarked(player, player.getLocation().add(0, 1, 0));
			game.hidePlayer(player);
		} else {
			game.showPlayer(player);
		}
	}
	
	public boolean hasReviveSkelly(ArmorStand reviveSkelly) {
		return reviveSkellies.containsKey(reviveSkelly.getUniqueId());
	}
	
	public UUID getReviveSkellyId(UUID playerId) {
		for (UUID skellyId : reviveSkellies.keySet()) {
			if (reviveSkellies.get(skellyId) == playerId) {
				return skellyId;
			}
		}
		return null;
	}
	
	public void revivePlayer(ArmorStand skelly) {
		UUID skellyId = skelly.getUniqueId();
		
		if (!reviveSkellies.containsKey(skelly.getUniqueId())) {
			return;
		}

		if(autoReviveMap.containsKey(skellyId))
			autoReviveMap.get(skellyId).cancel();
		autoReviveMap.remove(skellyId);

		UUID playerId = reviveSkellies.get(skellyId);
		Player player = Bukkit.getPlayer(playerId);
		
		setSpectator(player, false);
		LocationUtil.tpMarked(player, skelly.getLocation());
		skelly.remove();
		
		reviveSkellies.remove(skellyId);
		playerHealth.put(playerId, maxHealthPoints);
		alivePlayers.add(playerId);
		game.updateAliveScores();
	}

	public void revivePlayerToSpawn(ArmorStand skelly) {
		if (game.getState() != GameState.RUNNING) {
			return;
		}
		UUID skellyId = skelly.getUniqueId();

		if (!reviveSkellies.containsKey(skelly.getUniqueId())) {
			return;
		}
		UUID playerId = reviveSkellies.get(skellyId);
		Player player = Bukkit.getPlayer(playerId);

		setSpectator(player, false);
		LocationUtil.tpMarked(player, reviveSpawn);
		skelly.remove();
		// 无限水瓶
		PlayerInventory inv = player.getInventory();
		inv.setItem(1, PbKitHandler.getWaterBombs());

		reviveSkellies.remove(skellyId);
		playerHealth.put(playerId, maxHealthPoints);
		alivePlayers.add(playerId);
		game.updateAliveScores();
	}

	public void healPlayer(Player player) {
		player.setFoodLevel(20);
		player.setMaxHealth(2 * maxHealthPoints);
		player.setHealth(2 * maxHealthPoints);
		player.getInventory().setArmorContents(teamArmorSet);
		
		UUID playerId = player.getUniqueId();
		playerHealth.put(player.getUniqueId(), maxHealthPoints);
		uncoloredArmorSlots.put(playerId, new ArrayList<>(Arrays.asList(0, 1, 2, 3)));
	}
	
	private void equipPlayer(Player player) {
		PlayerInventory inv = player.getInventory();
		inv.clear();
		KitType kitType = kitHandler.getKitType(player.getUniqueId());
		PbKitHandler.getKit(kitType).prepPlayer(player);
		
		inv.setItem(0, kitType.getGun());
		inv.setItem(1, PbKitHandler.getWaterBombs());
		inv.setItem(8, teamArmorSet[2]);
		inv.setArmorContents(teamArmorSet);
	}

	public double getPaintNum() {
		return paintNum;
	}

	public void setPaintNum(double paintNum) {
		this.paintNum = paintNum;
	}

	public void onMove(Player player) {
		TeamUtil.giveBlindnessToPlayer(this,game,player);
	}
}
