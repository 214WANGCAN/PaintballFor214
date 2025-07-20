package me.gorgeousone.paintball.kit;

import me.gorgeousone.paintball.ConfigSettings;
import me.gorgeousone.paintball.team.PbTeam;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;
import java.util.UUID;

/**
 * Class for the rifle kit, which shoots a single high-range and accurate bullet.
 */
public class RifleKit extends AbstractKit {
	
	public RifleKit() {
		super(KitType.RIFLE, 3F, 1, 1.75f, 0.02f,8, 50, 5,10, Sound.ENTITY_CHICKEN_EGG, 1.35f, 1.15f);
	}
	
	@Override
	public void prepPlayer(Player player) {
		super.prepPlayer(player);
		if (ConfigSettings.RIFLE_PLAYER_SPEED > -1) {
			player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999, ConfigSettings.RIFLE_PLAYER_SPEED, false, false, false));
		}
	}

	@Override
	public boolean launchShot(Player player, PbTeam team, Collection<Player> gamePlayers)
	{
		if(!super.isUsingUltimate.getOrDefault(player.getUniqueId(), false)) return super.launchShot(player,team,gamePlayers);
		ShotOptions options = new ShotOptions();
		options.gunshotSound = Sound.ENTITY_FIREWORK_ROCKET_BLAST;
		options.gunshotPitchHigh = 1.4F;
		options.gunshotPitchLow = 1.4F;
		options.bulletDmg = 5F;
		options.bulletSpeed = 2.5F;
		options.bulletPathColor = 8;

		return super.launchShot(player,team,gamePlayers,options);
	}

	@Override
	public boolean useUltimateSkill(Player player, PbTeam team, Collection<Player> gamePlayers) {
		if (!super.useUltimateSkill(player, team, gamePlayers)) return false;

		UUID playerId = player.getUniqueId();
		setUsingUltimate(playerId, true);

		player.spigot().sendMessage(
				net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
				new net.md_5.bungee.api.chat.TextComponent("§a终极技能生效中 §7- §c伤害x1.75")
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
