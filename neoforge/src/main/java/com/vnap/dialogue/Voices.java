package com.vnap.dialogue;

import com.vnap.Nbt;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Pets;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;

import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Whose voice a group is written for (its {@code speaker} in dialogues.json) and who may say it: a woman answers a
 * men's line with her own group of that title, a mayor keeps his village lines, a child echoes a grown-up.
 */
public final class Voices {
	/** A child's title and the grown-up titles it echoes (a child's group of the same title comes first). */
	private static final String ECHOES = """
			Kid Scared of a Monster < Villager Panics | Zombie | Skeleton | Creeper | Spider | Witch | Enderman | Phantom \
			| Pillager | Ravager | Vindicator | Evoker | Vex | Zoglin | Warden | Drowned | Husk | Stray | Bogged \
			| Zombie Villager | Spawn Hostile Mobs in the Village | Woman Sees a Monster
			Kid Stands Up for a Grown-up < Attacked by the Player | Attacked with a Sword | Attacked with an Axe \
			| Attacked with an Arrow | Attacked with a Hoe | Attacked with a Shovel | Attacked with a Firework \
			| Attacked with a Splash Potion | Attacked with a Gun | Villager Gets Hurt | Attacked Inside Their Home \
			| Woman Is Hit by the Player
			Kid Sees Someone Die < See Another Villager Die | Villager Dies | Kill a Villager's Iron Golem
			Kid Sees a Fire < Set Fire Near a Villager's Home | Village House on Fire | Light a Roof on Fire | Burning
			Kid Hears a Boom < Set Off TNT Near Homes | Explode Near the Village | See TNT | Light TNT | See a Shell Explosion
			Kid in the Rain < Caught in the Rain | Thunderstorm | See Lightning | Lightning Hits Nearby | Woman in the Weather
			Kid in the Snow < Stand on Snow | Wander Somewhere Cold | Snowball | Place Snow
			Kid Watches a Trade < Complete a Trade | Start Trading with Neutral Reputation | Woman Completes a Trade
			Kid Likes the Player < Hero of the Village | High Reputation | Extremely High Reputation | Woman Praises the Hero
			Kid Avoids a Bad Player < Low Reputation | Extremely Low Reputation | Extremely Low Reputation with a Sword \
			| Woman Scolds a Bad Player
			Kid Amazed by the Player < Glide with Elytra | Fly in Creative Mode | Teleport with an Ender Pearl \
			| Mine Diamond Ore | Enchant an Item | Catch a Fish | Use a Totem of Undying | Mount a Horse | Tame an Animal \
			| Woman Chats With the Player
			Kid Loves Animals < Animals Have a Baby | Feed an Animal | Shear a Sheep | Milk a Cow | Cat | Wolf | Tamed Wolf \
			| Horse | Pig | Cow | Chicken | Sheep | Rabbit | Parrot | Frog | Panda
			Kid in a Happy Village < Village Mood Rising | Warm village | Kind village | Woman in a Happy Village
			Kid in a Grumpy Village < Village Mood Falling | Sour village | Harsh village | Woman in a Grumpy Village
			Kid at the Festival < Festoon Hung | Festival Begins | Pavilion Raised | Consecrate the Monument
			Kid at the Mourning < Vigil Lit | Mourning Laid | Pyre Lit
			Kid Hides From the Tocsin < Hide From the Tocsin | Tocsin Awakens | Tocsin Sheds Plates | Tocsin Bell Cracks
			Kid Cheers the Tocsin's Fall < Tocsin Silenced | Tocsin Sleeps
			Kid Watches Building < Workshop begins | House improved | Own house | Begin Building a Monument \
			| Place a Monument Brick
			Kid Sees the Player Die < Player Dies | Player Dies Again | Player Dies in Hardcore | Woman Sees the Player Die
			Kid Says Good Morning < Morning | Wake Up Naturally
			Kid at the Wedding < Wedding Cheers | Woman Wedding Vow
			Kid Bedtime < Woman Calls the Kids
			Kid Answers Mom < Woman Comforts Her Child | Woman Protects Her Child
			Kid at School < Librarian Teaches the Kids | Woman Teaches the Kids | Elder Tells a Story | Elder Woman Tells a Story
			""";
	private static final Map<String, String> CHILD_ECHOES = readEchoes();

