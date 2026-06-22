package org.apposed.appose.util;

import java.util.function.Consumer;

import org.apposed.appose.Builder.ProgressConsumer;
import org.apposed.appose.TaskEvent;

/**
 * Utility interface for classes that provide listeners to Appose events.
 */
public interface ApposeListener
{


	/**
	 * Returns a consumer that will be called with task events related to the
	 * execution of an Appose task, and that writes messages in the outputs
	 * defined in this class.
	 * 
	 * @return a new task event consumer.
	 */
	Consumer< TaskEvent > taskListener();

	/**
	 * Returns a consumer that will be called with output messages related to
	 * the downloading, installation and deployment of an Appose environment,
	 * and that writes messages in the outputs defined in this class.
	 * 
	 * @return a new output message consumer.
	 */
	Consumer< String > outputListener();

	/**
	 * Returns a consumer that will be called with error messages related to the
	 * the downloading, installation and deployment of an Appose environment,
	 * and that writes messages in the outputs defined in this class.
	 * 
	 * @return a new error message consumer.
	 */
	Consumer< String > errorListener();

	/**
	 * Returns a consumer that will be called with progress updates related to
	 * the downloading, installation and deployment of an Appose environment,
	 * and that writes messages in the outputs defined in this class.
	 * 
	 * @return a new progress update consumer.
	 */
	ProgressConsumer progressListener();

	/**
	 * Closes the listener and releases any resources it may be holding. This
	 * method should be called 1/ after the the downloading, installation and
	 * deployment phase and 2/ after the execution of an Appose task is
	 * completed.
	 */
	void close();

}
