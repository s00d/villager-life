package com.vnap.neo.neoforge.client.event;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import com.vnap.neo.bus.api.ICancellableEvent;

public final class ViewportEvent {
	private ViewportEvent() {
	}

	public static final class RenderFog extends Event implements ICancellableEvent {
		static {
			Bridge.bind(RenderFog.class, (priority, sink) -> Bridge.game(net.minecraftforge.client.event.ViewportEvent.RenderFog.class, priority, RenderFog::new, sink));
		}

		private final net.minecraftforge.client.event.ViewportEvent.RenderFog f;

		RenderFog(net.minecraftforge.client.event.ViewportEvent.RenderFog f) {
			super(f);
			this.f = f;
		}

		public float getFarPlaneDistance() {
			return f.getFarPlaneDistance();
		}

		public float getNearPlaneDistance() {
			return f.getNearPlaneDistance();
		}

		public void setFarPlaneDistance(float distance) {
			f.setFarPlaneDistance(distance);
		}

		public void setNearPlaneDistance(float distance) {
			f.setNearPlaneDistance(distance);
		}

		//? if <1.21.6 {
		public void setFogShape(com.mojang.blaze3d.shaders.FogShape shape) {
			f.setFogShape(shape);
		}
		//?}
	}

	public static final class ComputeFogColor extends Event {
		static {
			Bridge.bind(ComputeFogColor.class, (priority, sink) -> Bridge.game(net.minecraftforge.client.event.ViewportEvent.ComputeFogColor.class, priority,
					ComputeFogColor::new, sink));
		}

		private final net.minecraftforge.client.event.ViewportEvent.ComputeFogColor f;

		ComputeFogColor(net.minecraftforge.client.event.ViewportEvent.ComputeFogColor f) {
			super(f);
			this.f = f;
		}

		public float getRed() {
			return f.getRed();
		}

		public float getGreen() {
			return f.getGreen();
		}

		public float getBlue() {
			return f.getBlue();
		}

		public void setRed(float red) {
			f.setRed(red);
		}

		public void setGreen(float green) {
			f.setGreen(green);
		}

		public void setBlue(float blue) {
			f.setBlue(blue);
		}
	}

	public static final class ComputeCameraAngles extends Event {
		static {
			Bridge.bind(ComputeCameraAngles.class, (priority, sink) -> Bridge.game(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles.class, priority,
					ComputeCameraAngles::new, sink));
		}

		private final net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles f;

		ComputeCameraAngles(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles f) {
			super(f);
			this.f = f;
		}

		public double getPartialTick() {
			return f.getPartialTick();
		}

		public float getYaw() {
			return f.getYaw();
		}

		public float getPitch() {
			return f.getPitch();
		}

		public float getRoll() {
			return f.getRoll();
		}

		public void setYaw(float yaw) {
			f.setYaw(yaw);
		}

		public void setPitch(float pitch) {
			f.setPitch(pitch);
		}

		public void setRoll(float roll) {
			f.setRoll(roll);
		}
	}
}
