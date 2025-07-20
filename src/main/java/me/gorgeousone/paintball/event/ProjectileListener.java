package me.gorgeousone.paintball.event;

import me.gorgeousone.paintball.PaintballPlugin;
import me.gorgeousone.paintball.game.PbGame;
import me.gorgeousone.paintball.game.PbLobby;
import me.gorgeousone.paintball.game.PbLobbyHandler;
import me.gorgeousone.paintball.kit.PbKitHandler;
import me.gorgeousone.paintball.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Listener to handle gun shots and revive potion splashes in games.
 */
public class ProjectileListener implements Listener {
	
	private final JavaPlugin plugin;
	private final PbLobbyHandler lobbyHandler;
	public ProjectileListener(JavaPlugin plugin, PbLobbyHandler lobbyHandler) {
		this.plugin = plugin;
		this.lobbyHandler = lobbyHandler;
	}

	@EventHandler
	public void onProjectileLaunch(ProjectileLaunchEvent event) {
		Projectile projectile = event.getEntity();
		Location launchLoc = event.getLocation();

		Player shooter = (Player) projectile.getShooter();
		UUID playerId = shooter.getUniqueId();
		PbGame game = lobbyHandler.getGame(playerId);

		if (!(projectile.getShooter() instanceof Player)) {
			return;
		}
		if (projectile instanceof ThrownPotion) {
			return;
		}

		new BukkitRunnable() {
			float bulletMaxDist = -1;
			@Override
			public void run() {
				if (projectile.isDead() || !projectile.isValid()) {
					cancel();
					return ;
				}

				if (bulletMaxDist == -1f) {
					bulletMaxDist = getBulletMaxDist(projectile);
				}

				Location loc = projectile.getLocation();
				double dist = loc.distance(launchLoc);

				// 路径涂色
				Block pathBlock = findFirstSolidBlockBelow(loc);
				if(pathBlock!=null)
				{
					game.getTeam(playerId).paintBlock(playerId,pathBlock,getPathColor(projectile),false);
				}

				if (dist > bulletMaxDist && bulletMaxDist != -1f) {
					//System.out.println(dist+" > "+bulletMaxDist);
					projectile.remove();
					game.updateTeamCredit();
					cancel();
				}
			}
		}.runTaskTimer(plugin, 2, 1);
	}

	@EventHandler
	public void onProjectileHit(ProjectileHitEvent event) {
		Projectile projectile = event.getEntity();
		
		if (!(projectile.getShooter() instanceof Player)) {
			return;
		}
		Player shooter = (Player) projectile.getShooter();
		UUID playerId = shooter.getUniqueId();
		PbGame game = lobbyHandler.getGame(playerId);
		float bulletDmg = getBulletDmg(projectile);

		if (game == null || bulletDmg == 0) {
			return;
		}
		if (event.getHitBlock() != null) {
			game.getTeam(playerId).paintBlock(playerId,event.getHitBlock(),getBulletBlockCount(projectile),true);
			game.updateTeamCredit();
			return;
		}
		if (!(event.getHitEntity() instanceof Player)) {
			return;
		}
		Player target = (Player) event.getHitEntity();
		
		if (lobbyHandler.getGame(target.getUniqueId()) == game) {
			game.damagePlayer(target, shooter, bulletDmg);
		}
	}
	
	@EventHandler
	public void onPotionSplash(PotionSplashEvent event) {
		ThrownPotion potion = event.getPotion();
		
		if (!(potion.getShooter() instanceof Player)) {
			return;
		}
		Player player = (Player) potion.getShooter();
		PbGame game = lobbyHandler.getGame(player.getUniqueId());
		
		if (game == null || !PbKitHandler.getWaterBombs().isSimilar(potion.getItem())) {
			return;
		}
		for (Entity entity : getEffectedEntities(potion)) {
			//		for (Entity entity : event.getAffectedEntities()) {
			if (entity instanceof Player) {
				game.healPlayer((Player) entity, player);
			} else if (entity instanceof ArmorStand) {
				game.revivePlayer((ArmorStand) entity, player);
			}
		}
	}
	
