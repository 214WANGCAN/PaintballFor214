package me.gorgeousone.paintball.game;

import me.gorgeousone.paintball.CommandTrigger;
import me.gorgeousone.paintball.ConfigSettings;
import me.gorgeousone.paintball.GameBoard;
import me.gorgeousone.paintball.Message;
import me.gorgeousone.paintball.arena.PbArena;
import me.gorgeousone.paintball.equipment.Equipment;
import me.gorgeousone.paintball.equipment.IngameEquipment;
import me.gorgeousone.paintball.equipment.SlotClickEvent;
import me.gorgeousone.paintball.kit.KitType;
import me.gorgeousone.paintball.kit.PbKitHandler;
import me.gorgeousone.paintball.team.PbTeam;
import me.gorgeousone.paintball.team.TeamType;
import me.gorgeousone.paintball.util.LocationUtil;
import me.gorgeousone.paintball.util.SoundUtil;
import me.gorgeousone.paintball.util.StringUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static me.gorgeousone.paintball.util.LocationUtil.findMidpoint;

/**
 * Class to run all logic in a paintball game, like
 * running start/end timers,
 * player joining/leaving,
 * triggering game mechanics and
 * broadcasting game events.
 */
public class PbGame {
	private static final Set<Integer> GAME_INTERVALS = Set.of(60, 30, 15, 5 , 4 , 3 ,2 ,1);
	private final JavaPlugin plugin;
	private final PbKitHandler kitHandler;
	private final CommandTrigger commandTrigger;
	private GameState state;
	private final Set<UUID> players;
	private final Map<TeamType, PbTeam> teams;
	private GameBoard gameBoard;
	private final Runnable onGameEnd;
	private Equipment equipment;
	private PbArena playedArena;
	
	private GameStats gameStats;
	private PbTeam winnerTeam;
	private PbTeam loserTeam;
	private PbCountdown gameTimeLeft;


	public PbGame(
			JavaPlugin plugin,
			PbKitHandler kitHandler,
			Runnable onGameEnd,
			CommandTrigger commandTrigger) {
		this.plugin = plugin;
		this.kitHandler = kitHandler;
		this.onGameEnd = onGameEnd;
		this.commandTrigger = commandTrigger;

		this.state = GameState.LOBBYING;
		this.players = new HashSet<>();
		this.teams = new HashMap<>();
		
		for (TeamType teamType : TeamType.values()) {
			teams.put(teamType, new PbTeam(teamType, this, plugin, this.kitHandler));
		}
		this.equipment = new IngameEquipment(this::onShoot, this::onThrowWaterBomb, kitHandler);
		this.gameTimeLeft = new PbCountdown(this::onCountdownTick, this::onCountdownEnd, plugin);
	}
	
	public void updateUi() {
		this.equipment = new IngameEquipment(this::onShoot, this::onThrowWaterBomb, kitHandler);
		createScoreboard();
		updateAliveScores();
	}
	
	public Equipment getEquip() {
		return equipment;
	}
	
	public int size() {
		return players.size();
	}
	
	public boolean hasPlayer(UUID playerId) {
		return players.contains(playerId);
	}
	
	public void joinPlayer(UUID playerId) {
		players.add(playerId);
		String playerName = Bukkit.getOfflinePlayer(playerId).getName();
		allPlayers(p -> Message.LOBBY_PLAYER_JOIN.send(p, playerName));
	}
	
	public void removePlayer(UUID playerId) {
		if (!players.contains(playerId)) {
			return;
		}
		Player player = Bukkit.getPlayer(playerId);

		if (isRunning()) {
			if (!getTeam(playerId).isAlive(playerId)) {
				showPlayer(player);
			}
			getTeam(playerId).removePlayer(playerId);
			updateAliveScores();
		}
		if (gameBoard != null) {
			gameBoard.removePlayer(player);
		}
		players.remove(playerId);
	}
	
	public GameState getState() {
		return state;
	}

	/**
	 * Returns true if the players are currently inside the game's arena
	 */
	public boolean isRunning() {
		return state != GameState.LOBBYING;
	}
	
	public PbTeam getTeam(UUID playerId) {
		for (PbTeam team : teams.values()) {
			if (team.hasPlayer(playerId)) {
				return team;
			}
		}
		return null;
	}
	
