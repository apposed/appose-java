package org.apposed.appose;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.apposed.appose.NDArray.Shape.Order.F_ORDER;

public class NDArrayExampleGroovy {

	public static void main(String[] args) throws Exception {

		// create a FLOAT32 NDArray with shape (4,3,2) in F_ORDER
		// respectively (2,3,4) in C_ORDER
		final NDArray.DType dType = NDArray.DType.FLOAT32;
		final NDArray.Shape shape = new NDArray.Shape(F_ORDER, 4, 3, 2);
		final NDArray ndArray = new NDArray(dType, shape);

		// fill with values 0..23 in flat iteration order
		final FloatBuffer buf = ndArray.buffer().asFloatBuffer();
		final long len = ndArray.shape().numElements();
		for ( int i = 0; i < len; ++i ) {
			buf.put(i, i);
		}

		System.out.println("ndArray.shm().size() = " + ndArray.shm().size());
		System.out.println("ndArray.shm().name() = " + ndArray.shm().name());
		float v = ndArray.buffer().asFloatBuffer().get(5);
		System.out.println("v = " + v);

		// pass to groovy
		Environment env = Appose.system();
		try (Service service = env.groovy()) {
			final Map< String, Object > inputs = new HashMap<>();
			inputs.put( "img", ndArray);
			Service.Task task = service.task(PRINT_INPUT, inputs );
			task.waitFor();
			System.out.println( "result = " + task.outputs.get("result") );
		}
		ndArray.close();
	}

	private static final String PRINT_INPUT = "" + //
			"return  \"[\" + img.buffer().asFloatBuffer().get(5) + \"]\";\n";

}
