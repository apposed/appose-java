package org.apposed.appose;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.apposed.appose.NDArray.Shape.Order.F_ORDER;

public class NDArrayExamplePython {

	public static void main(String[] args) throws Exception {

		// create a FLOAT32 NDArray with shape (4,3,2) in F_ORDER
		// respectively (2,3,4) in C_ORDER
		final NDArray.DType dType = NDArray.DType.FLOAT32;
		final NDArray ndArray = new NDArray(dType, new NDArray.Shape(F_ORDER, 4, 3, 2));

		// fill with values 0..23 in flat iteration order
		final FloatBuffer buf = ndArray.buffer().asFloatBuffer();
		final long len = ndArray.shape().numElements();
		for ( int i = 0; i < len; ++i ) {
			buf.put(i, i);
		}

		// pass to python (will be wrapped as numpy ndarray
		final Environment env = Appose.base( "/opt/homebrew/Caskroom/miniforge/base/envs/appose/" ).build();
		try ( Service service = env.python() ) {
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
		"import numpy as np\n" + //
		"task.outputs['result'] = str(img.ndarray())";

}