	private Voices() {
	}

	private static Map<String, String> readEchoes() {
		Map<String, String> echoes = new HashMap<>();
		for (String line : ECHOES.split("\n", -1)) {
			if (line.isBlank()) continue;
			String[] sides = line.split(" < ", 2);
			for (String grownUp : sides[1].split(" \\| ", -1)) echoes.put(grownUp.strip(), sides[0].strip());
		}
		return Map.copyOf(echoes);
	}

	/** Grown-up title → the child's title that answers it. */
	public static Map<String, String> childEchoes() {
		return CHILD_ECHOES;
	}

	/** The voice a speaker talks in: the {@code speaker} of the groups written for it. */
	public static String of(LivingEntity speaker) {
		if (speaker instanceof Villager villager) {
			if (Infants.infant(villager)) return "infant";
			if (villager.isBaby()) return "child";
			if (Family.female(villager)) return "woman";
			return Mayors.isMayor(villager) ? "mayor" : "villager";
		}
		if (speaker instanceof WanderingTrader) return "wandering_trader";
		if (speaker instanceof Parrot parrot && Nbt.hasUUID(parrot.getPersistentData(), Pets.PET_HEAD)) return "parrot";
		return "";
	}

	static boolean woman(LivingEntity speaker) {
		return speaker instanceof Villager v && !v.isBaby() && Family.female(v);
	}

	/** A man who holds the office: his own mayor's lines for meeting, trading and getting hurt. */
	static boolean mayorMan(Villager villager) {
		return Mayors.isMayor(villager) && !Family.female(villager);
	}

	/** The group for this title in the speaker's voice; a woman or a mayor falls back to the men's group. */
	static DialogueCatalog.@Nullable DialogueGroup forTitle(LivingEntity speaker, String title) {
		DialogueCatalog.DialogueGroup own = DialogueCatalog.byTitle(title, of(speaker));
		if (own != null) return own;
		boolean grownUp = speaker instanceof Villager v && !v.isBaby();
		return grownUp && (woman(speaker) || (speaker instanceof Villager v && Mayors.isMayor(v)))
				? DialogueCatalog.byTitle(title, "villager") : null;
	}

	/** A woman's own words for a men's group: her group of that title, else the hand-picked Family.WOMAN_LINES. */
	static DialogueCatalog.@Nullable DialogueGroup herLine(DialogueCatalog.DialogueGroup mens) {
		DialogueCatalog.DialogueGroup hers = DialogueCatalog.byTitle(mens.title(), "woman");
		if (hers != null) return hers;
		String title = Family.WOMAN_LINES.get(mens.title());
		return title == null ? null : DialogueCatalog.byTitle(title, "woman");
	}

	static boolean writtenFor(LivingEntity speaker, DialogueCatalog.DialogueGroup group) {
		String voice = group.speaker();
		if (speaker instanceof Villager v && !v.isBaby()) {
			if (mayorMan(v) && voice.equals("villager")) return true;
		}
		return voice.equals(of(speaker));
	}

	/** Any grown-up voice: one may take a villager's line when the caller allows it (a trader's "look out!"). */
	static boolean grownUpVoice(LivingEntity speaker) {
		return speaker instanceof WanderingTrader || (speaker instanceof Villager v && !v.isBaby());
	}

	public static boolean canVoice(LivingEntity speaker, String groupId) {
		DialogueCatalog.DialogueGroup group = DialogueCatalog.byId(groupId);
		if (group == null) return false;
		if (woman(speaker) && group.speaker().equals("villager")) return herLine(group) != null;
		return writtenFor(speaker, group);
	}

	/** Lines only a child says (a test speaker for them is made a baby). */
	public static boolean childOnly(String groupId) {
		DialogueCatalog.DialogueGroup group = DialogueCatalog.byId(groupId);
		return group != null && group.speaker().equals("child");
	}

	/** What a child nearby says after a grown-up's line, or null. */
	static DialogueCatalog.@Nullable DialogueGroup echoOf(DialogueCatalog.DialogueGroup grownUp) {
		DialogueCatalog.DialogueGroup own = DialogueCatalog.byTitle(grownUp.title(), "child");
		if (own != null) return own;
		String title = CHILD_ECHOES.get(grownUp.title());
		return title == null ? null : DialogueCatalog.byTitle(title, "child");
	}
}
