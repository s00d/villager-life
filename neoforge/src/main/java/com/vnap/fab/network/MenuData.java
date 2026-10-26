package com.vnap.fab.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** What a menu is opened with, as the bytes NeoForge's writer put down (com.vnap.fab.Menus). */
public final class MenuData {
	public static final StreamCodec<ByteBuf, MenuData> STREAM_CODEC = ByteBufCodecs.BYTE_ARRAY.map(MenuData::new, MenuData::bytes);

	private final byte[] bytes;

	public MenuData(byte[] bytes) {
		this.bytes = bytes.clone();
	}

	public byte[] bytes() {
		return bytes.clone();
	}
}
