package me.gorgeousone.paintball.kit;

import org.bukkit.Sound;
import org.bukkit.util.Vector;

public class ShotOptions {
	public Integer bulletCount;
	public Float bulletSpeed;
	public Float bulletSpread;
	public Float bulletDmg;
	public Integer bulletBlockCount;
	public Float bulletMaxDist;
	public Integer bulletPathColor;
	public Sound gunshotSound;
	public Float gunshotPitchLow;
	public Float gunshotPitchHigh;
	public Long fireRate;

	public Vector customFacing;
	// 可选：是否跳过冷却
	public boolean ignoreCooldown = false;

	public boolean ignoreDefaultSound =false;
}
