package org.apposed.appose;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import org.apposed.appose.shm.ndarray.DType;
import org.apposed.appose.shm.ndarray.NDArray;
import org.apposed.appose.shm.ndarray.Shape;

import static org.apposed.appose.shm.ndarray.Shape.Order.F_ORDER;

public class PlaygroundNDArrayGroovy {

	public static void main(String[] args) throws IOException, InterruptedException {

		// create a FLOAT32 NDArray with shape (4,3,2) in F_ORDER
		// respectively (2,3,4) in C_ORDER
		final DType dType = DType.FLOAT32;
		final NDArray ndArray = new NDArray(dType, new Shape(F_ORDER, 4, 3, 2));

		// fill with values 0..23 in flat iteration order
		final FloatBuffer buf = ndArray.buffer().asFloatBuffer();
		final long len = ndArray.shape().numElements();
		for ( int i = 0; i < len; ++i ) {
			buf.put(i, i);
		}

		// pass to groovy
		Environment env = Appose.system();
		try (Service service = env.groovy()) {
			final Map< String, Object > inputs = new HashMap<>();
			inputs.put( "img", ndArray);
			Service.Task task = service.task(PRINT_INPUT, inputs );
			task.waitFor();
			final String result = ( String ) task.outputs.get( "result" );
			System.out.println( "result = \n" + result );
		}
		ndArray.close();
	}

	private static final String PRINT_INPUT = "" + //
		"time = 100\n" + //
		"return img.toString()\n";

}
