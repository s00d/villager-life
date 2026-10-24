package com.vnap.dialogue;

import net.minecraft.world.entity.npc.Villager;

/** The lines a kind of villager greets, idles, trades and gets hurt with: a mayor has his own. */
record Cast(String greets, String idles, String trades, String hurt, String attack) {
	static final Cast VILLAGER = new Cast("xfpjxq", "lvigit", "clbjww", "wyvzhk", "vevdkl");
	static final Cast MAYOR = new Cast("dpwhhs", "xxehbq", "njyapy", "ssbhiv", "ltdnvy");

	static Cast of(Villager villager) {
		return Voices.mayorMan(villager) ? MAYOR : VILLAGER;
	}
}
