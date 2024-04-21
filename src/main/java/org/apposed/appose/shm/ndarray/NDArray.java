package org.apposed.appose.shm.ndarray;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.apposed.appose.shm.SharedMemoryArray;

public class NDArray implements Closeable {

	private final SharedMemoryArray sharedMemoryArray;
	private final DType dType;
	private final Shape shape;

	public NDArray(final SharedMemoryArray sharedMemoryArray, final DType dType, final Shape shape) {
		this.sharedMemoryArray = sharedMemoryArray;
		this.dType = dType;
		this.shape = shape;
	}

	public NDArray(final DType dType, final Shape shape) {
		this(SharedMemoryArray.create(safeInt(shape.numElements() * dType.bytesPerElement())), dType, shape);
	}

	public DType dType() {
		return dType;
	}

	public Shape shape() {
		return shape;
	}

	public SharedMemoryArray shm() {
		return sharedMemoryArray;
	}

	public ByteBuffer buffer() {
		final long length = shape.numElements() * dType.bytesPerElement();
		return sharedMemoryArray.getPointer().getByteBuffer(0, length);
	}

	@Override
	public void close() throws IOException {
		sharedMemoryArray.close(); /* TODO reference counting or check for existence */
	}

	private static int safeInt(final long value) {
		if (value > Integer.MAX_VALUE) throw new IllegalArgumentException("value too large");
		return (int) value;
	}
}
