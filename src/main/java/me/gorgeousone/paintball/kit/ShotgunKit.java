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
import java.util.UUID;

/**
 * Class for the shotgun kit, which is a pump action gun that shoots a cloud of bullets with low range and accuracy.
 */
public class ShotgunKit extends AbstractKit {
	
	private final JavaPlugin plugin;
	
	public ShotgunKit(JavaPlugin plugin) {
		super(KitType.SHOTGUN, 1, 8, 1.25f, .35f, 4, 25, 2,25, Sound.ENTITY_CHICKEN_EGG, 1f, .85f);
		this.plugin = plugin;
	}

	@Override
	public boolean launchShot(Player player, PbTeam team, Collection<Player> gamePlayers)
	{
		if(!super.isUsingUltimate.getOrDefault(player.getUniqueId(), false)) return super.launchShot(player,team,gamePlayers);
		boolean successShot = super.launchShot(player,team,gamePlayers);
		if(!successShot) return successShot;
		ShotOptions options = new ShotOptions();
		options.ignoreCooldown = true;
		options.bulletCount = 4;
		// 延迟 2 tick 发射第 2 发
		Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(getClass()), () -> {
			super.launchShot(player,team,gamePlayers,options);
		}, 3L);

		// 延迟 4 tick 发射第 3 发
		Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(getClass()), () -> {
			super.launchShot(player,team,gamePlayers,options);
		}, 5L);
		return successShot;
	}


	@Override
	protected void playGunshotSound(Player player, Collection<Player> others, float pitchLow, float pitchHigh) {
		super.playGunshotSound(player, others, pitchLow, pitchHigh);
		
		new BukkitRunnable() {
			@Override
			public void run() {
				ShotgunKit.super.playGunshotSound(player, others, pitchLow, pitchHigh + .05f);
			}
		}.runTaskLater(plugin, 1);
	}
	
	@Override
	public void prepPlayer(Player player) {
		super.prepPlayer(player);
		if (ConfigSettings.SHOTGUN_PLAYER_SPEED > -1) {
			player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999, ConfigSettings.SHOTGUN_PLAYER_SPEED, false, false, false));
		}
	}

	@Override
	public boolean useUltimateSkill(Player player, PbTeam team, Collection<Player> gamePlayers) {
		if (!super.useUltimateSkill(player, team, gamePlayers)) return false;

		UUID playerId = player.getUniqueId();
		setUsingUltimate(playerId, true);

		player.spigot().sendMessage(
				net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
				new net.md_5.bungee.api.chat.TextComponent("§a终极技能生效中 §7- §c三连击!")
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
}
