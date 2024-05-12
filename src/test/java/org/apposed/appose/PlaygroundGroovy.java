package org.apposed.appose;

public class PlaygroundGroovy {

	public static void main(String[] args) {
		String line = "{\"task\":\"a8ed8490-88ac-4297-9732-6a86547948d6\",\"requestType\":\"EXECUTE\",\"inputs\":{\"img\":{\"appose_type\":\"ndarray\",\"shm\":{\"appose_type\":\"shm\",\"name\":\"shm-8a264e92-41a8-4575-b518-6\",\"size\":96},\"dtype\":\"float32\",\"shape\":[2,3,4]}},\"script\":\"time = 100\\nreturn img.toString()\\n\"}";
		System.out.println(line);

		Types.decode(line);
	}
}
