package me.gorgeousone.paintball.kit;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Class for the machine gun kit, which is a full-automatic gun with limited magazine that slowly reloads over time.
 * Accuracy drops with longer use.
 */
public class HandGunKit extends AbstractKit {
	public HandGunKit(JavaPlugin plugin) {
		super(KitType.HANDGUN, (float)1, 2, 1.3f, .15f,5, 50,2,10,Sound.ENTITY_CHICKEN_EGG, 2f, 1.75f);
	}

	@Override
	public void prepPlayer(Player player) {
			super.prepPlayer(player);
			player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999, 1, false, false, false));
	}
}