	public Collection<PbTeam> getTeams() {
		return teams.values();
	}

	public PbTeam getDifferentTeam(PbTeam inputTeam) {
		for (PbTeam team : getTeams()) {
			if (!team.equals(inputTeam)) {
				return team;
			}
		}
		return null;
	}
	public void start(PbArena arenaToPlay, TeamQueue teamQueue, int maxHealthPoints) {
		if (state != GameState.LOBBYING) {
			throw new IllegalStateException(Message.LOBBY_RUNNING.format());
		}
		teamQueue.assignTeams(players, teams);
		teams.values().forEach(t -> t.startGame(arenaToPlay.getSpawns(t.getType()), maxHealthPoints));
		
		createScoreboard();
		startCountdown();
		gameStats = new GameStats();
		
		allPlayers(p -> {
			Message.MAP_ANNOUNCE.send(p, ChatColor.WHITE + arenaToPlay.getSpacedName() + StringUtil.MSG_COLOR);
			gameStats.addPlayer(p.getUniqueId(), kitHandler.getKitType(p.getUniqueId()));
		});
		playedArena = arenaToPlay;
		state = GameState.COUNTING_DOWN;
	}
	
	private void createScoreboard() {
		gameBoard = new GameBoard(4 * teams.size() + 1);
		gameBoard.setTitle("" + ChatColor.GOLD + ChatColor.BOLD + "颜料大战");
		allPlayers(p -> gameBoard.addPlayer(p));
		int i = 2;
		
		for (TeamType teamType : teams.keySet()) {
			PbTeam team = teams.get(teamType);
			Team boardTeam = gameBoard.createTeam(teamType.name(), team.getType().prefixColor);
			
			boardTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OTHER_TEAMS);
			boardTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
			
			for (UUID playerId : team.getPlayers()) {
				Player player = Bukkit.getPlayer(playerId);
				boardTeam.addEntry(player.getName());
			}
			int teamPercent = (int) (team.getPaintNum() / (double) totalTeamCredit() * 100);
			gameBoard.setLine(i, "§6已上色: "+ team.getType().prefixColor +  teamPercent + "%" + StringUtil.pad(i));
			gameBoard.setLine(i + 1, Message.UI_ALIVE_PLAYERS.format(team.getAlivePlayers().size()) + StringUtil.pad(i));
			gameBoard.setLine(i + 2, teamType.displayName);
			i += 4;
		}
	}
	
	//TODO find nice wrapper class?
	private void startCountdown() {
		allPlayers(p -> p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .5f, 1f));
		BukkitRunnable countdown = new BukkitRunnable() {
			int time = 8 * 10;
			
			@Override
			public void run() {
				if (time == 80) {
					allPlayers(p -> p.sendTitle(Message.UI_TITLE_GUNS[0], Message.UI_TITLE_GUNS[1]));
				} else if (time == 40) {
					allPlayers(p -> p.sendTitle(Message.UI_TITLE_WATER_BOMBS[0], Message.UI_TITLE_WATER_BOMBS[1]));
				}
				time -= 1;
				
				if (time <= 0) {
					setRunning();
					this.cancel();
					return;
				}
				if (time % 10 == 0) {
					allPlayers(p -> p.playSound(p.getLocation(), SoundUtil.RELOAD_SOUND, .5f, 1f));
				}
			}
		};
		countdown.runTaskTimer(plugin, 0, 2);
	}
	
	private void setRunning() {
		allPlayers(p -> p.playSound(p.getLocation(), SoundUtil.GAME_START_SOUND, 1.5f, 2f));
		state = GameState.RUNNING;
		gameTimeLeft.start(ConfigSettings.GAME_TIME);
		for (PbTeam team : teams.values()) {
			if (team.getAlivePlayers().isEmpty()) {
				onTeamKill(team);
				break;
			}
		}
	}
	
	private void onShoot(SlotClickEvent event) {
		Player player = event.getPlayer();
		UUID playerId = player.getUniqueId();
		
		if (state != GameState.RUNNING || !getTeam(playerId).isAlive(playerId)) {
			event.setCancelled(true);
			return;
		}
		KitType kitType = kitHandler.getKitType(playerId);
		List<Player> coplayers = players.stream().map(Bukkit::getPlayer).collect(Collectors.toList());
		boolean didShoot = PbKitHandler.getKit(kitType).launchShot(player, getTeam(playerId), coplayers);
		
		if (didShoot) {
			gameStats.addGunShot(playerId);
		}
	}
	
	private void onThrowWaterBomb(SlotClickEvent event) {
		UUID playerId = event.getPlayer().getUniqueId();
		
		if (state != GameState.RUNNING || !getTeam(playerId).isAlive(playerId)) {
			event.setCancelled(true);
		}
	}
	
	public void damagePlayer(Player target, Player shooter, float bulletDmg) {
		PbTeam team = getTeam(target.getUniqueId());
		UUID shooterId = shooter.getUniqueId();
		
		if (!team.hasPlayer(shooterId) || bulletDmg == 9001) {
			team.damagePlayer(target, shooter, bulletDmg);
			gameStats.addBulletHit(shooterId);
		}
	}
	
	public void healPlayer(Player target, Player healer) {
		if (state != GameState.RUNNING) {
			return;
		}
		PbTeam team = getTeam(healer.getUniqueId());
		
		if (team.hasPlayer(target.getUniqueId())) {
			team.healPlayer(target);
		}
	}
	
	public void revivePlayer(ArmorStand skelly, Player healer) {
		if (state != GameState.RUNNING) {
			return;
		}
		PbTeam team = getTeam(healer.getUniqueId());
		
		if (team.hasReviveSkelly(skelly)) {
			team.revivePlayer(skelly);
			gameStats.addRevive(healer.getUniqueId());
		}
	}
	
	public void broadcastKill(Player target, Player shooter) {
		UUID shooterId = shooter.getUniqueId();
		UUID targetId = target.getUniqueId();
		
		TeamType targetTeam = getTeam(targetId).getType();
		TeamType shooterTeam = getTeam(shooterId).getType();
		allPlayers(p -> Message.PLAYER_PAINT.send(p,
				targetTeam.prefixColor + target.getDisplayName() + ChatColor.WHITE,
				shooterTeam.prefixColor + shooter.getDisplayName() + ChatColor.WHITE));
		
		gameStats.addKill(shooterId, targetId);
	}
	
	public void onTeamKill(PbTeam killedTeam) {
		if (state != GameState.RUNNING) {
			return;
		}
		gameTimeLeft.cancel();

		loserTeam = killedTeam;

		for (PbTeam team : teams.values()) {
			if (team != killedTeam) {
				winnerTeam = team;
				break;
			}
		}
		allPlayers(p -> p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .5f, 1f));
		announceWinners(winnerTeam);
		winnerTeam.getPlayers().forEach(id -> gameStats.setWin(id));
		haveALook();
		scheduleRestart();
	}
	
	private void announceWinners(PbTeam winningTeam) {
		state = GameState.OVER;
		
		if (winningTeam != null) {
			allPlayers(p -> p.sendTitle(Message.UI_TITLE_WINNER.format(winningTeam.getType().displayName + ChatColor.WHITE), ""));
		} else {
			allPlayers(p -> p.sendTitle("It's a draw?", ""));
		}
	}
	
	private void scheduleRestart() {
		BukkitRunnable restartTimer = new BukkitRunnable() {
			@Override
			public void run() {

				gameStats.save(plugin);
				state = GameState.LOBBYING;
				commandTrigger.triggerGameEndCommands();
				commandTrigger.triggerPlayerWinCommands(winnerTeam);
				commandTrigger.triggerPlayerLoseCommands(loserTeam);
				teams.values().forEach(PbTeam::reset);
				allPlayers(p -> {
					showPlayer(p);
					gameBoard.removePlayer(p);
					playedArena.resetSchem();
					onGameEnd.run();
				});
				gameBoard = null;
			}
		};
		restartTimer.runTaskLater(plugin, 8 * 20);
	}
	
	public void updateAliveScores() {
		if (state == GameState.LOBBYING) {
			return;
		}
		int i = 2;
		
		for (TeamType teamType : teams.keySet()) {
			PbTeam team = teams.get(teamType);
			//padding is for creating unique text :(
			gameBoard.setLine(i+1, Message.UI_ALIVE_PLAYERS.format(team.getAlivePlayers().size()) + StringUtil.pad(i));
			i += 4;
		}
	}
	public void updateTeamCredit() {
		if (state == GameState.LOBBYING) {
			return;
		}
		int i = 2;

		for (TeamType teamType : teams.keySet()) {
			PbTeam team = teams.get(teamType);
			int teamPercent = (int) (team.getPaintNum() / (double) totalTeamCredit() * 100);
			gameBoard.setLine(i, "§6已上色: "+ team.getType().prefixColor +  teamPercent + "%" + StringUtil.pad(i));
			i += 4;
		}
	}
	public void hidePlayer(Player player) {
		allPlayers(p -> p.hidePlayer(player));
	}
	
	public void showPlayer(Player player) {
		allPlayers(p -> p.showPlayer(player));
	}
	
	public void reset() {
		teams.values().forEach(PbTeam::reset);
		
		if (gameBoard != null) {
			allPlayers(p -> gameBoard.removePlayer(p));
		}
		players.clear();
	}
	
	public void allPlayers(Consumer<Player> consumer) {
		for (UUID playerId : players) {
			consumer.accept(Bukkit.getPlayer(playerId));
		}
	}
	
	public PbArena getPlayedArena() {
		return playedArena;
	}

	public int totalTeamCredit()
	{
		int count = 0;
		for (TeamType teamType : teams.keySet()) {
			PbTeam team = teams.get(teamType);
			count += team.getPaintNum();
		}
		return count;
	}

	private void onCountdownTick(int secondsLeft) {


		if (GAME_INTERVALS.contains(secondsLeft))
		{
			allPlayers(p -> {
				p.sendMessage("§c游戏剩余时间: "+secondsLeft+"s");
				p.playSound(p.getLocation(), SoundUtil.COUNTDOWN_SOUND, .5f, 1f);
			});
		}
	}

	private void onCountdownEnd()
	{
		winnerTeam = findTeamWithMostPaints();
		for (PbTeam team : teams.values()) {
			if (team != winnerTeam) {
				loserTeam = team;
				break;
			}
		}

		allPlayers(p -> {
			p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .5f, 1f);
		});
		countingBlock();
		haveALook();
		scheduleRestart();
	}
	public void countingBlock()
	{
		int t1 = (int) (teams.get(TeamType.ICE).getPaintNum() / (double) totalTeamCredit() * 100);
		int t2 = (int) (teams.get(TeamType.EMBER).getPaintNum() / (double) totalTeamCredit() * 100);

		new BukkitRunnable() {

			int t1Count = 0;
			int t2Count = 0;
			@Override
			public void run() {
				// 在此处编写你的动画逻辑
				if(t1Count < t1)
					t1Count++;
				if(t2Count < t2)
					t2Count++;

				allPlayers(p -> {
					p.playSound(p.getLocation(), Sound.ENTITY_CHICKEN_EGG, .5f, 1f);
					// 硬编码
					p.sendTitle("§e本场游戏的胜者是?", "§b"+t1Count+"% §f- §c"+t2Count+"%", 0, 2, 0);
				});

				if (t1Count >= t1 && t2Count >= t2) {
					this.cancel();
					allPlayers(p -> {
						p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, .5f, 2f);
						p.sendTitle(Message.UI_TITLE_WINNER.format(winnerTeam.getType().displayName + ChatColor.WHITE),"§b"+t1Count+"% §f- §c"+t2Count+"%");
					});

					winnerTeam.getPlayers().forEach(id -> gameStats.setWin(id));
				}
			}
		}.runTaskTimer(plugin, 0, 1);
	}



	public void haveALook()
	{
		state = GameState.OVER;
		Location loc = playedArena.getMidSpawnLocation();
		allPlayers(p ->{
			p.teleport(loc);
			hidePlayer(p);
		});
	}
	public PbTeam findTeamWithMostPaints() {
		// 使用 Java Streams API 找到涂色块最多的团队
		Optional<PbTeam> teamWithMostPaints = teams.values().stream()
				.max(Comparator.comparingInt(PbTeam::getPaintNum));

		// 返回找到的团队，或者可以根据需要处理 Optional
		return teamWithMostPaints.orElse(null);
	}
}
