package me.gorgeousone.paintball.kit;

import me.gorgeousone.paintball.ConfigSettings;
import me.gorgeousone.paintball.PaintballPlugin;
import me.gorgeousone.paintball.team.PbTeam;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
	protected final KitType kitType;
	protected float bulletDmg;
	protected int bulletCount;
	protected float bulletSpeed;
	protected float bulletSpread;
	protected int bulletBlockCount;
	protected float bulletMaxDist;
	protected int bulletPathColor;
	protected final long fireRate;
	protected final Sound gunshotSound;
	protected final float gunshotPitchHigh;
	protected final float gunshotPitchLow;
	protected final Random rnd = new Random();
	private final Map<UUID, Long> shootCooldowns;
	protected final Map<UUID, Double> ultimateEnergy;
	protected final Map<UUID, Boolean> isUsingUltimate;
	protected AbstractKit(KitType kitType,
                          float bulletDmg,
                          int bulletCount,
                          float bulletSpeed,
                          float bulletSpread,
                          int bulletBlockCount,
                          float bulletMaxDist,
                          int bulletPathColor,
                          long fireRate,
                          Sound gunshotSound,
                          float gunshotPitchHigh, float gunshotPitchLow)
	{
		this.kitType = kitType;
		this.bulletDmg = bulletDmg;
		this.bulletCount = bulletCount;
		this.bulletSpeed = bulletSpeed;
		this.bulletSpread = bulletSpread;
		this.bulletBlockCount = bulletBlockCount;
		this.bulletMaxDist = bulletMaxDist;
		this.bulletPathColor = bulletPathColor;
		this.fireRate = fireRate;
		this.gunshotSound = gunshotSound;
		this.gunshotPitchHigh = gunshotPitchHigh;
		this.gunshotPitchLow = gunshotPitchLow;
        this.ultimateEnergy = new HashMap<>();
        this.shootCooldowns = new HashMap<>();
		this.isUsingUltimate = new HashMap<>();
	}
	
	public void updateSpecs(int bulletCount, float bulletDmg, float bulletSpeed, float bulletSpread, float bulletMaxDist) {
		this.bulletCount = bulletCount;
		this.bulletDmg = bulletDmg;
		this.bulletSpeed = bulletSpeed;
		this.bulletSpread = bulletSpread;
		this.bulletMaxDist = bulletMaxDist;
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

			PersistentDataContainer dataContainer = bullet.getPersistentDataContainer();
			dataContainer.set(PaintballPlugin.BULLET_TAG, PersistentDataType.INTEGER, bulletBlockCount);
			dataContainer.set(PaintballPlugin.BULLET_MAXDIST, PersistentDataType.FLOAT, bulletMaxDist);
			dataContainer.set(PaintballPlugin.BULLET_PATHCOLOR, PersistentDataType.INTEGER, bulletPathColor);
		}
		playGunshotSound(player, gamePlayers, gunshotPitchLow, gunshotPitchHigh);
		
		if (fireRate > 0) {
			shootCooldowns.put(playerId, System.currentTimeMillis() + fireRate * 50);
		}
		return true;
	}

	public boolean launchShot(Player player, PbTeam team, Collection<Player> gamePlayers, ShotOptions options) {
		UUID playerId = player.getUniqueId();

		if (!options.ignoreCooldown && shootCooldowns.getOrDefault(playerId, 0L) > System.currentTimeMillis()) {
			return false;
		}

		Vector facing = options.customFacing != null ? options.customFacing : player.getLocation().getDirection();

		int actualBulletCount = options.bulletCount != null ? options.bulletCount : bulletCount;
		float actualBulletSpeed = options.bulletSpeed != null ? options.bulletSpeed : bulletSpeed;
		float actualBulletSpread = options.bulletSpread != null ? options.bulletSpread : bulletSpread;
		float actualBulletDmg = options.bulletDmg != null ? options.bulletDmg : bulletDmg;
		int actualBlockCount = options.bulletBlockCount != null ? options.bulletBlockCount : bulletBlockCount;
		float actualMaxDist = options.bulletMaxDist != null ? options.bulletMaxDist : bulletMaxDist;
		int actualColor = options.bulletPathColor != null ? options.bulletPathColor : bulletPathColor;
		long actualFireRate = options.fireRate != null ? options.fireRate : fireRate;

		for (int i = 0; i < actualBulletCount; ++i) {
			Projectile bullet = player.launchProjectile(team.getType().projectileType);
			bullet.setShooter(player);
			bullet.setVelocity(createVelocity(facing, actualBulletSpeed, actualBulletSpread));
			bullet.setCustomName("" + actualBulletDmg);

			PersistentDataContainer dataContainer = bullet.getPersistentDataContainer();
			dataContainer.set(PaintballPlugin.BULLET_TAG, PersistentDataType.INTEGER, actualBlockCount);
			dataContainer.set(PaintballPlugin.BULLET_MAXDIST, PersistentDataType.FLOAT, actualMaxDist);
			dataContainer.set(PaintballPlugin.BULLET_PATHCOLOR, PersistentDataType.INTEGER, actualColor);
		}

		float pitchLow = options.gunshotPitchLow != null ? options.gunshotPitchLow : gunshotPitchLow;
		float pitchHigh = options.gunshotPitchHigh != null ? options.gunshotPitchHigh : gunshotPitchHigh;

		if (!options.ignoreDefaultSound) {
			// 使用自定义 sound 播放器（如果 gunshotSound 已设置）
			if (options.gunshotSound != null) {
				playGunshotSound(player, gamePlayers, options.gunshotSound, 1, pitchHigh, pitchLow);
			} else {
				// 保持兼容旧逻辑（调用旧方法）
				playGunshotSound(player, gamePlayers, pitchLow, pitchHigh);
			}
		}


		if (!options.ignoreCooldown && actualFireRate > 0) {
			shootCooldowns.put(playerId, System.currentTimeMillis() + actualFireRate * 50);
		}

		return true;
	}


	public boolean useUltimateSkill(Player player, PbTeam team, Collection<Player> gamePlayers)
	{
		double currentEnergy = ultimateEnergy.getOrDefault(player.getUniqueId(), 0.0);
		if(currentEnergy < 100) return false;
		ultimateEnergy.put(player.getUniqueId(), 0.0);
		playGunshotSound(player,gamePlayers,Sound.BLOCK_BEACON_ACTIVATE,1,0.85F,0.85F);
		return true;
	}

	/**
	 * Adds kit specific status effects to the player.
	 */
	public void prepPlayer(Player player)
	{
		// 发光
		player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 99999, 0, false, false, false));
	}
	
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

	protected void playGunshotSound(Player source, Collection<Player> targets, Sound sound, float volume, float pitchSelf, float pitchOthers) {
		Location location = source.getEyeLocation();

		for (Player target : targets) {
			float pitch = (target == source) ? pitchSelf : pitchOthers;
			target.playSound(location, sound, volume, pitch);
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

	public void addUltimateEnergy(UUID playerId, double amount)
	{
		if(isUsingUltimate.getOrDefault(playerId, false)) return;
		double currentEnergy = ultimateEnergy.getOrDefault(playerId, 0.0);
		currentEnergy = Math.min(currentEnergy + amount, 100); // 最大值为 100
		ultimateEnergy.put(playerId, currentEnergy);

		// 显示到经验条
		Player player = Bukkit.getPlayer(playerId);
		if (player != null && player.isOnline()) {
			float expBarValue = (float) (currentEnergy / 100.0f); // 经验条比例
			player.setExp(expBarValue);

			// 如果充能已满，发送 ActionBar 提示
			if (currentEnergy >= 100) {
				player.spigot().sendMessage(
						net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
						new net.md_5.bungee.api.chat.TextComponent("§e终极技能已充能！§f按 §lF §f释放！")
				);
			}
		}
	}

	public void setUsingUltimate(UUID playerId, boolean isUsing) {
		if (isUsing) {
			isUsingUltimate.put(playerId, true);
		} else {
			// 只有当当前是 true 时，才修改为 false（避免误覆盖）
			if (Boolean.TRUE.equals(isUsingUltimate.get(playerId))) {
				isUsingUltimate.put(playerId, false);
			}
		}
	}

	public void setUltimateEnergy(UUID playerId, double amount)
	{
		ultimateEnergy.put(playerId, 0.0);
	}
	public void removePlayer(UUID playerId) {
		shootCooldowns.remove(playerId);
		ultimateEnergy.remove(playerId);
		isUsingUltimate.remove(playerId);

		Player player = Bukkit.getPlayer(playerId);
		if (player != null && player.isOnline()) {
			player.setExp(0);
		}
	}
}
