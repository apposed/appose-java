package org.apposed.appose;

/**
 * Enumerates possible data type of {@link NDArray} elements.
 */
public enum DType {
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
