package me.gorgeousone.paintball.kit;

import me.gorgeousone.paintball.ConfigSettings;
import me.gorgeousone.paintball.team.PbTeam;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Class for the machine gun kit, which is a full-automatic gun with limited magazine that slowly reloads over time.
 * Accuracy drops with longer use.
 */
public class MachineGunKit extends AbstractKit {
	
	private final Map<UUID, Integer> magazines;
	private final Map<UUID, Long> lastShots;
	private static final int MAGAZINE_SIZE = 50;
	private static final long RELOAD_DELAY = 10L;
	private static final long RELOAD_INTERVAL = 2L;
	private static final int RELOAD_RATE = 2;
	
	public MachineGunKit(JavaPlugin plugin) {
		super(KitType.MACHINE_GUN, 1, 1, 2.25f, .2f,4, 100,2,3, Sound.ENTITY_CHICKEN_EGG, 2f, 1.75f);
		this.magazines = new HashMap<>();
		this.lastShots = new HashMap<>();
		startReloadAnimator(plugin);
	}
	
	@Override
	public boolean launchShot(Player player, PbTeam team, Collection<Player> gamePlayers) {
		UUID playerId = player.getUniqueId();
		int magazine = getMagazine(playerId);

		if(super.isUsingUltimate.getOrDefault(player.getUniqueId(), false)) return launchUltimateShoot(player,team,gamePlayers);


		if (magazine <= 0) {
			return false;
		}
		float maxBulletSpread = bulletSpread;
		float gunHeat = (float) Math.pow(1f - 1f * magazine / MAGAZINE_SIZE, 2);
		bulletSpread *= gunHeat;
		boolean didShoot = super.launchShot(player, team, gamePlayers);
		bulletSpread = maxBulletSpread;
		
		if (!didShoot) {
			return false;
		}
		increaseMagazine(playerId, -1);
		lastShots.put(playerId, System.currentTimeMillis());
		return true;
	}

	private boolean launchUltimateShoot(Player player, PbTeam team, Collection<Player> gamePlayers)
	{
		ShotOptions options = new ShotOptions();
		options.fireRate = 1L;
		options.bulletDmg = 0.5F;
		boolean success = super.launchShot(player,team,gamePlayers,options);
		if(success)
		{
			options.ignoreCooldown = true;
			Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(getClass()), () -> {
				super.launchShot(player,team,gamePlayers,options);
			}, 25L);
		}
		return success;
	}

	@Override
	public boolean useUltimateSkill(Player player, PbTeam team, Collection<Player> gamePlayers) {
		if (!super.useUltimateSkill(player, team, gamePlayers)) return false;

		UUID playerId = player.getUniqueId();
		setUsingUltimate(playerId, true);

		player.spigot().sendMessage(
				net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
				new net.md_5.bungee.api.chat.TextComponent("§a终极技能生效中 §7- §c射速提升!")
		);

		// 5 秒后恢复状态
		Bukkit.getScheduler().runTaskLater(
				JavaPlugin.getProvidingPlugin(getClass()),
				() -> {
					setUsingUltimate(playerId, false);
					super.playGunshotSound(player,gamePlayers,Sound.BLOCK_BEACON_DEACTIVATE,1,1F,1F);
					player.spigot().sendMessage(
							net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
							new net.md_5.bungee.api.chat.TextComponent("§c终极技能释放完毕")
					);
				},
				100L
		);

		return true;
	}

	@Override
	public void removePlayer(UUID playerId) {
		super.removePlayer(playerId);
		magazines.remove(playerId);
		lastShots.remove(playerId);
	}
	
	private int getMagazine(UUID playerId) {
		magazines.putIfAbsent(playerId, MAGAZINE_SIZE);
		return magazines.get(playerId);
	}
	
	private void increaseMagazine(UUID playerId, int bullets) {
		int newMagazine = magazines.get(playerId) + bullets;
		newMagazine = Math.max(0, Math.min(MAGAZINE_SIZE, newMagazine));
		magazines.put(playerId, newMagazine);
		updateMagazineUI(playerId);
	}
	
	private void updateMagazineUI(UUID playerId) {
		Bukkit.getPlayer(playerId).setExp(1f - 1f * getMagazine(playerId) / MAGAZINE_SIZE);
	}
	
	private void startReloadAnimator(JavaPlugin plugin) {
		BukkitRunnable animator = new BukkitRunnable() {
			@Override
			public void run() {
				long reloadStart = System.currentTimeMillis() - RELOAD_DELAY * 50;
				
				for (UUID playerId : magazines.keySet()) {
					long lastShot = lastShots.getOrDefault(playerId, 0L);
					
					if (lastShot > reloadStart) {
						continue;
					}
					increaseMagazine(playerId, RELOAD_RATE);
					updateMagazineUI(playerId);
				}
			}
		};
		animator.runTaskTimer(plugin, 0, RELOAD_INTERVAL);
	}
	
	@Override
	public void prepPlayer(Player player) {
		super.prepPlayer(player);
		if (ConfigSettings.MACHINE_GUN_PLAYER_SPEED > -1) {
			player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999, ConfigSettings.MACHINE_GUN_PLAYER_SPEED, false, false, false));
		}
	}
}
