package me.gorgeousone.paintball.kit;

import me.gorgeousone.paintball.team.PbTeam;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class BrushKit extends AbstractKit {

    private final Map<UUID, Long> lastClickTimes = new HashMap<>();
    private final Map<UUID, Boolean> isLongPressing = new HashMap<>();
    private final JavaPlugin plugin;

    // 阈值：判断是否是长按
    private static final long PRESS_THRESHOLD = 250; // ms
    private static final long FAST_FIRE_INTERVAL = 50; // ms
    private static final long SLOW_FIRE_COOLDOWN = 500; // ms

    // 每个玩家的上次发射时间
    private final Map<UUID, Long> lastShotMillis = new HashMap<>();

    public BrushKit(JavaPlugin plugin) {
        super(KitType.BRUSH, 1, 1, 1.5f, 0.3f, 4, 8, 2, 3, Sound.ENTITY_CHICKEN_EGG, 2f, 1.75f);
        this.plugin = plugin;
    }

    @Override
    public boolean launchShot(Player player, PbTeam team, Collection<Player> gamePlayers) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        boolean isLongPress = false;
        long lastClick = lastClickTimes.getOrDefault(uuid, 0L);
        long sinceLastClick = now - lastClick;

        // 判断是否为长按
        if (sinceLastClick < PRESS_THRESHOLD) {
            isLongPress = true;
            isLongPressing.put(uuid, true);
        } else {
            isLongPressing.put(uuid, false);
        }

        lastClickTimes.put(uuid, now);

        // 当前 tick 时间（单位 tick）
        long currentTime = System.currentTimeMillis();
        long lastShotTime = lastShotMillis.getOrDefault(uuid, 0L);

        ShotOptions options = new ShotOptions();
        // 判断冷却
        if (isLongPress) {
            if (currentTime - lastShotTime < FAST_FIRE_INTERVAL) {
                return false;
            }
            options.bulletCount = 8;
            options.bulletSpread = 4f;
            options.bulletMaxDist = 1F;
            options.bulletPathColor = 15;
            options.bulletDmg = 0.2F;
            options.ignoreCooldown = true;
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 5, 3, false, false, false));
        } else {
            if (currentTime - lastShotTime < SLOW_FIRE_COOLDOWN) {
                return false;
            }
            options.bulletCount = 6;
            options.bulletSpread = 0.4f;
            options.bulletDmg = 0.6F;
            options.ignoreCooldown = true;
        }


        boolean didShoot = super.launchShot(player, team, gamePlayers,options);
        if (!didShoot) {
            return false;
        }
        lastShotMillis.put(uuid, currentTime);


        return didShoot;
    }

    @Override
    public void removePlayer(UUID playerId) {
        super.removePlayer(playerId);
        lastClickTimes.remove(playerId);
        lastShotMillis.remove(playerId);
        isLongPressing.remove(playerId);
    }
}
