package com.vnap.fab.neoforge.client.event;

import com.vnap.fab.bus.api.Event;
import com.vnap.fab.bus.api.ICancellableEvent;
import org.jspecify.annotations.Nullable;

/** The camera and the fog of a frame (com.vnap.fab.mixin.client.FogMixin, CameraMixin): what the listeners leave is used. */
public final class ViewportEvent {
	private ViewportEvent() {
	}

	/** The fog's planes; a cancel keeps what the listeners set (as NeoForge's event). */
	public static final class RenderFog extends Event implements ICancellableEvent {
		private float far;
		private float near;
		//? if <1.21.6
		private com.mojang.blaze3d.shaders.@Nullable FogShape shape;

		public RenderFog(float near, float far) {
			this.near = near;
			this.far = far;
		}

		public float getFarPlaneDistance() {
			return far;
		}

		public float getNearPlaneDistance() {
			return near;
		}

		public void setFarPlaneDistance(float distance) {
			far = distance;
		}

		public void setNearPlaneDistance(float distance) {
			near = distance;
		}
		//? if <1.21.6 {

		public void setFogShape(com.mojang.blaze3d.shaders.FogShape shape) {
			this.shape = shape;
		}

		public com.mojang.blaze3d.shaders.@Nullable FogShape vnap$shape() {
			return shape;
		}
		//?}
	}

	public static final class ComputeFogColor extends Event {
		private float red;
		private float green;
		private float blue;

		public ComputeFogColor(float red, float green, float blue) {
			this.red = red;
			this.green = green;
			this.blue = blue;
		}

		public float getRed() {
			return red;
		}

		public float getGreen() {
			return green;
		}

		public float getBlue() {
			return blue;
		}

		public void setRed(float red) {
			this.red = red;
		}

		public void setGreen(float green) {
			this.green = green;
		}

		public void setBlue(float blue) {
			this.blue = blue;
		}
	}

	public static final class ComputeCameraAngles extends Event {
		private final double partialTick;
		private float yaw;
		private float pitch;
		private float roll;

		public ComputeCameraAngles(double partialTick, float yaw, float pitch, float roll) {
			this.partialTick = partialTick;
			this.yaw = yaw;
			this.pitch = pitch;
			this.roll = roll;
		}

		public double getPartialTick() {
			return partialTick;
		}

		public float getYaw() {
			return yaw;
		}

		public float getPitch() {
			return pitch;
		}

		public float getRoll() {
			return roll;
		}

		public void setYaw(float yaw) {
			this.yaw = yaw;
		}

		public void setPitch(float pitch) {
			this.pitch = pitch;
		}

		public void setRoll(float roll) {
			this.roll = roll;
		}
	}
}