	float getBulletDmg(Projectile bullet) {
		String bulletName = bullet.getCustomName();
		
		if (bulletName == null) {
			return 0;
		}
		try {
			//return Integer.parseInt(bulletName);
			return Float.parseFloat(bulletName);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	int getBulletBlockCount(Projectile bullet) {
		PersistentDataContainer dataContainer = bullet.getPersistentDataContainer();

		if (dataContainer.has(PaintballPlugin.BULLET_TAG, PersistentDataType.INTEGER)) {
			Integer bulletCount = dataContainer.get(PaintballPlugin.BULLET_TAG, PersistentDataType.INTEGER);
			return bulletCount != null ? bulletCount : 0;
		}
		return 0;
	}

	float getBulletMaxDist(Projectile bullet) {
		PersistentDataContainer dataContainer = bullet.getPersistentDataContainer();

		if (dataContainer.has(PaintballPlugin.BULLET_MAXDIST, PersistentDataType.FLOAT)) {
			Float bulletMaxDist = dataContainer.get(PaintballPlugin.BULLET_MAXDIST, PersistentDataType.FLOAT);
			return bulletMaxDist != null ? bulletMaxDist : 1;
		}
		return 5;
	}

	int getPathColor(Projectile bullet) {
		PersistentDataContainer dataContainer = bullet.getPersistentDataContainer();

		if (dataContainer.has(PaintballPlugin.BULLET_PATHCOLOR, PersistentDataType.INTEGER)) {
			Integer bulletCount = dataContainer.get(PaintballPlugin.BULLET_TAG, PersistentDataType.INTEGER);
			return bulletCount != null ? bulletCount : 0;
		}
		return 2;
	}

	@EventHandler()
	public void onPlayerTeleport(PlayerTeleportEvent event) {
		Player player = event.getPlayer();

		switch (event.getCause()) {
			case ENDER_PEARL:
				if (lobbyHandler.getLobby(player.getUniqueId()) != null) {
					event.setCancelled(true);
				}
				break;
			case PLUGIN:
				if (player.hasMetadata(LocationUtil.TELEPORT_MARKER)) {
					player.removeMetadata(LocationUtil.TELEPORT_MARKER, plugin);
					break;
				}
				PbLobby lobby = lobbyHandler.getLobby(player.getUniqueId());
				Location from = event.getFrom();
				Location to = event.getTo();

				if (lobby != null && (from.getWorld() != to.getWorld() )) {
					//remove player from game synchronously, Paper teleports can be async
					new BukkitRunnable() {
						@Override
						public void run() {
							lobby.removePlayer(player, false, false);
						}
					}.runTask(plugin);
				}
				break;
		}
	}
	
	/**
	 * Custom method to get all entities effected by a thrown potion.
	 * Because there were some issues with armorstands not being included in the event.getAffectedEntities() method(?)
	 */
	private Collection<Entity> getEffectedEntities(ThrownPotion potion) {
		Location pos = potion.getLocation();
		Collection<Entity> entities = pos.getWorld().getNearbyEntities(pos, 4, 2, 4);
		entities.remove(potion);
		return entities;
	}

	public Block findFirstSolidBlockBelow(Location location) {
		World world = location.getWorld();
		int x = location.getBlockX();
		int y = location.getBlockY();
		int z = location.getBlockZ();

		// 从给定的坐标开始，向下寻找第一个不是空气的方块world.getMinHeight()
		for (int currentY = y; currentY >= y-5; currentY--) {
			Block block = world.getBlockAt(x, currentY, z);
			if (block.getType() != Material.AIR) {
				return block;
			}
		}

		// 如果没有找到，返回null
		return null;
	}
}
