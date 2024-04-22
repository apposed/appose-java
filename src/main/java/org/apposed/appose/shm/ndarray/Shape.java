package org.apposed.appose.shm.ndarray;

import java.util.Arrays;

public class Shape {

	public enum Order {C_ORDER, F_ORDER}

	private final Order order;
	private final int[] shape;

	public Shape(final Order order, final int... shape) {
		this.order = order;
		this.shape = shape;
	}

	public int get(final int d) {
		return shape[d];
	}

	public int length() {
		return shape.length;
	}

	public Order order() {
		return order;
	}

	public long numElements() {
		long n = 1;
		for (int s : shape) {
			n *= s;
		}
		return n;
	}

	public int[] toIntArray() {
		return shape;
	}

	public int[] toIntArray(final Order order) {
		if (order.equals(this.order)) {
			return shape;
		}
		else {
			final int[] ishape = new int[shape.length];
			Arrays.setAll(ishape, i -> shape[shape.length - i - 1]);
			return ishape;
		}
	}

	public long[] toLongArray() {
		return toLongArray(order);
	}

	public long[] toLongArray(final Order order) {
		final long[] lshape = new long[shape.length];
		if (order.equals(this.order)) {
			Arrays.setAll(lshape, i -> shape[i]);
		}
		else {
			Arrays.setAll(lshape, i -> shape[shape.length - i - 1]);
		}
		return lshape;
	}

	public Shape to(final Order order) {
		if (order.equals(this.order)) {
			return this;
		}
		else {
			return new Shape(order, toIntArray(order));
		}
	}
}
