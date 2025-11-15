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

import org.apposed.appose.Builder;
import org.apposed.appose.Environment;
import org.apposed.appose.builder.BuildException;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.awt.Window;
import java.util.function.Consumer;

/**
 * Utility methods for displaying environment build progress in dialogs.
 * <p>
 * This class provides convenient methods for common build dialog patterns,
 * handling SwingWorker boilerplate and EDT synchronization automatically.
 * </p>
 * <p>
 * Example usage:
 * </p>
 * <pre>
 * // Modal dialog - blocks until build completes
 * try {
 *     Environment env = ProgressDialogs.showBuildDialog(
 *         parentFrame,
 *         "Building Environment",
 *         Appose.pixi().conda("python>=3.8", "numpy")
 *     );
 *     // Use env...
 * }
 * catch (BuildException e) {
 *     // Handle build failure
 * }
 *
 * // Async dialog - returns immediately
 * ProgressDialogs.showBuildDialogAsync(
 *     parentFrame,
 *     "Building Environment",
 *     Appose.pixi().conda("python>=3.8", "numpy"),
 *     env -> {
 *         // Success callback - use env
 *     },
 *     error -> {
 *         // Error callback - handle failure
 *     }
 * );
 * </pre>
 *
 * @author Curtis Rueden
 * @author Claude Code
 */
public class ProgressDialogs {

	private ProgressDialogs() {
		// Prevent instantiation
	}

