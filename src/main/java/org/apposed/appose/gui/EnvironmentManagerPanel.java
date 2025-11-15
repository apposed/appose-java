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

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A panel for managing multiple Appose environments.
 * <p>
 * This component displays a list of {@link EnvironmentPanel}s with:
 * </p>
 * <ul>
 *     <li>A title</li>
 *     <li>Individual environment panels</li>
 *     <li>Refresh All button</li>
 * </ul>
 * <p>
 * Based on work by Stefan Hahmann for Mastodon Deep Lineage.
 * </p>
 * <p>
 * Example usage:
 * </p>
 * <pre>
 * EnvironmentDescriptor[] envs = {
 *     new EnvironmentDescriptor("Env 1", Appose.uv().include("numpy").name("env1")),
 *     new EnvironmentDescriptor("Env 2", Appose.pixi().conda("python").name("env2"))
 * };
 * EnvironmentManagerPanel panel = new EnvironmentManagerPanel(
 *     "My Environments",
 *     envs
 * );
 * myFrame.add(panel);
 * </pre>
 *
 * @author Curtis Rueden
 * @author Stefan Hahmann
 */
public class EnvironmentManagerPanel extends JPanel {

	private final List<EnvironmentPanel> environmentPanels;
	private final JButton refreshButton;

	/**
	 * Creates an environment manager panel.
	 *
	 * @param title Title to display at the top
	 * @param descriptors Environment descriptors to manage
	 */
	public EnvironmentManagerPanel(String title, EnvironmentDescriptor... descriptors) {
		this(title, Arrays.asList(descriptors));
	}

	/**
	 * Creates an environment manager panel.
	 *
	 * @param title Title to display at the top
	 * @param descriptors Environment descriptors to manage
	 */
	public EnvironmentManagerPanel(String title, List<EnvironmentDescriptor> descriptors) {
		super(new BorderLayout(10, 10));

		// Create environment panels
		environmentPanels = new ArrayList<>();
		for (EnvironmentDescriptor desc : descriptors) {
			environmentPanels.add(new EnvironmentPanel(desc));
		}

		// Create title
		JLabel titleLabel = new JLabel(title != null ? title : "Environment Manager");
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));

		// Create refresh button
		refreshButton = new JButton("Refresh All");
		refreshButton.addActionListener(e -> refreshAll());

		// Layout: title at top
		JPanel topPanel = new JPanel(new BorderLayout());
		topPanel.add(titleLabel, BorderLayout.WEST);
		topPanel.add(refreshButton, BorderLayout.EAST);
		add(topPanel, BorderLayout.NORTH);

		// Environment panels in the center
		JPanel envPanel = new JPanel(new GridLayout(0, 1, 10, 10));
		environmentPanels.forEach(envPanel::add);
		add(envPanel, BorderLayout.CENTER);
	}

	/**
	 * Refreshes the status of all managed environments.
	 * <p>
	 * This runs asynchronously in a background thread.
	 * </p>
	 */
	public void refreshAll() {
		refreshButton.setEnabled(false);
		refreshButton.setText("Refreshing...");

		SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() {
				environmentPanels.forEach(EnvironmentPanel::refresh);
				return null;
			}

			@Override
			protected void done() {
				refreshButton.setEnabled(true);
				refreshButton.setText("Refresh All");
			}
		};
		worker.execute();
	}

	/**
	 * Gets the list of environment panels being managed.
	 *
	 * @return List of environment panels
	 */
	public List<EnvironmentPanel> getEnvironmentPanels() {
		return new ArrayList<>(environmentPanels);
	}
}
