package me.gorgeousone.paintball.kit;

import me.gorgeousone.paintball.PaintballPlugin;
import me.gorgeousone.paintball.team.PbTeam;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Abstract class that stores basic properties and functions for gun kits.
 */
public abstract class AbstractKit {
	private JavaPlugin plugin;
	protected final KitType kitType;
	protected int bulletDmg;
	protected int bulletCount;
	protected float bulletSpeed;
	protected float bulletSpread;
	protected int bulletBlockCount;
	protected final long fireRate;
	protected final Sound gunshotSound;
	protected final float gunshotPitchHigh;
	protected final float gunshotPitchLow;
	protected final Random rnd = new Random();
	
	private final Map<UUID, Long> shootCooldowns;
	protected AbstractKit(JavaPlugin plugin,
						  KitType kitType,
						  int bulletDmg,
						  int bulletCount,
						  float bulletSpeed,
						  float bulletSpread,
						  int bulletBlockCount,
						  long fireRate,
						  Sound gunshotSound,
						  float gunshotPitchHigh, float gunshotPitchLow)
	{
		this.plugin = plugin;
		this.kitType = kitType;
		this.bulletDmg = bulletDmg;
		this.bulletCount = bulletCount;
		this.bulletSpeed = bulletSpeed;
		this.bulletSpread = bulletSpread;
		this.bulletBlockCount = bulletBlockCount;
		this.fireRate = fireRate;
		this.gunshotSound = gunshotSound;
		this.gunshotPitchHigh = gunshotPitchHigh;
		this.gunshotPitchLow = gunshotPitchLow;
		this.shootCooldowns = new HashMap<>();
	}
	
	public void updateSpecs(int bulletCount, int bulletDmg, float bulletSpeed, float bulletSpread) {
		this.bulletCount = bulletCount;
		this.bulletDmg = bulletDmg;
		this.bulletSpeed = bulletSpeed;
		this.bulletSpread = bulletSpread;
	}
	
	public boolean launchShot(Player player, PbTeam team, Collection<Player> gamePlayers) {
		UUID playerId = player.getUniqueId();
		
		if (shootCooldowns.getOrDefault(playerId, 0L) > System.currentTimeMillis()) {
			return false;
		}
		Vector facing = player.getLocation().getDirection();
		
		for (int i = 0; i < bulletCount; ++i) {
			Projectile bullet = player.launchProjectile(team.getType().projectileType);
			bullet.setShooter(player);
			bullet.setVelocity(createVelocity(facing, bulletSpeed, bulletSpread));
			bullet.setCustomName("" + bulletDmg);


			bullet.getPersistentDataContainer().set(PaintballPlugin.BULLET_TAG, PersistentDataType.INTEGER, bulletBlockCount);
		}
		playGunshotSound(player, gamePlayers, gunshotPitchLow, gunshotPitchHigh);
		
		if (fireRate > 0) {
			shootCooldowns.put(playerId, System.currentTimeMillis() + fireRate * 50);
		}
		return true;
	}
	
	/**
	 * Adds kit specific status effects to the player.
	 */
	public void prepPlayer(Player player) {}
	
	//TODO make own gunshots sound high pitched
	//TODO lower pitch? seems loud
	protected void playGunshotSound(Player player, Collection<Player> coplayers, float pitchLow, float pitchHigh) {
		Location location = player.getEyeLocation();
		
		for (Player other : coplayers) {
			if (other == player) {
				other.playSound(location, gunshotSound, .5f, pitchHigh);
			} else {
				other.playSound(location, gunshotSound, .5f, pitchLow);
			}
		}
	}
	
	public Vector createVelocity(Vector facing, float speed, float spread) {
		Vector velocity = facing.clone();
		velocity.add(new Vector(
				(rnd.nextFloat() - .5) * spread,
				(rnd.nextFloat() - .5) * spread,
				(rnd.nextFloat() - .5) * spread));
		return velocity.multiply(speed);
	}
	
	public void removePlayer(UUID playerId) {
		shootCooldowns.remove(playerId);
	}
}
