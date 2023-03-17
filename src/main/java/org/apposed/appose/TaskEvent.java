package org.apposed.appose;

import org.apposed.appose.Service.ResponseType;

public class TaskEvent {

	public final Service.Task task;
	public final ResponseType responseType;

	public TaskEvent(Service.Task task, ResponseType responseType) {
		this.task = task;
		this.responseType = responseType;
	}
}
