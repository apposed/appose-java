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

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * A reusable panel for displaying environment build progress.
 * <p>
 * This panel provides:
 * </p>
 * <ul>
 *     <li>A status label showing the current operation</li>
 *     <li>A progress bar (determinate when max > 0, indeterminate otherwise)</li>
 *     <li>A scrollable text area for build output and error messages</li>
 * </ul>
 * <p>
 * This component is designed to be embedded in dialogs or other UI containers.
 * It does <strong>not</strong> handle threading - callers should use
 * {@link BuilderProgressAdapter} to bridge Builder callbacks to this panel
 * with proper EDT synchronization.
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
public class BuildProgressPanel extends JPanel {

	private final JLabel statusLabel;
	private final JProgressBar progressBar;
	private final JTextArea outputArea;
	private final JScrollPane scrollPane;

	/**
	 * Creates a new build progress panel with default settings.
	 */
	public BuildProgressPanel() {
		this(600, 400);
	}

	/**
	 * Creates a new build progress panel with specified dimensions.
	 *
	 * @param width Preferred width in pixels
	 * @param height Preferred height in pixels
	 */
	public BuildProgressPanel(int width, int height) {
		super(new BorderLayout(5, 5));

		// Status label at the top
		statusLabel = new JLabel("Ready");
		statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		// Progress bar in the middle
		progressBar = new JProgressBar();
		progressBar.setIndeterminate(false);
		progressBar.setStringPainted(true);
		progressBar.setValue(0);

		// Output text area at the bottom
		outputArea = new JTextArea();
		outputArea.setEditable(false);
		outputArea.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
		outputArea.setLineWrap(false);
		outputArea.setWrapStyleWord(false);

		scrollPane = new JScrollPane(outputArea);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		scrollPane.setPreferredSize(new Dimension(width, height));

		// Assemble the panel
		JPanel topPanel = new JPanel(new BorderLayout());
		topPanel.add(statusLabel, BorderLayout.NORTH);
		topPanel.add(progressBar, BorderLayout.CENTER);

		add(topPanel, BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);

		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
	}

	/**
	 * Updates the status message.
	 * <p>
	 * <strong>Thread safety:</strong> This method should be called on the EDT.
	 * Use {@link BuilderProgressAdapter} for automatic EDT dispatching.
	 * </p>
	 *
	 * @param status The status message to display
	 */
	public void setStatus(String status) {
		statusLabel.setText(status);
	}

	/**
	 * Updates the progress bar.
	 * <p>
	 * If {@code max} is 0 or negative, the progress bar becomes indeterminate.
	 * Otherwise, it shows determinate progress from 0 to {@code max}.
	 * </p>
	 * <p>
	 * <strong>Thread safety:</strong> This method should be called on the EDT.
	 * Use {@link BuilderProgressAdapter} for automatic EDT dispatching.
	 * </p>
	 *
	 * @param current Current progress value
	 * @param max Maximum progress value (0 or negative for indeterminate)
	 */
	public void setProgress(long current, long max) {
		if (max <= 0) {
			if (!progressBar.isIndeterminate()) {
				progressBar.setIndeterminate(true);
			}
			progressBar.setString(null);
		}
		else {
			if (progressBar.isIndeterminate()) {
				progressBar.setIndeterminate(false);
			}
			progressBar.setMaximum((int) Math.min(max, Integer.MAX_VALUE));
			progressBar.setValue((int) Math.min(current, Integer.MAX_VALUE));
			progressBar.setString(current + " / " + max);
		}
	}

	/**
	 * Appends text to the output area.
	 * <p>
	 * The text area automatically scrolls to show the latest output.
	 * </p>
	 * <p>
	 * <strong>Thread safety:</strong> This method should be called on the EDT.
	 * Use {@link BuilderProgressAdapter} for automatic EDT dispatching.
	 * </p>
	 *
	 * @param text Text to append
	 */
	public void appendText(String text) {
		outputArea.append(text);
		// Auto-scroll to bottom
		outputArea.setCaretPosition(outputArea.getDocument().getLength());
	}

	/**
	 * Clears all text from the output area.
	 * <p>
	 * <strong>Thread safety:</strong> This method should be called on the EDT.
	 * </p>
	 */
	public void clearText() {
		outputArea.setText("");
	}

	/**
	 * Resets the panel to its initial "ready" state.
	 * <p>
	 * This clears all text, resets the status to "Ready", and sets the
	 * progress bar to a non-animating state at 0%.
	 * </p>
	 * <p>
	 * <strong>Thread safety:</strong> This method should be called on the EDT.
	 * </p>
	 */
	public void reset() {
		statusLabel.setText("Ready");
		progressBar.setIndeterminate(false);
		progressBar.setValue(0);
		progressBar.setString(null);
		outputArea.setText("");
	}

	/**
	 * Marks the build as complete and stops progress bar animation.
	 * <p>
	 * Sets the progress bar to 100% (determinate, non-animating).
	 * The status message is not changed - caller should set it appropriately
	 * (e.g., "Build completed successfully" or "Build failed").
	 * </p>
	 * <p>
	 * <strong>Thread safety:</strong> This method should be called on the EDT.
	 * </p>
	 */
	public void setCompleted() {
		progressBar.setIndeterminate(false);
		progressBar.setValue(progressBar.getMaximum());
		progressBar.setString("Complete");
	}

	/**
	 * Gets the underlying text area component.
	 * <p>
	 * This allows advanced customization of the output display.
	 * </p>
	 *
	 * @return The JTextArea component
	 */
	public JTextArea getOutputArea() {
		return outputArea;
	}

	/**
	 * Gets the progress bar component.
	 * <p>
	 * This allows advanced customization of the progress display.
	 * </p>
	 *
	 * @return The JProgressBar component
	 */
	public JProgressBar getProgressBar() {
		return progressBar;
	}

	/**
	 * Gets the status label component.
	 * <p>
	 * This allows advanced customization of the status display.
	 * </p>
	 *
	 * @return The JLabel component
	 */
	public JLabel getStatusLabel() {
		return statusLabel;
	}
}
