package com.vnap.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.vnap.dialogue.DialogueCatalog;
import com.vnap.dialogue.Speech;
import com.vnap.entity.VillagerLifeData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * /dialoguetest N plays every take of group N (in catalogue order) on a stage in front of the player: a speaker of
 * the group's voice and, when the title names one, what it talks about. /dialoguetest continuous goes through all.
 */
public final class DialogueTestCommand {
	private static final long SETTLE = 5L;
	private static final long PAUSE = 20L;
	private static final Map<UUID, Run> RUNS = new HashMap<>();

	private static final class Run {
		private int group;
		private int take;
		private final boolean all;
		private long next;
		private final List<Entity> stage = new ArrayList<>();
		private @Nullable LivingEntity speaker;
		private @Nullable Entity listener;

		private Run(int group, boolean all, long next) {
			this.group = group;
			this.all = all;
			this.next = next;
		}
	}

	private DialogueTestCommand() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> event.getDispatcher().register(Commands.literal("dialoguetest")
				.requires(source -> com.vnap.Mc.op(source))
				.then(Commands.literal("continuous").executes(ctx -> start(ctx.getSource(), 0, true)))
				.then(Commands.argument("group", IntegerArgumentType.integer(1, Math.max(1, DialogueCatalog.groups().size())))
						.executes(ctx -> start(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "group") - 1, false)))));
		NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
		NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> {
			RUNS.values().forEach(DialogueTestCommand::strike);
			RUNS.clear();
		});
	}

	private static int start(CommandSourceStack source, int group, boolean all) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Run old = RUNS.remove(player.getUUID());
		if (old != null) strike(old);
		RUNS.put(player.getUUID(), new Run(group, all, com.vnap.Mc.serverLevel(player).getGameTime() + 1L));
		if (all) player.sendSystemMessage(Component.literal("Dialogue test: all " + DialogueCatalog.groups().size() + " groups"));
		return 1;
	}

	private static void tick(MinecraftServer server) {
		RUNS.entrySet().removeIf(entry -> {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Run run = entry.getValue();
			if (player == null) {
				strike(run);
				return true;
			}
			ServerLevel level = com.vnap.Mc.serverLevel(player);
			if (level.getGameTime() < run.next) return false;
			List<DialogueCatalog.DialogueGroup> groups = List.copyOf(DialogueCatalog.groups().values());
			if (run.group >= groups.size()) return finish(player, run, groups.size());
			DialogueCatalog.DialogueGroup group = groups.get(run.group);
			LivingEntity speaker = run.speaker;
			if (speaker == null) {
				if (!stage(level, player, run, group)) {
					note(player, run, groups.size(), group, " (could not set the stage)");
					return advance(player, run, groups.size(), level);
				}
				run.next = level.getGameTime() + SETTLE;
				return false;
			}
			if (run.take >= group.variants().size() || !speaker.isAlive()) {
				strike(run);
				return advance(player, run, groups.size(), level);
			}
			note(player, run, groups.size(), group, "");
			int index = group.variants().get(run.take).index();
			Entity listener = run.listener == null ? player : run.listener;
			run.next = level.getGameTime() + Math.max(1L, Speech.test(level, speaker, group, index, listener)) + PAUSE;
			run.take++;
			return false;
		});
	}

	/** The group is done: the next one when going through all, else the end of the run. True when the run is over. */
	private static boolean advance(ServerPlayer player, Run run, int total, ServerLevel level) {
		if (!run.all) return finish(player, run, total);
		run.group++;
		run.take = 0;
		run.next = level.getGameTime() + PAUSE;
		return false;
	}

	private static boolean finish(ServerPlayer player, Run run, int total) {
		strike(run);
		player.sendSystemMessage(Component.literal(run.all ? "Dialogue test done: " + total + " groups"
				: "Dialogue test done: group " + (run.group + 1)));
		return true;
	}

	private static void note(ServerPlayer player, Run run, int total, DialogueCatalog.DialogueGroup group, String extra) {
		player.sendSystemMessage(Component.literal("[" + (run.group + 1) + "/" + total + " take " + (run.take + 1) + "/"
				+ group.variants().size() + "] " + group.id() + " " + group.speaker() + ": " + group.title() + extra));
	}

	private static boolean stage(ServerLevel level, ServerPlayer player, Run run, DialogueCatalog.DialogueGroup group) {
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		Vec3 ahead = look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
		Vec3 middle = player.position().add(ahead.scale(2.5));
		Vec3 aside = new Vec3(-ahead.z, 0.0, ahead.x).scale(0.8);
		LivingEntity speaker = speaker(level, group);
		if (speaker == null || !place(level, speaker, middle.add(aside), run)) return false;
		run.speaker = speaker;
		Entity subject = subject(level, group.title());
		if (subject != null && place(level, subject, middle.subtract(aside), run)) run.listener = subject;
		return true;
	}

	private static @Nullable LivingEntity speaker(ServerLevel level, DialogueCatalog.DialogueGroup group) {
		return switch (group.speaker()) {
			case "wandering_trader" -> com.vnap.Mc.create(EntityType.WANDERING_TRADER, level);
			case "parrot" -> com.vnap.Mc.create(EntityType.PARROT, level);
			default -> {
				Villager villager = com.vnap.Mc.create(EntityType.VILLAGER, level);
				if (villager != null) dress(villager, group);
				yield villager;
			}
		};
	}

	/** The villager the group is written for: a child, a woman, the mayor, a tradesman at work. */
	private static void dress(Villager villager, DialogueCatalog.DialogueGroup group) {
		String voice = group.speaker();
		if (voice.equals("child") || voice.equals("infant")) villager.setBaby(true);
		VillagerLifeData data = (VillagerLifeData) villager;
		data.vnap$setFemale(voice.equals("woman"));
		data.vnap$setMayor(voice.equals("mayor"));
		data.vnap$family().genderKnown = true;
		String title = group.title();
		String trade = title.endsWith(" at Work") ? title.substring(0, title.indexOf(' ')).toLowerCase(Locale.ROOT)
				: title.contains("Nitwit") ? "nitwit" : title.contains("Trad") || title.contains("Level") ? "farmer" : "none";
		var profession = switch (trade) {
			case "armorer" -> VillagerProfession.ARMORER;
			case "butcher" -> VillagerProfession.BUTCHER;
			case "cartographer" -> VillagerProfession.CARTOGRAPHER;
			case "cleric" -> VillagerProfession.CLERIC;
			case "farmer" -> VillagerProfession.FARMER;
			case "fisherman" -> VillagerProfession.FISHERMAN;
			case "fletcher" -> VillagerProfession.FLETCHER;
			case "leatherworker" -> VillagerProfession.LEATHERWORKER;
			case "librarian" -> VillagerProfession.LIBRARIAN;
			case "mason" -> VillagerProfession.MASON;
			case "shepherd" -> VillagerProfession.SHEPHERD;
			case "toolsmith" -> VillagerProfession.TOOLSMITH;
			case "weaponsmith" -> VillagerProfession.WEAPONSMITH;
			case "nitwit" -> VillagerProfession.NITWIT;
			default -> VillagerProfession.NONE;
		};
		villager.setVillagerData(com.vnap.Mc.withLevel(com.vnap.Mc.withProfession(villager.getVillagerData(), profession),
				title.equals("Reach Master Level") ? 5 : 1));
	}

	/** A mob the title names ("Baby Cow", "Tamed Wolf"), or a second villager for a talk between two. */
	private static @Nullable Entity subject(ServerLevel level, String title) {
		if (title.contains("Two Villagers") || title.contains("Another Villager") || title.contains("Gossip")) {
			return com.vnap.Mc.create(EntityType.VILLAGER, level);
		}
		boolean young = title.startsWith("Baby ");
		String name = title.replaceFirst("^(Baby |Tamed |Angry |Sheared )+", "").toLowerCase(Locale.ROOT).replace(' ', '_');
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.withDefaultNamespace(name)).orElse(null);
		if (type == null || type.equals(EntityType.PLAYER)) return null;
		Entity entity = com.vnap.Mc.create(type, level);
		if (young && entity instanceof Mob mob) mob.setBaby(true);
		return entity;
	}

	private static boolean place(ServerLevel level, Entity entity, Vec3 at, Run run) {
		entity.addTag(Speech.TEST_TAG);
		entity.setSilent(true);
		com.vnap.Mc.invulnerable(entity, true);
		entity.setNoGravity(!(entity instanceof LivingEntity));
		com.vnap.Mc.snap(entity, at.x, at.y, at.z);
		if (entity instanceof Mob mob) {
			mob.setNoAi(true);
			mob.setPersistenceRequired();
		}
		if (!level.addFreshEntity(entity)) return false;
		run.stage.add(entity);
		return true;
	}

	/** Takes the stage down. */
	private static void strike(Run run) {
		LivingEntity speaker = run.speaker;
		if (speaker != null && !speaker.isRemoved()) Speech.hush(speaker);
		run.stage.forEach(entity -> {
			if (!entity.isRemoved()) entity.discard();
		});
		run.stage.clear();
		run.speaker = null;
		run.listener = null;
	}
}
