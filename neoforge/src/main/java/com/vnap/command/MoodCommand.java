package com.vnap.command;

import com.vnap.village.town.TownRules;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.vnap.dialogue.Family;
import com.vnap.dialogue.FamilyState;
import com.vnap.dialogue.VillagerHome;
import com.vnap.mood.MoodConfig;
import com.vnap.mood.MoodState;
import com.vnap.mood.ScriptKind;
import com.vnap.mood.VillageMoodApi;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.home.HomeProjects;
import com.vnap.village.home.HouseProject;
import com.vnap.village.script.VillageScriptDirector;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.vnap.entity.VillageEntities;
import com.vnap.entity.golem.TocsinGolem;
import org.jspecify.annotations.Nullable;

public final class MoodCommand {
    private MoodCommand() {
    }

    public static void register() {
        Bots.register();
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, (RegisterCommandsEvent event) -> registerCommands(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStoppingEvent.class, (net.neoforged.neoforge.event.server.ServerStoppingEvent event) -> LAST_SEEN.clear());
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("vnap")
                .requires(source -> MoodConfig.debugCommands() && com.vnap.Mc.op(source))
                .then(Commands.literal("mood")
                        .then(Commands.literal("get").executes(ctx -> get(ctx.getSource())))
                        .then(Commands.literal("why").executes(ctx -> why(ctx.getSource())))
                        .then(Commands.literal("set")
                                .then(Commands.argument("mood", FloatArgumentType.floatArg(-100, 100))
                                        .then(Commands.argument("standing", FloatArgumentType.floatArg(-1000, 1000))
                                                .executes(ctx -> set(ctx.getSource(),
                                                        FloatArgumentType.getFloat(ctx, "mood"),
                                                        FloatArgumentType.getFloat(ctx, "standing"))))))
                        .then(Commands.literal("build")
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .executes(ctx -> build(ctx.getSource(), StringArgumentType.getString(ctx, "kind")))))
                        .then(Commands.literal("summon").executes(ctx -> summon(ctx.getSource())))
                        .then(Commands.literal("abort").executes(ctx -> abort(ctx.getSource())))
                        .then(Commands.literal("boss")
                                .then(Commands.literal("kill").executes(ctx -> killBoss(ctx.getSource())))
                                .then(Commands.literal("spawn").executes(ctx -> spawnBoss(ctx.getSource())))))
                .then(Commands.literal("family")
                        .then(Commands.literal("info").executes(ctx -> family(ctx.getSource(), "info")))
                        .then(Commands.literal("court").executes(ctx -> family(ctx.getSource(), "court")))
                        .then(Commands.literal("wedding").executes(ctx -> family(ctx.getSource(), "wedding")))
                        .then(Commands.literal("marry").executes(ctx -> family(ctx.getSource(), "marry")))
                        .then(Commands.literal("birth").executes(ctx -> family(ctx.getSource(), "birth")))
                        .then(Commands.literal("room").executes(ctx -> family(ctx.getSource(), "room"))
                                .then(Commands.literal("hand").executes(ctx -> family(ctx.getSource(), "room_hand"))))
                        .then(Commands.literal("female").executes(ctx -> family(ctx.getSource(), "female")))
                        .then(Commands.literal("male").executes(ctx -> family(ctx.getSource(), "male")))
                        .then(Commands.literal("elder").executes(ctx -> family(ctx.getSource(), "elder")))
                        .then(Commands.literal("passaway").executes(ctx -> family(ctx.getSource(), "passaway")))
                        .then(Commands.literal("grow").executes(ctx -> family(ctx.getSource(), "grow")))
                        .then(Commands.literal("quarrel").executes(ctx -> family(ctx.getSource(), "quarrel")))
                        .then(Commands.literal("founders").executes(ctx -> family(ctx.getSource(), "founders")))
                        .then(Commands.literal("infant").executes(ctx -> infant(ctx.getSource(), true))
                                .then(Commands.literal("girl").executes(ctx -> infant(ctx.getSource(), true)))
                                .then(Commands.literal("boy").executes(ctx -> infant(ctx.getSource(), false)))))
                .then(Commands.literal("town").executes(ctx -> towns(ctx.getSource()))
                        .then(Commands.literal("tour").executes(ctx -> tour(ctx.getSource()))))
                .then(Commands.literal("day").executes(ctx -> {
                    ServerLevel level = ctx.getSource().getLevel();
                    // the village day against the world's clocks: sleeping the night away moves the time of day, not game time
                    ctx.getSource().sendSuccess(() -> Component.literal("village day " + Family.day(level) + ", calendar day "
                            + com.vnap.Mc.dayTime(level) / 24000L + ", time of day " + Family.timeOfDay(level) + ", game day " + level.getGameTime() / 24000L), false);
                    return 1;
                }))
                .then(Commands.literal("story").executes(ctx -> story(ctx.getSource(), null))
                        .then(Commands.literal("lost_child").executes(ctx -> story(ctx.getSource(), com.vnap.village.town.TownStory.Kind.LOST_CHILD)))
                        .then(Commands.literal("storm").executes(ctx -> story(ctx.getSource(), com.vnap.village.town.TownStory.Kind.STORM)))
                        .then(Commands.literal("feast").executes(ctx -> story(ctx.getSource(), com.vnap.village.town.TownStory.Kind.FEAST)))
                        .then(Commands.literal("sick").executes(ctx -> story(ctx.getSource(), com.vnap.village.town.TownStory.Kind.SICK)))
                        .then(Commands.literal("dispute").executes(ctx -> story(ctx.getSource(), com.vnap.village.town.TownStory.Kind.DISPUTE))))
                .then(Commands.literal("roads").executes(ctx -> roads(ctx.getSource(), "info"))
                        .then(Commands.literal("plan").executes(ctx -> roads(ctx.getSource(), "plan")))
                        .then(Commands.literal("build").executes(ctx -> roads(ctx.getSource(), "build")))
                        .then(Commands.literal("square").executes(ctx -> roads(ctx.getSource(), "square")))
                        .then(Commands.literal("yard").executes(ctx -> roads(ctx.getSource(), "yard")))
                        .then(Commands.literal("comfort").executes(ctx -> roads(ctx.getSource(), "comfort")))
                        .then(Commands.literal("restyle").executes(ctx -> roads(ctx.getSource(), "restyle")))
                        .then(Commands.literal("signs").executes(ctx -> roads(ctx.getSource(), "signs")))
                        .then(Commands.literal("greens").executes(ctx -> roads(ctx.getSource(), "greens")))
                        .then(Commands.literal("hollows").executes(ctx -> roads(ctx.getSource(), "hollows")))
                        .then(Commands.literal("pits").executes(ctx -> roads(ctx.getSource(), "pits")))
                        .then(Commands.literal("debris").executes(ctx -> roads(ctx.getSource(), "debris")))
                        .then(Commands.literal("cemetery").executes(ctx -> roads(ctx.getSource(), "cemetery")))
                        .then(Commands.literal("bell").executes(ctx -> roads(ctx.getSource(), "bell")))
                        .then(Commands.literal("lots").executes(ctx -> roads(ctx.getSource(), "lots"))
                                .then(Commands.literal("reset").executes(ctx -> roads(ctx.getSource(), "lots_reset"))))
                        .then(Commands.literal("wall").executes(ctx -> roads(ctx.getSource(), "wall")))
                        .then(Commands.literal("chapel").executes(ctx -> roads(ctx.getSource(), "plot:CHAPEL")))
                        .then(Commands.literal("bakehouse").executes(ctx -> roads(ctx.getSource(), "plot:BAKEHOUSE")))
                        .then(Commands.literal("tavern").executes(ctx -> roads(ctx.getSource(), "plot:TAVERN")))
                        .then(Commands.literal("granary").executes(ctx -> roads(ctx.getSource(), "plot:GRANARY")))
                        .then(Commands.literal("school").executes(ctx -> roads(ctx.getSource(), "plot:SCHOOL")))
                        .then(Commands.literal("market").executes(ctx -> roads(ctx.getSource(), "plot:MARKET"))))
                .then(Commands.literal("errand").executes(ctx -> {
                    ServerLevel level = ctx.getSource().getLevel();
                    BlockPos at = BlockPos.containing(ctx.getSource().getPosition());
                    net.minecraft.world.entity.npc.Villager villager = level.getNearestEntity(net.minecraft.world.entity.npc.Villager.class,
                            net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat().ignoreLineOfSight(), null, at.getX(), at.getY(), at.getZ(),
                            new net.minecraft.world.phys.AABB(at).inflate(16));
                    com.vnap.village.home.Mayors.Office office = com.vnap.village.home.Towns.of(level, at);
                    if (villager == null || office == null) return 0;
                    Family.state(villager).wants.add(new com.vnap.dialogue.FamilyState.Want("minecraft:candle", 2, Family.day(level) - 1));
                    com.vnap.village.town.Roads roads = com.vnap.village.town.Roads.get(level.getServer());
                    roads.plan(level, roads.of(level.dimension(), office.bell), Family.day(level), 0);
                    ctx.getSource().sendSuccess(() -> Component.literal(com.vnap.village.home.Towns.bellOf(villager) + " wants candles; errands: "
                            + com.vnap.village.town.Errands.of(level, office.bell).size()), false);
                    return 1;
                }))
                .then(Commands.literal("placements").executes(ctx -> {
                    var jobs = com.vnap.village.home.Placements.get(ctx.getSource().getServer()).jobs();
                    String list = jobs.stream().map(job -> job.pos().toShortString() + " " + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(job.state().getBlock()).getPath()
                            + " day " + job.day()).collect(java.util.stream.Collectors.joining(" | "));
                    ctx.getSource().sendSuccess(() -> Component.literal(jobs.size() + " to set down by hand: " + list), false);
                    return jobs.size();
                }))
                .then(Commands.literal("hamlet")
                        .then(Commands.literal("depart").executes(ctx -> hamlet(ctx.getSource(), true)))
                        .then(Commands.literal("found").executes(ctx -> hamlet(ctx.getSource(), false))))
                .then(Commands.literal("pet")
                        .then(Commands.literal("info").executes(ctx -> pet(ctx.getSource(), "info")))
                        .then(Commands.literal("stray").executes(ctx -> pet(ctx.getSource(), "stray"))
                                .then(Commands.literal("dog").executes(ctx -> pet(ctx.getSource(), "stray:DOG")))
                                .then(Commands.literal("cat").executes(ctx -> pet(ctx.getSource(), "stray:CAT")))
                                .then(Commands.literal("parrot").executes(ctx -> pet(ctx.getSource(), "stray:PARROT"))))
                        .then(Commands.literal("forget").executes(ctx -> pet(ctx.getSource(), "forget")))
                        .then(Commands.literal("adopt").executes(ctx -> pet(ctx.getSource(), "adopt")))
                        .then(Commands.literal("feed").executes(ctx -> pet(ctx.getSource(), "feed")))
                        .then(Commands.literal("pat").executes(ctx -> pet(ctx.getSource(), "pat")))
                        .then(Commands.literal("train").executes(ctx -> pet(ctx.getSource(), "train")))
                        .then(Commands.literal("kennel").executes(ctx -> pet(ctx.getSource(), "kennel"))))
                .then(Commands.literal("mayor")
                        .then(Commands.literal("info").executes(ctx -> mayor(ctx.getSource(), "info")))
                        .then(Commands.literal("elect").executes(ctx -> mayor(ctx.getSource(), "elect")))
                        .then(Commands.literal("resign").executes(ctx -> mayor(ctx.getSource(), "resign")))
                        .then(Commands.literal("hall").executes(ctx -> mayor(ctx.getSource(), "hall"))))
                .then(Commands.literal("shop")
                        .then(Commands.literal("info").executes(ctx -> ShopCommands.run(ctx.getSource(), "info", 0)))
                        .then(Commands.literal("choose").executes(ctx -> ShopCommands.run(ctx.getSource(), "choose", 0)))
                        .then(Commands.literal("stall").executes(ctx -> ShopCommands.run(ctx.getSource(), "stall", 0)))
                        .then(Commands.literal("caravan").executes(ctx -> ShopCommands.run(ctx.getSource(), "caravan", 0)))
                        .then(Commands.literal("wants").executes(ctx -> ShopCommands.run(ctx.getSource(), "wants", 0)))
                        .then(Commands.literal("prices").executes(ctx -> ShopCommands.prices(ctx.getSource())))
                        .then(Commands.literal("coins").then(Commands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 1_000_000))
                                .executes(ctx -> ShopCommands.run(ctx.getSource(), "coins",
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount"))))))
                .then(Commands.literal("schem")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> schem(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("kids")
                        .then(Commands.literal("hide").executes(ctx -> hide(ctx.getSource()))))
                .then(Commands.literal("save").executes(ctx -> save(ctx.getSource())))
                // a LAN game of dev clients (offline names, tools/dev/client2.sh) lets them in: no Microsoft session check
                .then(Commands.literal("offline").executes(ctx -> {
                    ctx.getSource().getServer().setUsesAuthentication(false);
                    ctx.getSource().sendSuccess(() -> Component.literal("this server no longer checks accounts (offline mode)"), false);
                    return 1;
                }))
                .then(Commands.literal("bot")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.literal("remove").executes(ctx -> Bots.remove(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))
                                .then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.Vec3Argument.vec3())
                                        .executes(ctx -> Bots.place(ctx.getSource(), StringArgumentType.getString(ctx, "name"),
                                                net.minecraft.commands.arguments.coordinates.Vec3Argument.getVec3(ctx, "pos"))))))
                .then(Commands.literal("houses").executes(ctx -> houses(ctx.getSource())))
                .then(Commands.literal("site").executes(ctx -> BuildCommands.site(ctx.getSource())))
                .then(Commands.literal("worldhouses").executes(ctx -> BuildCommands.worldHouses(ctx.getSource(), 32))
                        .then(Commands.argument("r", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 256))
                                .executes(ctx -> BuildCommands.worldHouses(ctx.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "r")))))
                .then(Commands.literal("worldhouse")
                        .then(Commands.argument("template", StringArgumentType.word())
                                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        java.util.stream.Stream.concat(java.util.stream.Stream.of("all"), com.vnap.village.home.HouseLevels.WORLD_HOUSES.stream()), builder))
                                .executes(ctx -> BuildCommands.worldHouse(ctx.getSource(), StringArgumentType.getString(ctx, "template"), "l2", false))
                                .then(Commands.argument("level", StringArgumentType.word())
                                        .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("l1", "l2", "l3"), builder))
                                        .executes(ctx -> BuildCommands.worldHouse(ctx.getSource(), StringArgumentType.getString(ctx, "template"), StringArgumentType.getString(ctx, "level"), false))
                                        .then(Commands.literal("cut").executes(ctx -> BuildCommands.worldHouse(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "template"), StringArgumentType.getString(ctx, "level"), true))))))
                .then(Commands.literal("home").executes(ctx -> home(ctx.getSource(), ""))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> home(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("path").then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> path(ctx.getSource(), StringArgumentType.getString(ctx, "name"), null))
                        .then(Commands.argument("to", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(ctx -> path(ctx.getSource(), StringArgumentType.getString(ctx, "name"),
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos(ctx, "to"))))))
                .then(Commands.literal("spot").executes(ctx -> LookCommands.spot(ctx.getSource())))
                .then(Commands.literal("layer")
                        .executes(ctx -> LookCommands.layer(ctx.getSource(), 0, 8))
                        .then(Commands.argument("dy", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-16, 16))
                                .executes(ctx -> LookCommands.layer(ctx.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "dy"), 8))
                                .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 24))
                                        .executes(ctx -> LookCommands.layer(ctx.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "dy"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius"))))))
                .then(Commands.literal("mobs")
                        .executes(ctx -> LookCommands.mobs(ctx.getSource(), 64))
                        .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(8, 160))
                                .executes(ctx -> LookCommands.mobs(ctx.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius")))))
                .then(Commands.literal("interact")
                        .executes(ctx -> interact(ctx.getSource(), ""))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> interact(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("check")
                        .executes(ctx -> check(ctx.getSource(), 64))
                        .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(8, 160))
                                .executes(ctx -> check(ctx.getSource(),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius")))))
                .then(com.vnap.spire.SpireCommand.node())
                .then(Commands.literal("housing")
                        .executes(ctx -> housing(ctx.getSource())))
                .then(Commands.literal("who")
                        .executes(ctx -> who(ctx.getSource(), 24))
                        .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 128))
                                .executes(ctx -> who(ctx.getSource(),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius")))))
                // a scene: nobody talks but whom "/vnap say !Title" asks (on), or everyone again (off)
                .then(Commands.literal("stage").then(Commands.argument("on", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                        .executes(ctx -> {
                            com.vnap.dialogue.Speech.stageOnly = com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "on");
                            ctx.getSource().sendSuccess(() -> Component.literal("stage only " + com.vnap.dialogue.Speech.stageOnly), false);
                            return 1;
                        })))
                .then(Commands.literal("say")
                        .then(Commands.argument("title", StringArgumentType.greedyString())
                                .executes(ctx -> say(ctx.getSource(), StringArgumentType.getString(ctx, "title")))))
                .then(Commands.literal("house")
                        .executes(ctx -> house(ctx.getSource(), false))
                        .then(Commands.literal("start").executes(ctx -> house(ctx.getSource(), true)))
                        .then(Commands.literal("now").executes(ctx -> houseNow(ctx.getSource())))
                        .then(Commands.literal("finish").executes(ctx -> houseFinish(ctx.getSource())))));
    }

    /** The roads of the town around the player: cells, lamps, stretches to lay; plan now; lay everything now. */
    private static int roads(CommandSourceStack source, String action) {
        ServerLevel level = source.getLevel();
        BlockPos at = BlockPos.containing(source.getPosition());
        com.vnap.village.home.Mayors.Office office = com.vnap.village.home.Towns.of(level, at);
        if (office == null) {
            source.sendFailure(Component.literal("No town here"));
            return 0;
        }
        com.vnap.village.town.Roads roads = com.vnap.village.town.Roads.get(level.getServer());
        com.vnap.village.town.Roads.Network network = roads.of(level.dimension(), office.bell);
        String done = switch (action) {
            case "plan" -> "planned " + roads.plan(level, network, Family.day(level), 64) + " roads; ";
            case "build" -> "laid " + roads.buildNow(level, network) + " blocks; ";
            case "wall" -> com.vnap.village.town.Wall.lay(level, roads, network) ? "wall laid out; " : "no wall: " + com.vnap.village.town.Wall.lastRefusal + "; ";
            case "lots_reset" -> {
                com.vnap.village.town.TownPlan.replan(level, roads, network);
                yield com.vnap.village.town.TownPlan.describe(level, network) + "; ";
            }
            case "lots" -> {
                com.vnap.village.town.TownPlan.planNow(level, roads, network);
                yield com.vnap.village.town.TownPlan.describe(level, network) + "; ";
            }
            case "yard" -> com.vnap.village.town.Yards.debugNear(level, roads, network, net.minecraft.core.BlockPos.containing(source.getPosition())) + "; ";
            case "restyle" -> com.vnap.village.town.TownPlots.restyle(level, roads, network) ? "a town building restyled; " : "no town building to restyle; ";
            case "comfort" -> com.vnap.village.town.Comfort.debugNear(level, roads, network, net.minecraft.core.BlockPos.containing(source.getPosition())) + "; ";
            case "signs" -> com.vnap.village.town.Signage.debugNear(level, roads, network, net.minecraft.core.BlockPos.containing(source.getPosition())) + "; ";
            case "greens" -> com.vnap.village.town.Greenery.debug(level, roads, network) + "; ";
            case "hollows" -> com.vnap.village.town.Lighting.hollows(level, network) + "; ";
            case "pits" -> com.vnap.village.town.Upkeep.pits(level, roads, network) + " blocks of earth ordered into the pits; ";
            case "debris" -> com.vnap.village.town.Upkeep.debris(level, roads, network) + " blocks of debris ordered off the roads, "
                    + com.vnap.village.town.Upkeep.litter(level, network) + " things lying on them picked up; ";
            case "cemetery" -> com.vnap.village.town.Cemetery.plan(level, roads, network) ? "churchyard at " + network.cemetery + "; " : "no room for a churchyard; ";
            case "bell" -> {
                // debug: the town's bell hung where the player stands, the town moved to it (BellMove)
                level.setBlockAndUpdate(at, net.minecraft.world.level.block.Blocks.BELL.defaultBlockState());
                com.vnap.village.town.BellMove.move(level, roads, network, at);
                yield "bell hung at " + at.toShortString() + "; ";
            }
            // the last square once more (debug): laid out again over what stands
            case "square" -> com.vnap.village.town.Plaza.plan(level, roads, network, network.plaza >= 3 ? withSquare(network, 2) : network.plaza + 1)
                    ? "square " + network.plaza + " planned; " : "no square to plan; ";
            default -> action.startsWith("plot:") ? plot(level, roads, network, com.vnap.village.town.TownPlots.Kind.valueOf(action.substring(5))) : "";
        };
        int left = 0;
        for (com.vnap.village.town.Roads.Segment segment : network.segments) left += segment.job().remaining();
        int blocksLeft = left;
        source.sendSuccess(() -> Component.literal(done + "roads of " + network.bell.toShortString() + ": " + network.cells.size()
                + " cells, " + network.lamps.size() + " lamps, square " + network.plaza + ", wall " + network.wallCells.size() + " columns, ditch " + network.moat.size() + ", crossbows " + network.turrets.size() + ", tier " + network.tier + ", " + network.segments.size()
                + " stretches to lay (" + blocksLeft + " blocks: " + network.segments.stream().map(s -> s.label + " " + s.job().remaining() + "@" + s.origin.toShortString()
                        + (s.pausedDay == Long.MIN_VALUE ? "" : " paused " + s.pausedDay)).collect(java.util.stream.Collectors.joining(", ")) + "); plots " + network.plots.entrySet().stream().map(e -> e.getKey() + "@" + e.getValue().origin.toShortString() + " " + e.getValue().rotation)
                .collect(java.util.stream.Collectors.joining(", ")) + "; lamps at "
                + network.lamps.stream().limit(6).map(BlockPos::toShortString).collect(java.util.stream.Collectors.joining(" / "))), false);
        return 1;
    }

    private static int withSquare(com.vnap.village.town.Roads.Network network, int was) {
        network.plaza = was;
        return was + 1;
    }

    private static String plot(ServerLevel level, com.vnap.village.town.Roads roads, com.vnap.village.town.Roads.Network network,
                               com.vnap.village.town.TownPlots.Kind kind) {
        String name = kind.name().toLowerCase(java.util.Locale.ROOT);
        if (com.vnap.village.town.TownPlots.lay(level, roads, network, kind)) return name + " laid out; ";
        com.vnap.village.town.TownPlots.Plot plot = com.vnap.village.town.TownPlots.plot(network, kind);
        return plot != null ? name + " at " + plot.origin.toShortString() + (com.vnap.village.town.TownPlots.built(network, kind) ? ", built; " : ", building; ")
                : "no room for the " + name + " (" + com.vnap.village.town.TownPlots.lastRefusal + "); ";
    }

    /** The town's land as surveyed (TownSurvey): squares buildable / seen / its share, and what it holds. */
    private static String land(ServerLevel level, BlockPos bell) {
        com.vnap.village.town.Roads.Network network = com.vnap.village.town.Roads.get(level.getServer()).near(level.dimension(), bell);
        if (network == null) return ", land not surveyed";
        return ", land " + network.goodLand.size() + " buildable of " + (network.goodLand.size() + network.badLand.size()) + " seen, holds "
                + (network.capacity < 0 ? "?" : Integer.toString(network.capacity));
    }

    /** Every town (office) of this dimension: its bell, people (register / counted), reach, mayor. */
    private static int story(CommandSourceStack source, com.vnap.village.town.TownStory.@org.jspecify.annotations.Nullable Kind kind) {
        String text = com.vnap.village.town.TownStory.describe(source.getLevel(), net.minecraft.core.BlockPos.containing(source.getPosition()), kind);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(text), false);
        return 1;
    }

    /**
     * /vnap town tour: the places of the nearest town a visitor goes round, one line each ("vnaptour kind x y z"), for
     * the dev bot's tour (client/dev/TourTask) — in a world of one's own or on a server alike.
     */
    private static int tour(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos at = BlockPos.containing(source.getPosition());
        com.vnap.village.town.Roads.Network network = com.vnap.village.town.Roads.get(level.getServer()).near(level.dimension(), at);
        if (network == null) {
            source.sendFailure(Component.literal("vnaptour none"));
            return 0;
        }
        java.util.List<String> lines = new java.util.ArrayList<>();
        java.util.function.BiConsumer<String, BlockPos> add = (kind, pos) -> lines.add("vnaptour " + kind + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        add.accept("bell", network.bell);
        BlockPos board = com.vnap.village.town.NoticeBoard.board(level, network);
        if (board != null) add.accept("board", board);
        com.vnap.village.market.Shops.Shop shop = com.vnap.village.market.Shops.near(level, network.bell);
        if (shop != null && shop.counter != null) add.accept("counter", shop.counter);
        for (BlockPos door : com.vnap.village.town.Roads.nodes(level, network)) add.accept("door", door);
        if (network.cemetery != null) add.accept("cemetery", network.cemetery);
        lines.add("vnaptour end " + lines.size());
        // straight to the player who asked (not command feedback: that gamerule is the whole server's, and a tour
        // turning it on filled every other player's chat)
        net.minecraft.server.level.ServerPlayer player = source.getPlayer();
        for (String line : lines) {
            if (player != null) player.sendSystemMessage(Component.literal(line));
            else source.sendSuccess(() -> Component.literal(line), false);
        }
        return lines.size();
    }

    private static int towns(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        StringBuilder text = new StringBuilder();
        for (com.vnap.village.home.Mayors.Office office : com.vnap.village.home.Mayors.get(level.getServer()).offices()) {
            if (!office.dimension.equals(level.dimension())) continue;
            text.append(text.isEmpty() ? "" : " | ").append(office.bell.toShortString()).append(": ")
                    .append(office.residents.size()).append(" registered, ")
                    .append(com.vnap.village.home.Towns.population(level, office.bell)).append("/").append(com.vnap.village.home.Towns.limit(level, office.bell))
                    .append(", reach ").append(Math.round(com.vnap.village.home.Towns.reach(level, office.bell)))
                    .append(land(level, office.bell))
                    .append(", ").append(com.vnap.village.town.TownLoading.describe(level, office.bell))
                    .append(office.mayor == null ? ", no mayor" : ", mayor " + office.name)
                    .append(level.getBlockState(office.bell).is(net.minecraft.world.level.block.Blocks.BELL) ? "" : " (no bell block)");
        }
        String out = text.isEmpty() ? "no towns" : text.toString();
        source.sendSuccess(() -> Component.literal(out), false);
        return 1;
    }

    /** A cradle two blocks ahead of the player, its foot end towards them, with a newborn in it: to look at the models. */
    private static int infant(CommandSourceStack source, boolean female) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        net.minecraft.core.Direction ahead = player.getDirection();
        ServerLevel level = source.getLevel();
        net.minecraft.core.BlockPos cell = player.blockPosition().relative(ahead, 2);
        // down to the floor: a flying spectator's cell is in the air, and the cradle stood on a flower pot there
        while (cell.getY() > level.getMinBuildHeight() && level.getBlockState(cell.below()).canBeReplaced()) cell = cell.below();
        // on free floor only: set over a night stand it left the candle standing on the cradle
        if (!level.getBlockState(cell).canBeReplaced() || !level.getBlockState(cell.above()).isAir()
                || !level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), net.minecraft.core.Direction.UP)) {
            source.sendFailure(Component.literal("no free floor two blocks ahead"));
            return 0;
        }
        Villager baby = com.vnap.dialogue.Infants.spawn(source.getLevel(), cell, ahead.getOpposite(), female);
        // named as a born one is: its coos read "Villager:" under it otherwise
        if (baby != null && !baby.hasCustomName()) baby.setCustomName(Component.literal(Family.givenName(baby)));
        String at = cell.toShortString();
        source.sendSuccess(() -> Component.literal(baby == null ? "no cradle" : "infant at " + at), false);
        return baby == null ? 0 : 1;
    }

    /**
     * Family debugging on the villagers nearest to the player: {@code info} on the nearest one; {@code court},
     * {@code wedding} and {@code marry} on the nearest man and woman; {@code birth} on the nearest married couple.
     */
    private static int family(CommandSourceStack source, String action) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        ServerLevel level = source.getLevel();
        long day = Family.day(level);
        java.util.List<Villager> near = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(16.0),
                villager -> villager.isAlive() && !villager.isBaby());
        near.sort(java.util.Comparator.comparingDouble(villager -> villager.distanceToSqr(player)));
        if (near.isEmpty()) {
            source.sendFailure(Component.literal("No villager nearby"));
            return 0;
        }
        Villager first = near.get(0);
        if (action.equals("founders")) {
            // debug: the founders' households of the nearest town made now, whatever its founding day
            BlockPos bell = com.vnap.village.home.Towns.bellOf(first);
            if (bell == null) return 0;
            source.sendSuccess(() -> Component.literal(Family.founders(level, bell, day)), false);
            return 1;
        }
        boolean couple = action.equals("birth") || action.equals("quarrel");
        // birth and quarrel need spouses: the nearest wife whose husband is close by, not just any man and woman
        Villager woman = near.stream().filter(Family::female)
                .filter(villager -> !couple || near.stream().anyMatch(other -> other.getUUID().equals(Family.state(villager).spouse)))
                .findFirst().orElse(null);
        Villager man = couple && woman != null
                ? near.stream().filter(other -> other.getUUID().equals(Family.state(woman).spouse)).findFirst().orElse(null)
                : near.stream().filter(villager -> !Family.female(villager)).findFirst().orElse(null);
        switch (action) {
            case "info" -> {
                FamilyState state = Family.state(first);
                String line = String.format("%s %s spouse=%s courting=%s wedding=%d home=%s bed=%s head=%s children=%d mother=%s lived=%d%s",
                        VillagerHome.nameOf(first), Family.female(first) ? "female" : "male", state.spouseName,
                        state.courting, state.weddingDay, state.home, ((com.vnap.entity.VillagerLifeData) first).vnap$homeBed(),
                        Family.isHead(first), state.childrenBorn, state.mother, state.lived, com.vnap.spire.Heroes.armed(first) ? " HERO" : "");
                // and why a married pair has no child (yet)
                Villager spouse = state.spouse != null && level.getEntity(state.spouse) instanceof Villager found ? found : null;
                String child = spouse == null ? "" : " child: " + java.util.Objects.requireNonNullElse(Family.whyNoChild(level, first, spouse), "may have one");
                String grow = Family.isHead(first) ? " grow: wants=" + com.vnap.village.home.FamilyRooms.wantsRoom(level, first)
                        + " house=" + (com.vnap.village.home.FamilyRooms.houseOf(level, first) == null ? "world" : "mod") + " last=" + com.vnap.village.home.FamilyRooms.lastRefusal : "";
                source.sendSuccess(() -> Component.literal(line + child + grow), false);
                return 1;
            }
            case "room", "room_hand" -> {
                // debug: start the nearest head's extension and set every block at once ("hand": left to its builders)
                boolean byHand = action.equals("room_hand");
                com.vnap.village.home.HouseProject house = com.vnap.village.home.FamilyRooms.houseOf(level, first);
                // a house with levels grows its next level (a bedroom storey, a cellar) instead of a room on its side
                if (house != null && com.vnap.village.home.HouseLevels.next(level, house.plan) != null) {
                    com.vnap.village.home.HouseProject grown = com.vnap.village.home.HouseLevels.upgrade(level, first, house, day);
                    if (grown == null) {
                        source.sendFailure(Component.literal("The next level does not fit here: " + com.vnap.village.home.HouseLevels.refusal));
                        return 0;
                    }
                    if (!byHand) HomeProjects.buildNow(level, grown);
                    source.sendSuccess(() -> Component.literal((byHand ? "growing by hand into " : "grown into ") + grown.plan), true);
                    return 1;
                }
                if (house == null) {
                    // a house of the world: taken as the family's own and grown where it stands (left to its builders)
                    if (!com.vnap.village.home.FamilyRooms.start(level, first, day)) {
                        source.sendFailure(Component.literal("No growth: " + com.vnap.village.home.FamilyRooms.lastRefusal));
                        return 0;
                    }
                    String plan = "a new house elsewhere";
                    for (com.vnap.village.home.HouseProject project : VillageMoodSavedData.get(level).houses()) {
                        if (!project.done && project.owner.equals(first.getUUID())) plan = project.plan + " at " + project.origin.toShortString();
                    }
                    String started = plan;
                    source.sendSuccess(() -> Component.literal("building " + started + " by hand"), true);
                    return 1;
                }
                if (!com.vnap.village.home.FamilyRooms.start(level, first, day)) {
                    source.sendFailure(Component.literal("No room: not a head of a villager-built house, a room already, or no space"));
                    return 0;
                }
                com.vnap.village.build.BuildJob job = house.roomJob(level);
                while (job != null && !job.done()) {
                    job.skipDone(level);
                    job.placeNext(level, null);
                }
                com.vnap.village.home.FamilyRooms.finish(level, house, first);
                source.sendSuccess(() -> Component.literal("room built"), true);
                return 1;
            }
            case "elder" -> {
                com.vnap.dialogue.Generations.makeElder(first, day);
                source.sendSuccess(() -> Component.literal(VillagerHome.nameOf(first) + " is an elder now"), true);
                return 1;
            }
            case "passaway" -> {
                String name = VillagerHome.nameOf(first);
                com.vnap.dialogue.Generations.passAway(level, first);
                source.sendSuccess(() -> Component.literal(name + " passed away"), true);
                return 1;
            }
            case "grow" -> {
                Villager kid = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(16.0), Villager::isBaby).stream()
                        .min(java.util.Comparator.comparingDouble(villager -> villager.distanceToSqr(player))).orElse(null);
                if (kid == null) {
                    source.sendFailure(Component.literal("No child nearby"));
                    return 0;
                }
                kid.setAge(0);
                Family.state(kid).tickedDay = -1L;
                Family.tick(level, kid, day);
                source.sendSuccess(() -> Component.literal(VillagerHome.nameOf(kid) + " came of age"), true);
                return 1;
            }
            case "female", "male" -> {
                ((com.vnap.entity.VillagerLifeData) first).vnap$setFemale(action.equals("female"));
                source.sendSuccess(() -> Component.literal("now " + action), true);
                return 1;
            }
            default -> {
            }
        }
        if (man == null || woman == null) {
            source.sendFailure(Component.literal("Need a man and a woman nearby"));
            return 0;
        }
        boolean done = switch (action) {
            case "court" -> {
                Family.startCourting(man, woman, day);
                yield true;
            }
            case "wedding" -> {
                Family.startCourting(man, woman, day - MoodConfig.courtshipDays());
                Family.state(man).weddingDay = day;
                Family.state(woman).weddingDay = day;
                yield true;
            }
            case "marry" -> Family.marry(level, man, woman);
            case "quarrel" -> {
                com.vnap.dialogue.FamilyEvents.quarrel(woman, man, day);
                yield true;
            }
            case "birth" -> {
                String why = Family.whyNoChild(level, woman, man);
                if (why != null) {
                    source.sendFailure(Component.literal("no birth: " + why));
                    yield false;
                }
                boolean born = Family.birth(level, woman, man) != null;
                if (!born) source.sendFailure(Component.literal("no birth: the bed could not be booked or the baby did not spawn"));
                yield born;
            }
            default -> false;
        };
        if (!done) {
            source.sendFailure(Component.literal(action + " failed (a house, the spouses and room for a child bed are needed)"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(action + ": " + VillagerHome.nameOf(man) + " & " + VillagerHome.nameOf(woman)), true);
        return 1;
    }

    /** Debug: the nearest villager (and its sweetheart) leaves for a hamlet now, or every waiting hamlet is founded. */
    private static int hamlet(CommandSourceStack source, boolean depart) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        ServerLevel level = source.getLevel();
        if (!depart) {
            int founded = 0;
            for (com.vnap.village.home.Hamlets.Settlement settlement
                    : java.util.List.copyOf(com.vnap.village.home.Hamlets.get(level.getServer()).pending())) {
                if (settlement.dimension().equals(level.dimension()) && com.vnap.village.home.Hamlets.found(level, settlement)) {
                    com.vnap.village.home.Hamlets.get(level.getServer()).pending().remove(settlement);
                    founded++;
                }
            }
            int count = founded;
            source.sendSuccess(() -> Component.literal(count + " hamlet(s) founded"), true);
            return count;
        }
        Villager villager = level.getNearestEntity(Villager.class, TargetingConditions.forNonCombat().ignoreLineOfSight(),
                player, player.getX(), player.getY(), player.getZ(), player.getBoundingBox().inflate(16.0));
        if (villager == null) {
            source.sendFailure(Component.literal("No villager nearby"));
            return 0;
        }
        java.util.List<Villager> group = new java.util.ArrayList<>(java.util.List.of(villager));
        if (Family.state(villager).courting != null && level.getEntity(Family.state(villager).courting) instanceof Villager partner) {
            group.add(partner);
        }
        var settlement = com.vnap.village.home.Hamlets.depart(level, group, com.vnap.village.home.Towns.bellOf(villager, villager.blockPosition()));
        if (settlement == null) {
            source.sendFailure(Component.literal("Could not leave"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Hamlet waits at " + settlement.pos().toShortString()), true);
        return 1;
    }

    /**
     * Debug for the nearest head of a family: {@code info}, {@code stray} (find or bring one), {@code adopt} (tame it at
     * once), {@code feed} (the wife fills the bowl), {@code pat} and {@code train} (the nearest child), {@code kennel}
     * (built at once).
     */
    private static int pet(CommandSourceStack source, String action) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        ServerLevel level = source.getLevel();
        long day = Family.day(level);
        Villager head = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(24.0), Family::isHead).stream()
                .min(java.util.Comparator.comparingDouble(villager -> villager.distanceToSqr(player))).orElse(null);
        if (head == null) {
            source.sendFailure(Component.literal("No head of a family nearby"));
            return 0;
        }
        FamilyState family = Family.state(head);
        net.minecraft.world.entity.Entity pet = family.pet == null ? null : level.getEntity(family.pet);
        net.minecraft.world.entity.Entity stray = family.stray == null ? null : level.getEntity(family.stray);
        Villager kid = Family.household(level, head).stream().filter(Villager::isBaby).findFirst().orElse(null);
        String result;
        switch (action) {
            case "forget" -> {
                family.pet = null;
                family.stray = null;
                family.petLostDay = Long.MIN_VALUE / 2;
                result = "the family has no pet now";
            }
            case "stray", "stray:DOG", "stray:CAT", "stray:PARROT" -> {
                String why = com.vnap.village.home.Pets.whyNoPet(level, head, day);
                var kind = action.contains(":") ? com.vnap.village.home.Pets.Kind.valueOf(action.substring(6))
                        : com.vnap.village.home.Pets.wanted(level, head);
                var found = why == null ? com.vnap.village.home.Pets.findStray(level, head, family, day, kind) : null;
                result = found != null ? "stray " + found.getType().getDescription().getString() + " at " + found.blockPosition().toShortString()
                        : "no stray: " + (why != null ? why : "none found or spawned");
            }
            case "adopt" -> {
                if (!(stray instanceof net.minecraft.world.entity.TamableAnimal animal)) {
                    source.sendFailure(Component.literal("No stray: /vnap pet stray first"));
                    return 0;
                }
                com.vnap.village.home.Pets.adopt(level, head, animal);
                result = "adopted " + animal.getName().getString();
            }
            case "feed" -> {
                Villager wife = family.spouse != null && level.getEntity(family.spouse) instanceof Villager found ? found : head;
                result = com.vnap.village.home.Pets.fillBowl(level, wife, family) ? "bowl filled" : "no bowl, no food, or the bowl is full";
            }
            case "pat", "train" -> {
                if (kid == null || !(pet instanceof net.minecraft.world.entity.TamableAnimal animal)) {
                    source.sendFailure(Component.literal("Need a child and a pet"));
                    return 0;
                }
                if (action.equals("pat")) com.vnap.village.home.Pets.pet(level, kid, animal);
                else if (!(animal instanceof net.minecraft.world.entity.animal.Wolf dog) || !com.vnap.village.home.Pets.startTraining(level, kid, dog)) {
                    source.sendFailure(Component.literal("Only a free dog can be trained"));
                    return 0;
                }
                result = action + " " + animal.getName().getString();
            }
            case "kennel" -> {
                com.vnap.village.home.Pets.buildKennelNow(level, head);
                result = family.kennel == null ? "no kennel: a dog and a free spot in the yard are needed" : "kennel at " + family.kennel.toShortString();
            }
            default -> result = VillagerHome.nameOf(head) + " pet=" + (pet == null ? "-" : pet.getName().getString()) + " stray="
                    + (stray == null ? "-" : stray.getType().getDescription().getString() + " trust " + stray.getPersistentData().getInt(
                    com.vnap.village.home.Pets.TRUST)) + " kennel=" + family.kennel + " why=" + com.vnap.village.home.Pets.whyNoPet(level, head, day)
                    + " kind=" + com.vnap.village.home.Pets.wanted(level, head);
        }
        String message = result;
        source.sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    /** Debug: the child nearest to the player starts a game of hide-and-seek. */
    private static int hide(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        ServerLevel level = source.getLevel();
        Villager kid = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(16.0), Villager::isBaby).stream()
                .min(java.util.Comparator.comparingDouble(villager -> villager.distanceToSqr(player))).orElse(null);
        var game = kid == null ? null : com.vnap.dialogue.FamilyEvents.start(level, kid,
                com.vnap.village.home.Towns.bellOf(kid, kid.blockPosition()), level.getGameTime());
        if (game == null) {
            source.sendFailure(Component.literal("No game: a child, two more nearby and a hiding place are needed"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Hide-and-seek with " + game.hiders.size() + " hiders"), true);
        return 1;
    }

    /**
     * Mayor debugging around the nearest villager's bell: {@code info} on the office, {@code elect} holds the
     * election now, {@code resign} sends the mayor out of office (a new one is elected), {@code hall} finishes the
     * mayor's hall (started first when none is going up).
     */
    private static int mayor(CommandSourceStack source, String action) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        ServerLevel level = source.getLevel();
        long day = Family.day(level);
        BlockPos bell = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(48.0),
                        villager -> villager.getBrain().hasMemoryValue(MemoryModuleType.MEETING_POINT))
                .stream().min(java.util.Comparator.comparingDouble(villager -> villager.distanceToSqr(player)))
                .flatMap(villager -> villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT))
                .map(net.minecraft.core.GlobalPos::pos).orElse(null);
        if (bell == null) {
            source.sendFailure(Component.literal("No villager with a bell nearby"));
            return 0;
        }
        com.vnap.village.home.Mayors mayors = com.vnap.village.home.Mayors.get(level.getServer());
        com.vnap.village.home.Mayors.Office office = mayors.officeNear(level.dimension(), bell);
        Villager mayor = com.vnap.village.home.Mayors.mayorNear(level, bell);
        if (action.equals("elect")) {
            com.vnap.village.home.Mayors.Office seat = mayors.officeFor(level.dimension(), bell);
            if (seat.mayor != null) {
                source.sendFailure(Component.literal("The office is held by " + seat.name));
                return 0;
            }
            Villager elected = mayors.elect(level, seat, day, null);
            if (elected == null) {
                source.sendFailure(Component.literal("No mayor: fewer than " + TownRules.MAYOR_MIN_ADULTS
                        + " grown-ups or nobody without a trade"));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("elected " + VillagerHome.nameOf(elected)), true);
            return 1;
        }
        if (action.equals("resign")) {
            if (mayor == null) {
                source.sendFailure(Component.literal("No mayor loaded here"));
                return 0;
            }
            com.vnap.village.home.Mayors.resign(level, mayor, day, true);
            Villager next = com.vnap.village.home.Mayors.mayorNear(level, bell);
            source.sendSuccess(() -> Component.literal("resigned; now " + (next == null ? "no mayor" : VillagerHome.nameOf(next))), true);
            return 1;
        }
        if (action.equals("hall")) {
            if (mayor == null) {
                source.sendFailure(Component.literal("No mayor loaded here"));
                return 0;
            }
            HouseProject hall = null;
            for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
                if (!house.done && house.owner.equals(mayor.getUUID())) hall = house;
            }
            if (hall == null) hall = com.vnap.village.home.Mayors.planHall(level, mayor, bell, day);
            if (hall == null) {
                source.sendFailure(Component.literal("No site for the hall near the bell, and no house to take down for it"));
                return 0;
            }
            HomeProjects.buildNow(level, hall);
            HouseProject built = hall;
            source.sendSuccess(() -> Component.literal("hall " + built.schematicName() + " at " + built.origin.toShortString()), true);
            return 1;
        }
        String text = office == null ? "no office at " + bell.toShortString() + " yet"
                : "office " + office.bell.toShortString() + ": " + (office.mayor == null ? "vacant" : office.name
                + (mayor == null ? " (not loaded)" : "") + " since day " + office.electedDay
                + (office.homelessSince == Long.MIN_VALUE ? "" : ", homeless since day " + office.homelessSince)
                + (mayor != null && com.vnap.village.home.Mayors.hall(level, mayor) != null ? ", lives in the hall" : "")
                + building(level, office.mayor, office.bell))
                + "; last election day " + (office.lastElection == Long.MIN_VALUE ? "-" : office.lastElection)
                + "; offices " + mayors.offices().size();
        String town = "; town " + com.vnap.village.home.Towns.population(level, bell) + "/" + com.vnap.village.home.Towns.limit(level, bell)
                + " (register " + (office == null ? 0 : office.residents.size()) + "), reach "
                + Math.round(com.vnap.village.home.Towns.reach(level, bell));
        source.sendSuccess(() -> Component.literal(text + town), false);
        return 1;
    }

    /** The house the mayor builds, or the village's hall going up (its builder may be dead: the mayor finishes it). */
    private static String building(ServerLevel level, java.util.@org.jspecify.annotations.Nullable UUID mayor, BlockPos bell) {
        for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
            if (house.done || !(house.owner.equals(mayor) || (com.vnap.village.home.Mayors.HALLS.contains(house.plan)
                    && house.bell.closerThan(bell, TownRules.SPAN)))) continue;
            return ", building " + house.schematicName() + " at " + house.origin.toShortString()
                    + (house.clearing.isEmpty() ? "" : " (" + house.clearing.size() + " cells of an old house to clear)");
        }
        return "";
    }

    /**
     * Debug: a schematic (e.g. {@code house/mayor_house@plains}) read afresh from the resources (after /reload) and set
     * down at once, its corner three blocks south of the player, front to the north, its ground cleared first ("+name":
     * over what stands): for looking at plans while they are drawn.
     */
    private static int schem(CommandSourceStack source, String spec) {
        ServerLevel level = source.getLevel();
        // "+name" sets it over what stands (an extension room in its house's frame)
        boolean over = spec.startsWith("+");
        String name = over ? spec.substring(1) : spec;
        int at = name.indexOf('@');
        com.vnap.village.script.SchematicSpec plan = com.vnap.village.script.SchematicSpec.load(level.getServer().getResourceManager(),
                at < 0 ? name : name.substring(0, at));
        if (at >= 0) plan = com.vnap.village.home.HousePalette.apply(plan, name.substring(at + 1));
        if (plan.blocks.isEmpty()) {
            source.sendFailure(Component.literal("No schematic " + name));
            return 0;
        }
        BlockPos feet = BlockPos.containing(source.getPosition());
        BlockPos origin = feet.offset(0, -1, 3);
        if (!over) {
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, 1, -1), origin.offset(plan.sizeX, plan.sizeY + 2, plan.sizeZ))) {
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16);
            }
        }
        // in build order (walls before what hangs on them), as the villagers set it
        com.vnap.village.build.BuildJob job = com.vnap.village.build.BuildJob.missing(level, name, plan, origin,
                net.minecraft.world.level.block.Rotation.NONE, 0);
        job.completeNow(level);
        int blocks = plan.blocks.size();
        source.sendSuccess(() -> Component.literal(name + ": " + blocks + " blocks at " + origin.toShortString()), false);
        return 1;
    }

    /** Debug: the house the nearest owner is having built is finished at once, and the owner moves in. */
    private static int houseFinish(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
            if (!house.done && house.origin.closerThan(BlockPos.containing(source.getPosition()), 48.0)) {
                HomeProjects.buildNow(level, house);
                source.sendSuccess(() -> Component.literal("finished " + house.schematicName() + " at " + house.origin.toShortString()), true);
                return 1;
            }
        }
        source.sendFailure(Component.literal("No house being built within 48 blocks"));
        return 0;
    }

    /** Debug: everything that went wrong around the player so far (DevChecks); "check: 0 problems" when all is well. */
    private static int check(CommandSourceStack source, int radius) {
        java.util.List<String> problems = DevChecks.run(source.getLevel(), BlockPos.containing(source.getPosition()), radius);
        for (String problem : problems) source.sendSuccess(() -> Component.literal("problem: " + problem), false);
        source.sendSuccess(() -> Component.literal("check: " + problems.size() + " problems"), false);
        return problems.isEmpty() ? 1 : 0;
    }

    /** A player's right click on the nearest villager (of that name) with what it holds: the tools cannot click entities. */
    private static int interact(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        Villager villager = source.getLevel().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(16.0),
                        v -> name.isEmpty() || VillagerHome.nameOf(v).equalsIgnoreCase(name)).stream()
                .min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        if (villager == null) {
            source.sendFailure(Component.literal("No villager nearby"));
            return 0;
        }
        var result = com.vnap.Mc.interactOn(player, villager);
        source.sendSuccess(() -> Component.literal(VillagerHome.nameOf(villager) + ": " + result), false);
        return 1;
    }

    /** Debug: why the nearest villager (or the one of that name within 128) has no home (the nearest bed, its door, sign and POI ticket). */
    private static int home(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        Villager villager = source.getLevel().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(name.isEmpty() ? 16.0 : 128.0),
                        v -> name.isEmpty() || VillagerHome.nameOf(v).equalsIgnoreCase(name)).stream()
                .min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        if (villager == null) {
            source.sendFailure(Component.literal("No villager nearby"));
            return 0;
        }
        String why = VillagerHome.nameOf(villager) + ": " + VillagerHome.whyNoHome(source.getLevel(), villager);
        source.sendSuccess(() -> Component.literal(why), false);
        return 1;
    }

    /** Debug: whether the named villager has a way on foot to its bed (HOME) or {@code to}, and where the path ends when it has not. */
    private static int path(CommandSourceStack source, String name, @Nullable BlockPos to) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        Villager villager = source.getLevel().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(128.0),
                v -> VillagerHome.nameOf(v).equalsIgnoreCase(name)).stream().findFirst().orElse(null);
        BlockPos home = villager == null ? null : to != null ? to : villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME)
                .map(net.minecraft.core.GlobalPos::pos).orElse(null);
        if (villager == null || home == null) {
            source.sendFailure(Component.literal("No such villager with a bed nearby"));
            return 0;
        }
        net.minecraft.world.level.pathfinder.Path path = villager.getNavigation().createPath(home, 1);
        String said = VillagerHome.nameOf(villager) + " @" + villager.blockPosition().toShortString() + " -> " + home.toShortString() + ": "
                + (path == null ? "no path" : (path.canReach() ? "reaches" : "does not reach") + ", " + path.getNodeCount() + " nodes, ends at "
                + path.getTarget().toShortString() + " / last node " + (path.getEndNode() == null ? "-" : path.getEndNode().asBlockPos().toShortString()));
        source.sendSuccess(() -> Component.literal(said), false);
        return 1;
    }

    /** Debug: every house project of the level: its owner, plan, place, and how far the build has got. */
    private static int houses(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        java.util.List<HouseProject> all = VillageMoodSavedData.get(level).houses();
        for (HouseProject house : all) {
            String owner = level.getEntity(house.owner) instanceof Villager villager ? VillagerHome.nameOf(villager) : "(gone)";
            var job = house.done && !house.mending ? null : house.job(level);
            String state = job == null ? "done" + (house.hasRoom() ? " +room" : "") + (house.damagedDay >= 0 ? ", damaged day " + house.damagedDay : "")
                    : (house.mending ? "mending, " : "building, ") + job.remaining() + " blocks left, next "
                    + job.nextPos().toShortString() + (job.lacksMaterials() ? " needs " + com.vnap.village.market.Materials.items(job.nextState()) : "")
                    + (house.site == null ? "" : ", funds " + house.site.funds) + (house.pausedDay >= 0 ? ", paused day " + house.pausedDay : "")
                    + (house.pausedDay >= 0 ? ", left " + job.upcoming(12).stream().map(pos -> pos.subtract(house.origin).toShortString()
                    + "=" + level.getBlockState(pos).getBlock().getName().getString()).toList() : "");
            String line = owner + " " + house.schematicName() + " @" + house.origin.toShortString() + " " + state;
            source.sendSuccess(() -> Component.literal(line), false);
        }
        source.sendSuccess(() -> Component.literal(all.size() + " house projects"), false);
        return all.size();
    }

    /** Debug: saves every level and flushes the region files, so tools/dev/restart.sh can kill the client safely. */
    private static int save(CommandSourceStack source) {
        boolean saved = source.getServer().saveEverything(false, true, true);
        source.sendSuccess(() -> Component.literal(saved ? "Saved the game" : "Save failed"), false);
        return saved ? 1 : 0;
    }

    private static final java.util.Map<java.util.UUID, net.minecraft.world.phys.Vec3> LAST_SEEN = new java.util.HashMap<>();

    /** The nearest town's homes in one line: homeless (and how long), households in debt, the treasury and what it printed. */
    private static int housing(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        com.vnap.village.home.Mayors.Office office = com.vnap.village.home.Mayors.get(level.getServer())
                .officeNear(level.dimension(), BlockPos.containing(source.getPosition()));
        if (office == null) {
            source.sendFailure(Component.literal("no town here"));
            return 0;
        }
        BlockPos bell = office.bell;
        long day = Family.day(level);
        int adults = 0, debtors = 0, owed = 0, maxOwed = 0;
        StringBuilder homeless = new StringBuilder();
        for (Villager villager : com.vnap.village.home.Towns.members(level, bell, v -> !v.isBaby())) {
            adults++;
            int debt = Family.state(villager).debt;
            if (debt > 0) {
                debtors++;
                owed += debt;
                maxOwed = Math.max(maxOwed, debt);
            }
            if (com.vnap.village.home.HomeProjects.hasHome(villager)) continue;
            var tag = villager.getPersistentData();
            long since = tag.contains(com.vnap.village.home.HomeProjects.HOMELESS_SINCE) ? com.vnap.Nbt.getLong(tag, com.vnap.village.home.HomeProjects.HOMELESS_SINCE) : day;
            homeless.append(' ').append(VillagerHome.nameOf(villager)).append('(').append(day - since).append("d ")
                    .append(Family.state(villager).coins).append("c)");
        }
        String line = "housing: day " + day + ", adults " + adults + ", homeless" + (homeless.isEmpty() ? " none" : homeless)
                + ", in debt " + debtors + " (" + owed + "c, most " + maxOwed + "), treasury " + office.treasury + ", minted " + office.minted;
        source.sendSuccess(() -> Component.literal(line), false);
        return 1;
    }

    /**
     * Debug: every villager around the player, one line each: who, where, the brain's activity, the behaviours
     * running, the walk target, how far it moved since the last /vnap who, and what the dialogue system holds it for.
     */
    private static int who(CommandSourceStack source, int radius) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        ServerLevel level = source.getLevel();
        java.util.List<Villager> near = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(radius), Villager::isAlive);
        near.sort(java.util.Comparator.comparingDouble(villager -> villager.distanceToSqr(player)));
        for (Villager villager : near) {
            net.minecraft.world.entity.ai.Brain<Villager> brain = villager.getBrain();
            StringBuilder running = new StringBuilder();
            for (net.minecraft.world.entity.ai.behavior.BehaviorControl<? super Villager> behavior : com.vnap.village.life.LifeBrain.running(villager)) {
                String name = behavior.debugString();
                running.append(name.substring(name.lastIndexOf('.') + 1)).append(',');
            }
            String walk = brain.getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
                    .map(target -> target.getTarget().currentBlockPosition().toShortString()).orElse("-");
            net.minecraft.world.phys.Vec3 before = LAST_SEEN.put(villager.getUUID(), villager.position());
            String moved = before == null ? "?" : String.format("%.1f", before.distanceTo(villager.position()));
            FamilyState family = Family.state(villager);
            String ties = (family.married() ? " wed:" + family.spouseName : "") + (family.courting != null ? " courting" : "")
                    + (com.vnap.village.home.Mayors.isMayor(villager) ? " MAYOR" : "")
                    + (com.vnap.village.market.Shops.isKeeper(villager) ? " SHOP" : "")
                    + (Family.state(villager).coins > 0 ? " " + Family.state(villager).coins + "c" : "")
                    + (Family.isHead(villager) ? " head" : "") + (Family.home(villager) == null ? " homeless" : "");
            ties += " mem=" + ((com.vnap.mixin.BrainAccessor<?>) (Object) brain).vnap$memories().values().stream().filter(com.vnap.Mc::remembers).count()
                    + (brain.hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME) ? "H" : "")
                    + (brain.hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE) ? "J" : "")
                    + (brain.hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.MEETING_POINT) ? "M" : "");
            String line = String.format("%s %s%s%s%s %s @%s act=%s moved=%s walk=%s run=[%s]%s%s",
                    VillagerHome.nameOf(villager), Family.female(villager) ? "F" : "M", villager.isBaby() ? " kid" : "", ties,
                    com.vnap.dialogue.Generations.elder(villager) ? " elder" : "",
                    net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(com.vnap.Mc.profession(villager.getVillagerData())).getPath(),
                    villager.blockPosition().toShortString(),
                    brain.getActiveNonCoreActivity().map(Object::toString).orElse("-"), moved, walk, running,
                    villager.isNoAi() ? " NOAI" : "", com.vnap.dialogue.Speech.describe(villager));
            source.sendSuccess(() -> Component.literal(line), false);
        }
        int count = near.size();
        source.sendSuccess(() -> Component.literal(count + " villagers within " + radius), false);
        return count;
    }

    /** Debug: the nearest villager says a line by title, or tells why it does not. */
    private static int say(CommandSourceStack source, String title) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        ServerLevel level = source.getLevel();
        // the villager nearest the command's position (execute at one of them), not the player's
        net.minecraft.world.phys.Vec3 at = source.getPosition();
        Villager villager = level.getNearestEntity(Villager.class, TargetingConditions.forNonCombat().ignoreLineOfSight(),
                player, at.x, at.y, at.z, new net.minecraft.world.phys.AABB(at, at).inflate(16.0));
        if (villager == null) {
            source.sendFailure(Component.literal("No villager nearby"));
            return 0;
        }
        // "!Title": said now, over a neighbour still talking and past its cooldown (a scene set up by hand)
        boolean now = title.startsWith("!");
        String asked = now ? title.substring(1) : title;
        com.vnap.dialogue.Speech.staged = now;
        boolean said;
        try {
            said = com.vnap.dialogue.Speech.line(villager, asked, "debug_say:" + villager.getUUID() + ":" + level.getGameTime(), false);
        } finally {
            com.vnap.dialogue.Speech.staged = false;
        }
        String result = said ? "said " + asked : "silent: " + com.vnap.dialogue.Speech.lastRefusal();
        source.sendSuccess(() -> Component.literal(result), false);
        return said ? 1 : 0;
    }

    /** Debug: plans a house for the nearest villager and sets every block at once; the owner moves in. */
    private static int houseNow(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        ServerLevel level = source.getLevel();
        Villager villager = level.getNearestEntity(Villager.class, TargetingConditions.forNonCombat().ignoreLineOfSight(),
                player, player.getX(), player.getY(), player.getZ(), player.getBoundingBox().inflate(16.0));
        HouseProject house = villager == null ? null : HomeProjects.start(level, villager, Family.day(level));
        if (house == null) {
            source.sendFailure(Component.literal("No villager with a bell nearby, or no site"));
            return 0;
        }
        HomeProjects.buildNow(level, house);
        source.sendSuccess(() -> Component.literal("House " + house.schematicName() + " at " + house.origin.toShortString()), true);
        return 1;
    }

    /** Why the villager nearest to the player does not build a house; {@code start} plans one regardless of chance. */
    private static int house(CommandSourceStack source, boolean start) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        ServerLevel level = source.getLevel();
        Villager villager = level.getNearestEntity(Villager.class, TargetingConditions.forNonCombat().ignoreLineOfSight(),
                player, player.getX(), player.getY(), player.getZ(), player.getBoundingBox().inflate(16.0));
        if (villager == null) {
            source.sendFailure(Component.literal("No villager nearby"));
            return 0;
        }
        long day = Family.day(level);
        if (!start) {
            String blocker = HomeProjects.blocker(level, villager, day);
            source.sendSuccess(() -> Component.literal(blocker == null ? "may start a house" : "no house: " + blocker), false);
            return 1;
        }
        HouseProject house = HomeProjects.start(level, villager, day);
        if (house == null) {
            source.sendFailure(Component.literal("No site for a house near the bell"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("House " + house.schematicName() + " at " + house.origin.toShortString()), true);
        return 1;
    }

    private static @Nullable MoodState resolve(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return null;
        }
        ServerLevel level = source.getLevel();
        VillageMoodApi.VillageRef ref = VillageMoodApi.resolve(level, player.blockPosition());
        if (ref == null) {
            source.sendFailure(Component.literal("No village nearby"));
            return null;
        }
        return VillageMoodSavedData.get(level).getOrCreate(ref.villageId(), ref.meetingPos());
    }

    private static int get(CommandSourceStack source) {
        MoodState state = resolve(source);
        if (state == null) {
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format(
                "mood=%.1f standing=%.1f script=%s flags=%d monument=%s altar=%s last=%s",
                state.mood, state.standing, state.script, state.flags,
                state.monumentPos, state.altarPos, VillageScriptDirector.lastReason(source.getLevel(), state.villageId))), false);
        return 1;
    }

    private static int set(CommandSourceStack source, float mood, float standing) {
        MoodState state = resolve(source);
        if (state == null) {
            return 0;
        }
        state.standing = standing;
        VillageMoodApi.setExact(source.getLevel(), state, mood, true);
        source.sendSuccess(() -> Component.translatable("vnap.command.mood.set", String.format("%.0f", state.mood)), true);
        return why(source, state);
    }

    private static int why(CommandSourceStack source) {
        MoodState state = resolve(source);
        return state == null ? 0 : why(source, state);
    }

    private static int why(CommandSourceStack source, MoodState state) {
        for (Component line : VillageScriptDirector.explain(source.getLevel(), state)) {
            source.sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int build(CommandSourceStack source, String kind) {
        MoodState state = resolve(source);
        if (state == null) {
            return 0;
        }
        ScriptKind script = switch (kind.toLowerCase(java.util.Locale.ROOT)) {
            case "altar", "forge" -> ScriptKind.BUILD_ALTAR;
            case "festoon" -> ScriptKind.BUILD_FESTOON;
            case "festival" -> ScriptKind.BUILD_FESTIVAL;
            case "pavilion" -> ScriptKind.BUILD_PAVILION;
            case "vigil" -> ScriptKind.BUILD_VIGIL;
            case "mourning" -> ScriptKind.BUILD_MOURNING;
            case "pyre" -> ScriptKind.BUILD_PYRE;
            default -> ScriptKind.BUILD_MONUMENT;
        };
        boolean ok = VillageScriptDirector.forceStart(source.getLevel(), state, script);
        source.sendSuccess(() -> Component.literal(ok ? "Build started" : "Build failed"), true);
        return ok ? 1 : 0;
    }

    private static int summon(CommandSourceStack source) {
        MoodState state = resolve(source);
        if (state == null) {
            return 0;
        }
        boolean ok = VillageScriptDirector.forceStart(source.getLevel(), state, ScriptKind.SUMMON);
        source.sendSuccess(() -> Component.literal(ok ? "Summon started" : "Summon failed"), true);
        return ok ? 1 : 0;
    }

    private static int abort(CommandSourceStack source) {
        MoodState state = resolve(source);
        if (state == null) {
            return 0;
        }
        VillageScriptDirector.abort(source.getLevel(), state, false);
        source.sendSuccess(() -> Component.literal("Script aborted"), true);
        return 1;
    }

    private static int killBoss(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Entity anchor = source.getEntity();
        var box = anchor == null
                ? new AABB(source.getPosition(), source.getPosition()).inflate(64.0)
                : anchor.getBoundingBox().inflate(64.0);
        int killed = 0;
        for (TocsinGolem golem : level.getEntities(VillageEntities.TOCSIN_GOLEM, box, e -> true)) {
            //? if >=1.21.2 {
            /*golem.kill(level);
            *///?} else
            golem.kill();
            killed++;
        }
        int finalKilled = killed;
        source.sendSuccess(() -> Component.literal("Killed " + finalKilled + " golem(s)"), true);
        return killed;
    }

    /** Debug: wakes a golem on the spot, tied to the nearest village if there is one. */
    private static int spawnBoss(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        TocsinGolem golem = com.vnap.Mc.create(VillageEntities.TOCSIN_GOLEM, level);
        if (golem == null) {
            return 0;
        }
        net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.containing(source.getPosition());
        com.vnap.Mc.snap(golem, source.getPosition().x, source.getPosition().y, source.getPosition().z, 0.0F, 0.0F);
        level.addFreshEntity(golem);
        MoodState state = resolve(source);
        golem.bind(state != null ? state.villageId : Long.MIN_VALUE, at);
        if (state != null) {
            state.script = ScriptKind.BOSS_ACTIVE;
            VillageMoodSavedData.get(level).markDirty();
        }
        source.sendSuccess(() -> Component.literal("Tocsin Golem awakened"), true);
        return 1;
    }
}
