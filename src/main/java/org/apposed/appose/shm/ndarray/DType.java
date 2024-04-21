package org.apposed.appose.shm.ndarray;

public enum DType
{
	INT8( "int8", Byte.BYTES ),
	INT16( "int16", Short.BYTES ),
	INT32( "int32", Integer.BYTES ),
	INT64( "int64", Long.BYTES ),
	UINT8( "uint8", Byte.BYTES ),
	UINT16( "uint16", Short.BYTES ),
	UINT32( "uint32", Integer.BYTES ),
	UINT64( "uint64", Long.BYTES ),
	FLOAT32( "float32", Float.BYTES ),
	FLOAT64( "float64", Double.BYTES ),
	COMPLEX64( "complex64", Float.BYTES * 2 ),
	COMPLEX128( "complex128", Double.BYTES * 2 ),
	BOOL( "bool", 1 );

	private final String label;

	private final int bytesPerElement;

	DType( final String label, final int bytesPerElement )
	{
		this.label = label;
		this.bytesPerElement = bytesPerElement;
	}

	public String label()
	{
		return label;
	}

	public int bytesPerElement()
	{
		return bytesPerElement;
	}

	// --- maybe ? --

	public String json()
	{
		return label();
	}

	public static DType fromJson( final String json ) throws IllegalArgumentException
	{
//		return valueOf( json.toUpperCase() );
		switch ( json )
		{
		case "int8":
			return INT8;
		case "int16":
			return INT16;
		case "int32":
			return INT32;
		case "int64":
			return INT64;
		case "uint8":
			return UINT8;
		case "uint16":
			return UINT16;
		case "uint32":
			return UINT32;
		case "uint64":
			return UINT64;
		case "float32":
			return FLOAT32;
		case "float64":
			return FLOAT64;
		case "complex64":
			return COMPLEX64;
		case "complex128":
			return COMPLEX128;
		case "bool":
			return BOOL;
		default:
			throw new IllegalArgumentException();
		}
	}
}
