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
public class PaintBucketKit extends AbstractKit {
	public PaintBucketKit(JavaPlugin plugin) {
		super(KitType.PAINTBUKKET, (float)0.4, 16, 1.5f, .5f,3, 6,2,18,Sound.ENTITY_CHICKEN_EGG, 2f, 1.75f);
	}

	@Override
	public void prepPlayer(Player player) {
		super.prepPlayer(player);
			player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999, 0, false, false, false));
	}
}
