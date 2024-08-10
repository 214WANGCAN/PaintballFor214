package me.gorgeousone.paintball.team;

import me.gorgeousone.paintball.game.PbGame;
import me.gorgeousone.paintball.kit.KitType;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * Utility class to create colored armor sets, revive skeletons and create colored blocks for gunshots.
 */
public class TeamUtil {
	
	public static final ItemStack[] DEATH_ARMOR_SET = createColoredArmoSet(TeamType.DEATH_COLOR, "");
	public static final PotionEffect KNOCKOUT_BLINDNESS = new PotionEffect(PotionEffectType.BLINDNESS, 30, 4);
	
	private static final Random rng = new Random();
	
	public static ItemStack[] createColoredArmoSet(Color color, String name) {
		ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
		ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
		ItemStack legs = new ItemStack(Material.LEATHER_LEGGINGS);
		ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
		
		LeatherArmorMeta meta = (LeatherArmorMeta) helmet.getItemMeta();
		meta.setColor(color);
		meta.setDisplayName(name);
		
		helmet.setItemMeta(meta);
		chest.setItemMeta(meta);
		legs.setItemMeta(meta);
		boots.setItemMeta(meta);
		return new ItemStack[]{boots, legs, chest, helmet};
	}
	
	public static ArmorStand createSkelly(ItemStack[] deathArmorSet,
			Player player,
			TeamType teamType,
			KitType kitType) {
		Location deathLoc = player.getLocation();
		String name = "" +
		              ChatColor.BOLD + ChatColor.MAGIC + "XX" + ChatColor.RESET +
		              ChatColor.BOLD + teamType.prefixColor + player.getName() + ChatColor.RESET +
		              ChatColor.BOLD + ChatColor.MAGIC + "XX";
		
		ArmorStand stand = (ArmorStand) player.getWorld().spawnEntity(deathLoc, EntityType.ARMOR_STAND);
		stand.getEquipment().setArmorContents(deathArmorSet);
		stand.setCustomNameVisible(true);
		stand.setCustomName(name);
		stand.setInvulnerable(true);
		stand.setArms(true);
		stand.setBasePlate(false);
		
		try {
			stand.setItemInHand(kitType.getGun());
		} catch (NoSuchMethodError e) {
			stand.setItemInHand(kitType.getGun());
		}
		return stand;
	}
	
	//TODO make this nicer block patterns :(
	public static void paintBlot(PbGame game, PbTeam team, Block block, TeamType teamType, int blockCount, int range) {
		if (isTerracotta(block)) {
			countCredit(block,team,game);
			paintBlock(block, teamType);

		}
		List<Block> neighbors = getNeighbors(block, range);
		// 溅射
		for (int i = 0; i < blockCount - 1; ++i) {
			if (neighbors.isEmpty()) {
				break;
			}
			int rndIdx = rng.nextInt(neighbors.size());
			Block neighbor = neighbors.get(rndIdx);
			countCredit(neighbor,team,game);
			paintBlock(neighbor, teamType);
			neighbors.remove(rndIdx);
		}
	}
	
	private static void paintBlock(Block block, TeamType teamType) {
		World world = block.getWorld();
		teamType.blockColor.updateBlock(block, false);
		world.playSound(block.getLocation(), Sound.BLOCK_STONE_PLACE, .1f, .8f);
		Location particleLoc = block.getLocation().add(.5, .5, .5);
		int particleCount = 10;
		
		if (teamType.particleExtra != null) {
			world.spawnParticle(teamType.blockParticle, particleLoc, particleCount, .5f, .5f, .5f, 0, teamType.particleExtra);
		} else {
			world.spawnParticle(teamType.blockParticle, particleLoc, particleCount, .5f, .5f, .5f);
		}
	}
	
	private static List<Block> getNeighbors(Block block, int range) {
		List<Block> terracotta = new LinkedList<>();
		
		for (int dz = -range; dz <= range; ++dz) {
			for (int dy = -range; dy <= range; ++dy) {
				for (int dx = -range; dx <= range; ++dx) {
					Block neighbor = block.getRelative(dx, dy, dz);
					
					if (isTerracotta(neighbor)) {
						terracotta.add(neighbor);
					}
				}
			}
		}
		terracotta.remove(block);
		return terracotta;
	}
	
	private static boolean isTerracotta(Block block) {
		String matName = block.getType().name();
		return matName.contains("STAINED_CLAY") || matName.contains("TERRACOTTA");
	}

	public static void countCredit(Block block,PbTeam team, PbGame game) {
		Material material = block.getType();

		if(material == team.getType().blockColor.getType())
			return;

		PbTeam differTeam = game.getDifferentTeam(team);
		if(material == differTeam.getType().blockColor.getType())
		{
			differTeam.setPaintNum(differTeam.getPaintNum() - 1);
		}

		team.setPaintNum(team.getPaintNum()+1);
	}

	public static void giveBlindnessToPlayer(PbTeam team, PbGame game,Player player)
	{
		Material material = player.getLocation().subtract(0, 1, 0).getBlock().getType();
		PbTeam differTeam = game.getDifferentTeam(team);
		if(material == differTeam.getType().blockColor.getType())
		{
			PotionEffect blindness = new PotionEffect(PotionEffectType.SLOW, 2, 3,false,false);
			player.addPotionEffect(blindness);
		}
	}
}
