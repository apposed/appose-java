package org.apposed.appose;

import java.util.Map;

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;

public final class Types {

	private Types() {
		// NB: Prevent instantiation of utility class.
	}

	public static String encode(Map<?, ?> data) {
		return JsonOutput.toJson(data);
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> decode(String json) {
		return (Map<String, Object>) new JsonSlurper().parseText(json);
	}

}
