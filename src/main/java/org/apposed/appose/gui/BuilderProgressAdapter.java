/*-
 * #%L
 * Appose: multi-language interprocess cooperation with shared memory.
 * %%
 * Copyright (C) 2023 - 2025 Appose developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package org.apposed.appose.gui;

import javax.swing.SwingUtilities;

/**
 * Bridges Builder subscription callbacks to a {@link BuildProgressPanel}
 * with proper Event Dispatch Thread (EDT) synchronization.
 * <p>
 * This adapter ensures all UI updates happen on the EDT, making it safe
 * to use Builder callbacks (which may be called from background threads)
 * directly with Swing components.
 * </p>
 * <p>
 * Example usage:
 * </p>
 * <pre>
 * BuildProgressPanel panel = new BuildProgressPanel();
 * BuilderProgressAdapter adapter = new BuilderProgressAdapter(panel);
 *
 * Environment env = Appose.pixi()
 *     .subscribeProgress(adapter::updateProgress)
 *     .subscribeOutput(adapter::appendOutput)
 *     .subscribeError(adapter::appendError)
 *     .build();
 * </pre>
 *
 * @author Curtis Rueden
 * @author Claude Code
 */
public class BuilderProgressAdapter {

	private final BuildProgressPanel panel;

	/**
	 * Creates a new adapter that updates the given panel.
	 *
	 * @param panel The panel to update
	 */
	public BuilderProgressAdapter(BuildProgressPanel panel) {
		this.panel = panel;
	}

	/**
	 * Updates progress information.
	 * <p>
	 * This method can be called from any thread - updates are automatically
	 * dispatched to the EDT.
	 * </p>
	 * <p>
	 * Intended for use with {@link org.apposed.appose.Builder#subscribeProgress(org.apposed.appose.Builder.ProgressConsumer)}.
	 * </p>
	 *
	 * @param title Progress title/status message
	 * @param current Current progress value
	 * @param maximum Maximum progress value (0 for indeterminate)
	 */
	public void updateProgress(String title, long current, long maximum) {
		SwingUtilities.invokeLater(() -> {
			if (title != null && !title.isEmpty()) {
				panel.setStatus(title);
			}
			panel.setProgress(current, maximum);
		});
	}

	/**
	 * Appends output text to the panel.
	 * <p>
	 * This method can be called from any thread - updates are automatically
	 * dispatched to the EDT.
	 * </p>
	 * <p>
	 * Intended for use with {@link org.apposed.appose.Builder#subscribeOutput(java.util.function.Consumer)}.
	 * </p>
	 *
	 * @param text Output text to append
	 */
	public void appendOutput(String text) {
		if (text == null || text.isEmpty()) return;
		SwingUtilities.invokeLater(() -> panel.appendText(text));
	}

	/**
	 * Appends error text to the panel.
	 * <p>
	 * This method can be called from any thread - updates are automatically
	 * dispatched to the EDT.
	 * </p>
	 * <p>
	 * Intended for use with {@link org.apposed.appose.Builder#subscribeError(java.util.function.Consumer)}.
	 * </p>
	 *
	 * @param text Error text to append
	 */
	public void appendError(String text) {
		if (text == null || text.isEmpty()) return;
		SwingUtilities.invokeLater(() -> panel.appendText("[ERROR] " + text));
	}

	/**
	 * Marks the build as complete and stops progress bar animation.
	 * <p>
	 * This method can be called from any thread - updates are automatically
	 * dispatched to the EDT.
	 * </p>
	 *
	 * @param status Status message to display (e.g., "Build completed successfully")
	 */
	public void setCompleted(String status) {
		SwingUtilities.invokeLater(() -> {
			panel.setStatus(status);
			panel.setCompleted();
		});
	}

	/**
	 * Resets the panel to its initial "ready" state.
	 * <p>
	 * This method can be called from any thread - updates are automatically
	 * dispatched to the EDT.
	 * </p>
	 */
	public void reset() {
		SwingUtilities.invokeLater(() -> panel.reset());
	}

	/**
	 * Gets the underlying panel.
	 *
	 * @return The BuildProgressPanel
	 */
	public BuildProgressPanel getPanel() {
		return panel;
	}
}
