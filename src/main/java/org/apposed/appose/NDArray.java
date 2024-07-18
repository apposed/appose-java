/*-
 * #%L
 * Appose: multi-language interprocess cooperation with shared memory.
 * %%
 * Copyright (C) 2023 - 2024 Appose developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package org.apposed.appose;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Represents a multidimensional array similar to a NumPy ndarray.
 * <p>
 * The array contains elements of a {@link DType data type}, arranged in a
 * particular {@link Shape}, and flattened into {@link SharedMemory},
 */
public class NDArray implements AutoCloseable {

	/**
	 * shared memory containing the flattened array data.
	 */
	private final SharedMemory shm;

	/**
	 * data type of the array elements.
	 */
	private final DType dType;

	/**
	 * shape of the array.
	 */
	private final Shape shape;

	/**
	 * Constructs an {@code NDArray} with the specified data type, shape,
	 * and {@code SharedMemory}.
	 *
	 * @param dType element data type
	 * @param shape array shape
	 * @param shm the flattened array data.
	 */
	public NDArray(final DType dType, final Shape shape, final SharedMemory shm) {
		this.dType = dType;
		this.shape = shape;
		this.shm = shm == null
			? SharedMemory.create(null, safeInt(shape.numElements() * dType.bytesPerElement()))
			: shm;
	}

	/**
	 * Constructs an {@code NDArray} with the specified data type and shape,
	 * allocating a new {@code SharedMemory} block.
	 *
	 * @param dType element data type
	 * @param shape array shape
	 */
	public NDArray(final DType dType, final Shape shape) {
		this(dType, shape, null);
	}

	/**
	 * @return The data type of the array elements.
	 */
	public DType dType() {
		return dType;
	}

	/**
	 * @return The shape of the array.
	 */
	public Shape shape() {
		return shape;
	}

	/**
	 * @return The shared memory block containing the array data.
	 */
	public SharedMemory shm() {
		return shm;
	}

	/**
	 * Returns a ByteBuffer view of the array data.
	 *
	 * @return A ByteBuffer view of {@link #shm()}.
	 */
	public ByteBuffer buffer() {
		final long length = shape.numElements() * dType.bytesPerElement();
		return shm.pointer().getByteBuffer(0, length);
	}

	/**
	 * Release resources ({@code SharedMemory}) associated with this {@code NDArray}.
	 */
	@Override
	public void close() {
		shm.close();
	}

	@Override
	public String toString() {
		return "NDArray(" +
			"dType=" + dType +
			", shape=" + shape +
			", shm=" + shm +
			")";
	}

	/**
	 * Cast {@code long} to {@code int}.
	 *
	 * @throws IllegalArgumentException if the value is too large to fit in an integer.
	 */
	private static int safeInt(final long value) {
		if (value > Integer.MAX_VALUE) throw new IllegalArgumentException("value too large");
		return (int) value;
	}

	/**
	 * Enumerates possible data type of {@link NDArray} elements.
	 */
	public static enum DType {
		INT8("int8", Byte.BYTES), //
		INT16("int16", Short.BYTES), //
		INT32("int32", Integer.BYTES), //
		INT64("int64", Long.BYTES), //
		UINT8("uint8", Byte.BYTES), //
		UINT16("uint16", Short.BYTES), //
		UINT32("uint32", Integer.BYTES), //
		UINT64("uint64", Long.BYTES), //
		FLOAT32("float32", Float.BYTES), //
		FLOAT64("float64", Double.BYTES), //
		COMPLEX64("complex64", Float.BYTES * 2), //
		COMPLEX128("complex128", Double.BYTES * 2), //
		BOOL("bool", 1);

		private final String label;

		private final int bytesPerElement;

		DType(final String label, final int bytesPerElement)
		{
			this.label = label;
			this.bytesPerElement = bytesPerElement;
		}

		/**
		 * Get the number of bytes per element for this data type.
		 */
		public int bytesPerElement()
		{
			return bytesPerElement;
		}

		/**
		 * Get the label of this {@code DType}.
		 * <p>
		 * The label can used as a {@code dtype} in Python. It is also used for JSON
		 * serialization.
		 *
		 * @return the label.
		 */
		public String label()
		{
			return label;
		}

		/**
		 * Returns the {@code DType} corresponding to the given {@code label}.
		 *
		 * @param label a label.
		 * @return {@code DType} corresponding to {@code label}.
		 * @throws IllegalArgumentException if no {@code DType} corresponds to the given label.
		 */
		public static DType fromLabel(final String label) throws IllegalArgumentException
		{
			return valueOf( label.toUpperCase() );
		}
	}

	/**
	 * The shape of a multi-dimensional array.
	 */
	public static class Shape {

		/**
		 * The order of the array elements, where
		 * <ul>
		 *     <li>{@code C_ORDER} means fastest-moving dimension first (as in NumPy)</li>
		 *     <li>{@code F_ORDER} means fastest-moving dimension last (as in ImgLib2)</li>
		 * </ul>
		 * See <a href="https://github.com/bogovicj/JaneliaDataStandards/blob/arrayOrder/ArrayOrder.md">ArrayOrder</a>
		 */
		public enum Order {C_ORDER, F_ORDER}

		/**
		 * native order
		 */
		private final Order order;

		/**
		 * dimensions along each axis, arranged in the native order.
		 */
		private final int[] shape;

		/**
		 * Construct a {@code Shape} with the specified order and dimensions.
		 *
		 * @param order order of the axes.
		 * @param shape size along each axis.
		 */
		public Shape(final Order order, final int... shape) {
			this.order = order;
			this.shape = shape;
		}

		/**
		 * Get the size along at the specified dimension (in the native {@link
		 * #order()} of this {@code Shape})
		 *
		 * @param d axis index
		 * @return size along dimension {@code d}.
		 */
		public int get(final int d) {
			return shape[d];
		}

		/**
		 * Get the number of dimensions.
		 */
		public int length() {
			return shape.length;
		}

		/**
		 * Get the native order of this {@code Shape}, that is the order in
		 * which axes are arranged when accessed through {@link #get(int)},
		 * {@link #toIntArray()}, {@link #toLongArray()}.
		 */
		public Order order() {
			return order;
		}

		/**
		 * Get the total number of elements in the shape.
		 */
		public long numElements() {
			long n = 1;
			for (int s : shape) {
				n *= s;
			}
			return n;
		}

		/**
		 * Get the shape dimensions as an array in the native {@link #order()}
		 * of this {@code Shape}.
		 *
		 * @return dimensions array
		 */
		public int[] toIntArray() {
			return shape;
		}

		/**
		 * Get the shape dimensions as an array in the specified order.
		 *
		 * @return dimensions array
		 */
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

		/**
		 * Get the shape dimensions as an array in the native {@link #order()}
		 * of this {@code Shape}.
		 *
		 * @return dimensions array
		 */
		public long[] toLongArray() {
			return toLongArray(order);
		}

		/**
		 * Get the shape dimensions as an array in the specified order.
		 *
		 * @return dimensions array
		 */
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

		/**
		 * Returns representation of this {@code Shape} with the given native {@code order}.
		 */
		public Shape to(final Order order) {
			if (order.equals(this.order)) {
				return this;
			}
			else {
				return new Shape(order, toIntArray(order));
			}
		}

		/**
		 * Returns a string representation of this Shape, including its order and dimensions.
		 *
		 * @return A string representation of this Shape.
		 */
		@Override
		public String toString() {
			return "Shape{" +
					"order=" + order +
					", shape=" + Arrays.toString(shape) +
					'}';
		}
	}
}
