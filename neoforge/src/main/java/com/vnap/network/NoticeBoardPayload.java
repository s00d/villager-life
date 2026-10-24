package com.vnap.network;

import com.vnap.VillagerLife;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The town's notice board as a player reads it (NoticeBoard, NoticeBoardScreen): the town at a glance, what it is
 * building and how far along, and what its stall is waiting for (a player may bring it, at the full price).
 * {@code serviceIn}/{@code caravanIn}: days until the chapel's service and the caravan (-1: none); {@code flag}: the town's
 * flag (TownFlag code, -1: none).
 */
public record NoticeBoardPayload(BlockPos board, String mayor, int people, int limit, int treasury, int mood, boolean fair, int serviceIn, int caravanIn,
								 int lost, List<Project> projects, List<Wanted> wanted, PlanMap plan, List<Errand> errands, int flag, List<Chronicle> chronicle,
								 List<Shipment> shipments) implements CustomPacketPayload {
	public static final Type<NoticeBoardPayload> TYPE = new Type<>(VillagerLife.id("notice_board"));

	/** A build: a lang key (or a family's name for a house) and its blocks left of all. */
	public record Project(String label, String name, int left, int total) {
	}

	/** What players ordered at the stall and the caravan has yet to bring ({@code item}'s count is how many; {@code who} ordered it). */
	public record Shipment(ItemStack item, String who) {
	}

	/** A line of the town's chronicle: its day, its lang key (chronicle.villager_life.*) and the names in it. */
	public record Chronicle(long day, String key, List<String> args) {
	}

	/** {@code item}'s count is how many are wanted; {@code price} what one is paid. */
	public record Wanted(ItemStack item, int price) {
	}

	/** An errand on the board: for whom, what (its count), the reward, whose it is ({@code state}: OPEN, MINE, TAKEN), days left. */
	public record Errand(int id, String name, ItemStack item, int reward, int state, int daysLeft) {
		public static final int OPEN = 0;
		public static final int MINE = 1;
		public static final int TAKEN = 2;
	}

	/** A lot of the town's plan: its ground, whose it is ({@code state}: FREE, HOUSE, MINE, THEIRS), its price, its owner. */
	public record Lot(int id, int minX, int minZ, int maxX, int maxZ, int state, int price, String owner) {
		public static final int FREE = 0;
		public static final int HOUSE = 1;
		public static final int MINE = 2;
		public static final int THEIRS = 3;
	}

	/**
	 * The town's plan from above, round the bell (columns relative to it): its buildings as boxes, its laid road cells
	 * and planned streets (packed dx, dz), its lots, and where the reader stands.
	 */
	public static final class PlanMap {
		public final int radius;
		public final int youX;
		public final int youZ;
		/** minX, minZ, maxX, maxZ per building. */
		public final int[] buildings;
		public final int[] roads;
		public final int[] streets;
		public final List<Lot> lots;

		public PlanMap(int radius, int youX, int youZ, int[] buildings, int[] roads, int[] streets, List<Lot> lots) {
			this.radius = radius;
			this.youX = youX;
			this.youZ = youZ;
			this.buildings = buildings;
			this.roads = roads;
			this.streets = streets;
			this.lots = lots;
		}

		public static int pack(int dx, int dz) {
			return (dx & 0xFFFF) << 16 | (dz & 0xFFFF);
		}

		public static int dx(int packed) {
			return (short) (packed >> 16);
		}

		public static int dz(int packed) {
			return (short) packed;
		}

		void write(RegistryFriendlyByteBuf buffer) {
			buffer.writeVarInt(radius);
			buffer.writeVarInt(youX);
			buffer.writeVarInt(youZ);
			buffer.writeVarIntArray(buildings);
			buffer.writeVarIntArray(roads);
			buffer.writeVarIntArray(streets);
			buffer.writeVarInt(lots.size());
			for (Lot lot : lots) {
				buffer.writeVarInt(lot.id());
				buffer.writeVarInt(lot.minX());
				buffer.writeVarInt(lot.minZ());
				buffer.writeVarInt(lot.maxX());
				buffer.writeVarInt(lot.maxZ());
				buffer.writeVarInt(lot.state());
				buffer.writeVarInt(lot.price());
				buffer.writeUtf(lot.owner());
			}
		}

		static PlanMap read(RegistryFriendlyByteBuf buffer) {
			int radius = buffer.readVarInt();
			int youX = buffer.readVarInt();
			int youZ = buffer.readVarInt();
			int[] buildings = buffer.readVarIntArray();
			int[] roads = buffer.readVarIntArray();
			int[] streets = buffer.readVarIntArray();
			int n = buffer.readVarInt();
			List<Lot> lots = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				lots.add(new Lot(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
						buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf()));
			}
			return new PlanMap(radius, youX, youZ, buildings, roads, streets, lots);
		}
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, NoticeBoardPayload> CODEC = new StreamCodec<>() {
		@Override
		public NoticeBoardPayload decode(RegistryFriendlyByteBuf buffer) {
			BlockPos board = buffer.readBlockPos();
			int lost = buffer.readVarInt();
			String mayor = buffer.readUtf();
			int people = buffer.readVarInt();
			int limit = buffer.readVarInt();
			int treasury = buffer.readVarInt();
			int mood = buffer.readVarInt();
			boolean fair = buffer.readBoolean();
			int serviceIn = buffer.readVarInt();
			int caravanIn = buffer.readVarInt();
			int projects = buffer.readVarInt();
			List<Project> builds = new ArrayList<>(projects);
			for (int i = 0; i < projects; i++) builds.add(new Project(buffer.readUtf(), buffer.readUtf(), buffer.readVarInt(), buffer.readVarInt()));
			int wanted = buffer.readVarInt();
			List<Wanted> wants = new ArrayList<>(wanted);
			for (int i = 0; i < wanted; i++) {
				ItemStack item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
				int count = buffer.readVarInt();
				wants.add(new Wanted(item.copyWithCount(count), buffer.readVarInt()));
			}
			PlanMap plan = PlanMap.read(buffer);
			int n = buffer.readVarInt();
			List<Errand> errands = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				int id = buffer.readVarInt();
				String name = buffer.readUtf();
				ItemStack item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
				int count = buffer.readVarInt();
				errands.add(new Errand(id, name, item.copyWithCount(count), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()));
			}
			int flag = buffer.readInt();
			int lines = buffer.readVarInt();
			List<Chronicle> chronicle = new ArrayList<>(lines);
			for (int i = 0; i < lines; i++) {
				long day = buffer.readVarLong();
				String key = buffer.readUtf();
				int count = buffer.readVarInt();
				List<String> args = new ArrayList<>(count);
				for (int j = 0; j < count; j++) args.add(buffer.readUtf());
				chronicle.add(new Chronicle(day, key, args));
			}
			int shipped = buffer.readVarInt();
			List<Shipment> shipments = new ArrayList<>(shipped);
			for (int i = 0; i < shipped; i++) {
				ItemStack item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
				int count = buffer.readVarInt();
				shipments.add(new Shipment(item.copyWithCount(count), buffer.readUtf()));
			}
			return new NoticeBoardPayload(board, mayor, people, limit, treasury, mood, fair, serviceIn, caravanIn, lost, builds, wants, plan, errands, flag, chronicle,
					shipments);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buffer, NoticeBoardPayload payload) {
			buffer.writeBlockPos(payload.board());
			buffer.writeVarInt(payload.lost());
			buffer.writeUtf(payload.mayor());
			buffer.writeVarInt(payload.people());
			buffer.writeVarInt(payload.limit());
			buffer.writeVarInt(payload.treasury());
			buffer.writeVarInt(payload.mood());
			buffer.writeBoolean(payload.fair());
			buffer.writeVarInt(payload.serviceIn());
			buffer.writeVarInt(payload.caravanIn());
			buffer.writeVarInt(payload.projects().size());
			for (Project project : payload.projects()) {
				buffer.writeUtf(project.label());
				buffer.writeUtf(project.name());
				buffer.writeVarInt(project.left());
				buffer.writeVarInt(project.total());
			}
			buffer.writeVarInt(payload.wanted().size());
			for (Wanted want : payload.wanted()) {
				ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, want.item().copyWithCount(1));
				buffer.writeVarInt(want.item().getCount());
				buffer.writeVarInt(want.price());
			}
			payload.plan().write(buffer);
			buffer.writeVarInt(payload.errands().size());
			for (Errand errand : payload.errands()) {
				buffer.writeVarInt(errand.id());
				buffer.writeUtf(errand.name());
				ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, errand.item().copyWithCount(1));
				buffer.writeVarInt(errand.item().getCount());
				buffer.writeVarInt(errand.reward());
				buffer.writeVarInt(errand.state());
				buffer.writeVarInt(errand.daysLeft());
			}
			buffer.writeInt(payload.flag());
			buffer.writeVarInt(payload.chronicle().size());
			for (Chronicle line : payload.chronicle()) {
				buffer.writeVarLong(line.day());
				buffer.writeUtf(line.key());
				buffer.writeVarInt(line.args().size());
				for (String arg : line.args()) buffer.writeUtf(arg);
			}
			buffer.writeVarInt(payload.shipments().size());
			for (Shipment shipment : payload.shipments()) {
				ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, shipment.item().copyWithCount(1));
				buffer.writeVarInt(shipment.item().getCount());
				buffer.writeUtf(shipment.who());
			}
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