	/**
	 * Shows a modal dialog that builds an environment and blocks until completion.
	 * <p>
	 * This method must be called from the EDT. The build happens in a background
	 * thread, but the dialog is modal and blocks the calling thread until the
	 * build completes.
	 * </p>
	 * <p>
	 * If the build fails, a {@link BuildException} is thrown.
	 * </p>
	 *
	 * @param parent Parent component for the dialog (may be null)
	 * @param title Dialog title
	 * @param builder Builder to execute
	 * @return The built environment
	 * @throws BuildException If the build fails
	 * @throws IllegalStateException If not called from EDT
	 */
	public static Environment showBuildDialog(Component parent, String title, Builder<?> builder)
			throws BuildException {
		if (!SwingUtilities.isEventDispatchThread()) {
			throw new IllegalStateException("showBuildDialog must be called from EDT");
		}

		BuildProgressPanel panel = new BuildProgressPanel();
		BuilderProgressAdapter adapter = new BuilderProgressAdapter(panel);

		// Subscribe to builder events
		builder.subscribeProgress(adapter::updateProgress)
				.subscribeOutput(adapter::appendOutput)
				.subscribeError(adapter::appendError);

		// Create modal dialog with Close button
		Window parentWindow = parent instanceof Window ?
				(Window) parent :
				(parent != null ? SwingUtilities.getWindowAncestor(parent) : null);

		JDialog dialog = new JDialog(parentWindow, title, JDialog.DEFAULT_MODALITY_TYPE);

		// Add panel and Close button
		JPanel contentPanel = new JPanel(new java.awt.BorderLayout());
		contentPanel.add(panel, java.awt.BorderLayout.CENTER);

		JButton closeButton = new JButton("Close");
		closeButton.setEnabled(false); // Disabled during build
		closeButton.addActionListener(e -> dialog.dispose());
		JPanel buttonPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
		buttonPanel.add(closeButton);
		contentPanel.add(buttonPanel, java.awt.BorderLayout.SOUTH);

		dialog.setContentPane(contentPanel);
		dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		dialog.pack();
		dialog.setLocationRelativeTo(parent);

		// Build environment in background
		BuildWorker worker = new BuildWorker(builder, adapter, closeButton);
		worker.execute();

		// Show dialog (blocks until user clicks Close)
		dialog.setVisible(true);

		// Retrieve result or throw exception
		try {
			Environment env = worker.get();
			if (env == null) {
				throw new BuildException(builder, new RuntimeException("Build returned null"));
			}
			return env;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new BuildException(builder, e);
		}
		catch (java.util.concurrent.ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof BuildException) {
				throw (BuildException) cause;
			}
			throw new BuildException(builder, cause);
		}
	}

	/**
	 * Shows a non-modal dialog that builds an environment asynchronously.
	 * <p>
	 * This method can be called from any thread. The dialog is shown non-modally,
	 * and callbacks are invoked when the build completes or fails.
	 * </p>
	 * <p>
	 * Both callbacks are optional (may be null).
	 * </p>
	 *
	 * @param parent Parent component for the dialog (may be null)
	 * @param title Dialog title
	 * @param builder Builder to execute
	 * @param onSuccess Callback invoked with the Environment on success (may be null)
	 * @param onError Callback invoked with the exception on failure (may be null)
	 */
	public static void showBuildDialogAsync(
			Component parent,
			String title,
			Builder<?> builder,
			Consumer<Environment> onSuccess,
			Consumer<Throwable> onError) {

		SwingUtilities.invokeLater(() -> {
			BuildProgressPanel panel = new BuildProgressPanel();
			BuilderProgressAdapter adapter = new BuilderProgressAdapter(panel);

			// Subscribe to builder events
			builder.subscribeProgress(adapter::updateProgress)
					.subscribeOutput(adapter::appendOutput)
					.subscribeError(adapter::appendError);

			// Create non-modal dialog with Close button
			Window parentWindow = parent instanceof Window ?
					(Window) parent :
					(parent != null ? SwingUtilities.getWindowAncestor(parent) : null);

			JDialog dialog = new JDialog(parentWindow, title, JDialog.ModalityType.MODELESS);

			// Add panel and Close button
			JPanel contentPanel = new JPanel(new java.awt.BorderLayout());
			contentPanel.add(panel, java.awt.BorderLayout.CENTER);

			JButton closeButton = new JButton("Close");
			closeButton.addActionListener(e -> dialog.dispose());
			JPanel buttonPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
			buttonPanel.add(closeButton);
			contentPanel.add(buttonPanel, java.awt.BorderLayout.SOUTH);

			dialog.setContentPane(contentPanel);
			dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			dialog.pack();
			dialog.setLocationRelativeTo(parent);

			// Build environment in background
			SwingWorker<Environment, Void> worker = new SwingWorker<Environment, Void>() {
				@Override
				protected Environment doInBackground() throws Exception {
					return builder.build();
				}

				@Override
				protected void done() {
					try {
						Environment env = get();
						adapter.setCompleted("Build completed successfully!");
						if (onSuccess != null) {
							onSuccess.accept(env);
						}
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						adapter.setCompleted("Build interrupted");
						if (onError != null) {
							onError.accept(e);
						}
					}
					catch (java.util.concurrent.ExecutionException e) {
						adapter.setCompleted("Build failed: " + e.getCause().getMessage());
						if (onError != null) {
							onError.accept(e.getCause());
						}
					}
				}
			};

			worker.execute();
			dialog.setVisible(true);
		});
	}

	/**
	 * Shows a non-modal dialog that builds an environment asynchronously
	 * with only a success callback.
	 * <p>
	 * If the build fails, an error dialog is shown to the user.
	 * </p>
	 *
	 * @param parent Parent component for the dialog (may be null)
	 * @param title Dialog title
	 * @param builder Builder to execute
	 * @param onSuccess Callback invoked with the Environment on success
	 */
	public static void showBuildDialogAsync(
			Component parent,
			String title,
			Builder<?> builder,
			Consumer<Environment> onSuccess) {

		showBuildDialogAsync(parent, title, builder, onSuccess, error -> {
			SwingUtilities.invokeLater(() -> {
				String message = "Build failed: " + error.getMessage();
				JOptionPane.showMessageDialog(
						parent,
						message,
						"Build Error",
						JOptionPane.ERROR_MESSAGE
				);
			});
		});
	}

	/**
	 * SwingWorker that builds an environment and enables the Close button when done.
	 */
	private static class BuildWorker extends SwingWorker<Environment, Void> {
		private final Builder<?> builder;
		private final BuilderProgressAdapter adapter;
		private final JButton closeButton;

		BuildWorker(Builder<?> builder, BuilderProgressAdapter adapter, JButton closeButton) {
			this.builder = builder;
			this.adapter = adapter;
			this.closeButton = closeButton;
		}

		@Override
		protected Environment doInBackground() throws Exception {
			return builder.build();
		}

		@Override
		protected void done() {
			try {
				get(); // Check for exceptions
				adapter.setCompleted("Build completed successfully!");
			}
			catch (Exception e) {
				adapter.setCompleted("Build failed: " + e.getMessage());
			}
			closeButton.setEnabled(true);
		}
	}
}
