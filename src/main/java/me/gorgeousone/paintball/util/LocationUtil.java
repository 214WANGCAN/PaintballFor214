package me.gorgeousone.paintball.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class LocationUtil {

	private static final BlockFace[] CARDINAL_FACES = {
			BlockFace.NORTH,
			BlockFace.EAST,
			BlockFace.SOUTH,
			BlockFace.WEST};

	public static final String TELEPORT_MARKER = "paintball-teleport";
	private static MetadataValue TELEPORT_META;

	public static void createTpMarker(JavaPlugin plugin) {
		TELEPORT_META = new FixedMetadataValue(plugin, true);
	}

	public static BlockFace yawToFace(float yaw) {
		return CARDINAL_FACES[Math.round(yaw / 90f) & 0x3].getOppositeFace();
	}
	
	public static Vector faceToDirection(BlockFace face) {
		return new Vector(face.getModX(), face.getModY(), face.getModZ());
	}
	
	/**
	 * Returns location centered to the middle of the block and facing rounded to the nearest cardinal direction.
	 */
	public static Location cleanSpawn(Location spawn) {
		Vector direction = faceToDirection(yawToFace(spawn.getYaw()));
		spawn.setDirection(direction);
		spawn.setX(spawn.getBlockX() + .5);
		spawn.setY(spawn.getBlockY());
		spawn.setZ(spawn.getBlockZ() + .5);
		return spawn;
	}
	
	/**
	 * Executes a teleportation 1 tick later to prevent "Player moved too quickly" warnings.
	 */
	public static void tpTick(Player player, Location location, JavaPlugin plugin) {
		new BukkitRunnable() {
			@Override
			public void run() {
				tpMarked(player, location);
			}
		}.runTaskLater(plugin, 1);
	}


	public static void tpMarked(Player player, Location location) {
		player.setMetadata(TELEPORT_MARKER, TELEPORT_META);
		player.teleport(location);
	}
	
	public static String humanBlockPos(Location location) {
		return String.format("x:%d, y:%d, z:%d", location.getBlockX(), location.getBlockY(), location.getBlockZ());
	}
	
	public static int getWorldMinY(World world) {
		try {
			return world.getMinHeight();
		} catch (NoSuchMethodError e) {
			return 0;
		}
	}

	public static Location findMidpoint(Location loc1, Location loc2) {
		if (loc1 == null || loc2 == null) {
			throw new IllegalArgumentException("Locations cannot be null");
		}

		if (!loc1.getWorld().equals(loc2.getWorld())) {
			throw new IllegalArgumentException("Locations must be in the same world");
		}

		World world = loc1.getWorld();
		double xMid = (loc1.getX() + loc2.getX()) / 2;
		double yMid = (loc1.getY() + loc2.getY()) / 2;
		double zMid = (loc1.getZ() + loc2.getZ()) / 2;

		return new Location(world, xMid, yMid, zMid);
	}
}
