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
public class SubMachineGunKit extends AbstractKit {
	public SubMachineGunKit(JavaPlugin plugin) {
		super(KitType.SUBMACHINE_GUN, (float)0.1, 1, 1f, .3f,1, 3,Sound.ENTITY_CHICKEN_EGG, 2f, 1.75f);
	}

	@Override
	public void prepPlayer(Player player) {
			player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999, 1, false, false, false));
	}
}
