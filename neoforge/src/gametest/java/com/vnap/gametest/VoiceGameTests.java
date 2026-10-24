package com.vnap.gametest;

import com.vnap.dialogue.Speech;
import com.vnap.dialogue.DialogueCatalog;
import com.vnap.dialogue.Family;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public class VoiceGameTests {
	/** A title typo is silence in game: every title the code plays must exist with the right speaker. */
	@GameTest(template = "platform")
	public static void childAndWomenLinesResolve(GameTestHelper helper) {
		List<String> missing = new ArrayList<>();
		for (Map.Entry<String, String> echo : com.vnap.dialogue.Voices.childEchoes().entrySet()) {
			if (DialogueCatalog.byTitle(echo.getKey(), "villager") == null && DialogueCatalog.byTitle(echo.getKey(), "woman") == null) {
				missing.add("adult " + echo.getKey());
			}
			if (DialogueCatalog.byTitle(echo.getValue(), "child") == null) missing.add("child " + echo.getValue());
		}
		for (String title : List.of("Kid Plays", "Kid Bedtime", "Kid Gets a Sibling")) {
			if (DialogueCatalog.byTitle(title, "child") == null) missing.add("child " + title);
		}
		for (Map.Entry<String, String> line : Family.WOMAN_LINES.entrySet()) {
			if (DialogueCatalog.byTitle(line.getKey(), "villager") == null) missing.add("adult " + line.getKey());
			if (DialogueCatalog.byTitle(line.getValue(), "woman") == null) missing.add("woman " + line.getValue());
		}
		for (String title : List.of("Woman Wedding Vow", "Woman Good Morning Dear", "Woman Calls the Kids", "Woman New Baby",
				"Woman Widowed", "Woman Child Grew Up", "Woman Defends Her Husband", "Woman Protects Her Child",
				"Woman Comforts Her Child", "Woman Thanks for the Flower", "Woman Courting")) {
			if (DialogueCatalog.byTitle(title, "woman") == null) missing.add("woman " + title);
		}
		for (String title : List.of("Woman Thanks for the Bed", "Woman Cooks", "Woman Dinner Is Ready", "Woman Decorates",
				"Woman Invites the Player", "Woman Thanks for the Blanket", "Woman Chats With Husband", "Women Gossip",
				"Women Gossip Reply")) {
			if (DialogueCatalog.byTitle(title, "woman") == null) missing.add("woman " + title);
		}
		for (String title : List.of("Kid Eats Dinner", "Kid Gets a Toy", "Kid Welcomes Dad", "Kid Gets a Room")) {
			if (DialogueCatalog.byTitle(title, "child") == null) missing.add("child " + title);
		}
		for (String title : List.of("Man Wedding Vow", "Man Good Morning Dear", "Man New Baby", "Man Widowed",
				"Man Defends His Wife", "Man Thanks for the Flower", "Man Courting", "Wedding Cheers", "Man Carries a Bed",
				"Man Builds a Bed", "Man Starts a Room", "Man Finishes the Room", "Man Thanks for the Help", "Man Praises Dinner",
				"Man Chats With Wife", "Man Comes Home", "Librarian Teaches the Kids")) {
			if (DialogueCatalog.byTitle(title, "villager") == null) missing.add("man " + title);
		}
		for (String title : List.of("Woman Leaves for a Hamlet", "Woman Pantry Empty", "Woman Fetches Food", "Woman Fills the Pantry",
				"Woman Proud of Her Child", "Woman Mourns", "Woman Visits the Grave", "Woman Thanks the Godparent", "Woman Scolds",
				"Woman Makes Up", "Woman Lights the Candle", "Young Woman Comes of Age")) {
			if (DialogueCatalog.byTitle(title, "woman") == null) missing.add("woman " + title);
		}
		for (String title : List.of("Man Leaves for a Hamlet", "Neighbour Says Goodbye", "Settlers Found a Hamlet", "Man Proud of His Child",
				"Man Proud of His Apprentice", "Elder Tells a Story", "Funeral Words", "Man Mourns", "Man Visits the Grave",
				"Man Asks for a Wedding Gift", "Man Thanks for the Wish", "Man Grumbles", "Man Makes Up", "Young Man Comes of Age")) {
			if (DialogueCatalog.byTitle(title, "villager") == null) missing.add("man " + title);
		}
		for (String title : List.of("Kid Wants a Pet", "Kid Tames a Stray", "Kid Names the Pet", "Kid Pets the Animal", "Kid Says Sit",
				"Kid Says Fetch", "Kid Says Good Dog", "Kid Watches the Fish", "Kid Misses the Pet")) {
			if (DialogueCatalog.byTitle(title, "child") == null) missing.add("child " + title);
		}
		for (String title : List.of("Woman Tames a Stray", "Woman Feeds the Pet", "Woman Pets the Animal", "Woman Misses the Pet")) {
			if (DialogueCatalog.byTitle(title, "woman") == null) missing.add("woman " + title);
		}
		for (String title : List.of("Man Welcomes the Pet", "Man Builds a Kennel", "Man Finishes the Kennel")) {
			if (DialogueCatalog.byTitle(title, "villager") == null) missing.add("man " + title);
		}
		for (String title : List.of("Kid Greets Godparent", "Kid Visits the Neighbour", "Kid Counts", "Kid Found You", "Kid Was Found")) {
			if (DialogueCatalog.byTitle(title, "child") == null) missing.add("child " + title);
		}
		Checks.assertTrue(helper, DialogueCatalog.groups().values().stream().noneMatch(group -> group.speaker().equals("golem")),
				"the Tocsin Golem does not speak");
		Checks.assertTrue(helper, missing.isEmpty(), "dialogue titles without a group: " + missing);
		Checks.assertTrue(helper, com.vnap.dialogue.Voices.childOnly("ecslqo"),
				"the original baby lines are spoken by children");
		helper.succeed();
	}
}
