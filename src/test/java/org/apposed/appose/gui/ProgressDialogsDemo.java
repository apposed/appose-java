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

import org.apposed.appose.Appose;
import org.apposed.appose.Environment;
import org.apposed.appose.builder.BuildException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.GridLayout;

/**
 * Interactive demo of the Appose GUI progress components.
 * <p>
 * This demo shows:
 * </p>
 * <ul>
 *     <li>Modal build dialog (blocks until complete)</li>
 *     <li>Async build dialog (non-blocking)</li>
 *     <li>Custom panel embedding</li>
 * </ul>
 *
 * @author Curtis Rueden
 * @author Claude Code
 */
public class ProgressDialogsDemo {

	public static void main(String[] args) {
		// Set system look and feel
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		SwingUtilities.invokeLater(() -> {
			JFrame frame = new JFrame("Appose GUI Demo");
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

			JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
			JLabel titleLabel = new JLabel("<html><h2>Appose GUI Components Demo</h2>" +
					"<p>Click buttons to test different build dialog modes.</p></html>");
			mainPanel.add(titleLabel, BorderLayout.NORTH);

			JPanel buttonPanel = new JPanel(new GridLayout(3, 1, 10, 10));

			// Button 1: Modal dialog
			JButton modalButton = new JButton("Modal Build Dialog");
			modalButton.addActionListener(e -> {
				try {
					Environment env = ProgressDialogs.showBuildDialog(
							frame,
							"Building Test Environment",
							Appose.uv()
									.include("cowsay")
									.name("test-modal-env")
					);
					System.out.println("Build succeeded: " + env.base());
				}
				catch (BuildException ex) {
					System.err.println("Build failed: " + ex.getMessage());
					ex.printStackTrace();
				}
			});
			buttonPanel.add(modalButton);

			// Button 2: Async dialog
			JButton asyncButton = new JButton("Async Build Dialog");
			asyncButton.addActionListener(e -> {
				ProgressDialogs.showBuildDialogAsync(
						frame,
						"Building Test Environment (Async)",
						Appose.uv()
								.include("cowsay")
								.name("test-async-env"),
						env -> System.out.println("Async build succeeded: " + env.base())
				);
			});
			buttonPanel.add(asyncButton);

			// Button 3: Custom panel demo
			JButton customButton = new JButton("Custom Panel Demo");
			customButton.addActionListener(e -> showCustomPanelDemo(frame));
			buttonPanel.add(customButton);

			mainPanel.add(buttonPanel, BorderLayout.CENTER);

			JLabel infoLabel = new JLabel("<html><p style='margin: 10px;'>" +
					"<b>Note:</b> These demos build real environments. " +
					"The first build may take a few minutes.</p></html>");
			mainPanel.add(infoLabel, BorderLayout.SOUTH);

			frame.setContentPane(mainPanel);
			frame.pack();
			frame.setSize(500, 300);
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);
		});
	}

	private static void showCustomPanelDemo(JFrame parent) {
		JFrame customFrame = new JFrame("Custom Panel Demo");
		customFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		JPanel mainPanel = new JPanel(new BorderLayout(10, 10));

		BuildProgressPanel progressPanel = new BuildProgressPanel(700, 400);
		mainPanel.add(progressPanel, BorderLayout.CENTER);

		BuilderProgressAdapter adapter = new BuilderProgressAdapter(progressPanel);

		JButton buildButton = new JButton("Start Build");
		buildButton.addActionListener(e -> {
			buildButton.setEnabled(false);
			adapter.reset();

			// Build in background
			new Thread(() -> {
				try {
					Environment env = Appose.uv()
							.include("cowsay")
							.name("test-custom-env")
							.subscribeProgress(adapter::updateProgress)
							.subscribeOutput(adapter::appendOutput)
							.subscribeError(adapter::appendError)
							.build();

					adapter.setCompleted("Build completed successfully!");
					SwingUtilities.invokeLater(() -> buildButton.setEnabled(true));

					System.out.println("Custom panel build succeeded: " + env.base());
				}
				catch (BuildException ex) {
					adapter.setCompleted("Build failed: " + ex.getMessage());
					SwingUtilities.invokeLater(() -> {
						progressPanel.appendText("\n\nERROR: " + ex.getMessage());
						buildButton.setEnabled(true);
					});
					ex.printStackTrace();
				}
			}).start();
		});

		JPanel bottomPanel = new JPanel(new BorderLayout());
		bottomPanel.add(buildButton, BorderLayout.EAST);
		mainPanel.add(bottomPanel, BorderLayout.SOUTH);

		customFrame.setContentPane(mainPanel);
		customFrame.pack();
		customFrame.setLocationRelativeTo(parent);
		customFrame.setVisible(true);
	}
}
