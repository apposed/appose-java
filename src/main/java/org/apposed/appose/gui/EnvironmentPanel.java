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

import org.apposed.appose.builder.BuildException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.io.File;
import java.util.function.Consumer;

/**
 * A panel for displaying and managing a single Appose environment.
 * <p>
 * This component displays:
 * </p>
 * <ul>
 *     <li>Environment name and status (installed/not installed)</li>
 *     <li>Environment path and disk size</li>
 *     <li>Install/Update and Delete buttons</li>
 *     <li>Status icon (checkmark or X)</li>
 * </ul>
 * <p>
 * Based on work by Stefan Hahmann for Mastodon Deep Lineage.
 * </p>
 * <p>
 * Example usage:
 * </p>
 * <pre>
 * EnvironmentDescriptor desc = new EnvironmentDescriptor(
 *     "My Environment",
 *     "Python with numpy",
 *     Appose.uv().include("numpy").name("my-env")
 * );
 * EnvironmentPanel panel = new EnvironmentPanel(desc);
 * myFrame.add(panel);
 * </pre>
 *
 * @author Curtis Rueden
 * @author Stefan Hahmann
 */
public class EnvironmentPanel extends JPanel {

	public static final Color COLOR_INSTALLED = new Color(0, 128, 0);
	public static final Color COLOR_NOT_INSTALLED = new Color(180, 0, 0);

	private final EnvironmentDescriptor descriptor;
	private final JLabel statusIcon;
	private final JLabel statusLabel;
	private final JButton installButton;
	private final JButton deleteButton;
	private final JLabel sizeLabel;

	/**
	 * Creates a new environment panel.
	 *
	 * @param descriptor Environment descriptor
	 */
	public EnvironmentPanel(EnvironmentDescriptor descriptor) {
		super(new GridBagLayout());
		this.descriptor = descriptor;

		// Create components
		statusIcon = new JLabel();
		statusIcon.setPreferredSize(new Dimension(24, 24));

		statusLabel = new JLabel();
		statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));

		installButton = new JButton();
		deleteButton = new JButton("Delete");
		sizeLabel = new JLabel();

		// Set up layout
		initLayout();

		// Set up behavior
		installButton.addActionListener(e -> installOrUpdate());
		deleteButton.addActionListener(e -> delete());

		// Initial status check
		refresh();
	}

	private void initLayout() {
		TitledBorder border = BorderFactory.createTitledBorder(descriptor.getName());
		border.setTitleFont(border.getTitleFont().deriveFont(Font.BOLD));
		setBorder(border);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(5, 5, 5, 5);
		gbc.fill = GridBagConstraints.HORIZONTAL;

		// Row 1: Status icon, status label, spacer, buttons
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 0;
		add(statusIcon, gbc);

		gbc.gridx = 1;
		gbc.weightx = 0;
		add(statusLabel, gbc);

		gbc.gridx = 2;
		gbc.weightx = 1.0;
		add(new JLabel(), gbc); // Spacer

		gbc.gridx = 3;
		gbc.weightx = 0;
		add(installButton, gbc);

		gbc.gridx = 4;
		add(deleteButton, gbc);

		// Row 2: Path label and field
		if (descriptor.getDescription() != null) {
			gbc.gridx = 0;
			gbc.gridy = 1;
			gbc.gridwidth = 5;
			gbc.weightx = 1.0;
			JLabel descLabel = new JLabel(descriptor.getDescription());
			descLabel.setFont(descLabel.getFont().deriveFont(10f));
			add(descLabel, gbc);
			gbc.gridy = 2;
		}
		else {
			gbc.gridy = 1;
		}

		gbc.gridx = 0;
		gbc.gridwidth = 1;
		gbc.weightx = 0;
		JLabel pathLabel = new JLabel("Path:");
		pathLabel.setFont(pathLabel.getFont().deriveFont(10f));
		add(pathLabel, gbc);

		gbc.gridx = 1;
		gbc.gridwidth = 4;
		gbc.weightx = 1.0;
		File envDir = getEnvironmentDir();
		JTextField pathField = new JTextField(envDir.getAbsolutePath());
		pathField.setEditable(false);
		pathField.setFont(pathField.getFont().deriveFont(10f));
		pathField.setBackground(getBackground());
		add(pathField, gbc);

		// Row 3: Size label
		gbc.gridy++;
		gbc.gridx = 0;
		gbc.gridwidth = 1;
		gbc.weightx = 0;
		JLabel sizeLabelText = new JLabel("Size:");
		sizeLabelText.setFont(sizeLabelText.getFont().deriveFont(10f));
		add(sizeLabelText, gbc);

		gbc.gridx = 1;
		gbc.gridwidth = 4;
		gbc.weightx = 1.0;
		sizeLabel.setFont(sizeLabel.getFont().deriveFont(10f));
		add(sizeLabel, gbc);
	}

	/**
	 * Refreshes the panel's status display.
	 * <p>
	 * This checks if the environment is installed and updates the UI accordingly.
	 * The operation runs asynchronously in a background thread.
	 * </p>
	 */
	public void refresh() {
		statusLabel.setText("Checking...");
		statusLabel.setForeground(Color.GRAY);
		installButton.setEnabled(false);
		deleteButton.setEnabled(false);
		sizeLabel.setText("...");

		SwingWorker<Status, Void> worker = new SwingWorker<Status, Void>() {
			@Override
			protected Status doInBackground() {
				File envDir = getEnvironmentDir();
				boolean installed = EnvironmentUtils.isInstalled(envDir);
				String size = installed ?
						EnvironmentUtils.formatSize(EnvironmentUtils.calculateSize(envDir)) : "N/A";
				return new Status(installed, size);
			}

			@Override
			protected void done() {
				try {
					Status status = get();
					updateUI(status.installed, status.size);
				}
				catch (Exception e) {
					statusLabel.setText("Error");
					statusLabel.setForeground(Color.RED);
				}
			}
		};
		worker.execute();
	}

	private void updateUI(boolean installed, String size) {
		statusIcon.setIcon(installed ? createCheckIcon() : createXIcon());
		statusLabel.setText(installed ? "Installed" : "Not Installed");
		statusLabel.setForeground(installed ? COLOR_INSTALLED : COLOR_NOT_INSTALLED);
		installButton.setText(installed ? "Update" : "Install");
		installButton.setEnabled(true);
		deleteButton.setEnabled(installed);
		sizeLabel.setText(size);
	}

	private void installOrUpdate() {
		boolean isUpdate = EnvironmentUtils.isInstalled(getEnvironmentDir());
		String action = isUpdate ? "Update" : "Install";

		int result = JOptionPane.showConfirmDialog(
				this,
				action + " environment '" + descriptor.getName() + "'?\n\n" +
						"This requires an internet connection and may take several minutes.",
				"Confirm " + action,
				JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE
		);

		if (result == JOptionPane.YES_OPTION) {
			Window parentWindow = SwingUtilities.getWindowAncestor(this);
			ProgressDialogs.showBuildDialogAsync(
					parentWindow,
					action + " Environment",
					descriptor.getBuilder(),
					env -> {
						SwingUtilities.invokeLater(() -> {
							refresh();
							JOptionPane.showMessageDialog(
									this,
									"Environment '" + descriptor.getName() + "' " +
											(isUpdate ? "updated" : "installed") + " successfully!",
									action + " Complete",
									JOptionPane.INFORMATION_MESSAGE
							);
						});
					},
					error -> {
						SwingUtilities.invokeLater(() -> {
							JOptionPane.showMessageDialog(
									this,
									action + " failed: " + error.getMessage(),
									action + " Error",
									JOptionPane.ERROR_MESSAGE
							);
							refresh();
						});
					}
			);
		}
	}

	private void delete() {
		int result = JOptionPane.showConfirmDialog(
				this,
				"Delete environment '" + descriptor.getName() + "'?\n\n" +
						"This will permanently remove all files.",
				"Confirm Deletion",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE
		);

		if (result == JOptionPane.YES_OPTION) {
			installButton.setEnabled(false);
			deleteButton.setEnabled(false);

			SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
				@Override
				protected Void doInBackground() throws Exception {
					EnvironmentUtils.delete(getEnvironmentDir());
					return null;
				}

				@Override
				protected void done() {
					try {
						get(); // Check for exceptions
						refresh();
						JOptionPane.showMessageDialog(
								EnvironmentPanel.this,
								"Environment '" + descriptor.getName() + "' deleted successfully!",
								"Deletion Complete",
								JOptionPane.INFORMATION_MESSAGE
						);
					}
					catch (Exception e) {
						JOptionPane.showMessageDialog(
								EnvironmentPanel.this,
								"Deletion failed: " + e.getMessage(),
								"Deletion Error",
								JOptionPane.ERROR_MESSAGE
						);
						refresh();
					}
				}
			};
			worker.execute();
		}
	}

	private File getEnvironmentDir() {
		return descriptor.getExpectedDir();
	}

	// --- Icon creation (based on Stefan Hahmann's work) ---

	private javax.swing.Icon createCheckIcon() {
		return createCircleIcon(COLOR_INSTALLED, g2 -> {
			g2.drawLine(5, 10, 8, 14);
			g2.drawLine(8, 14, 15, 6);
		});
	}

	private javax.swing.Icon createXIcon() {
		return createCircleIcon(COLOR_NOT_INSTALLED, g2 -> {
			g2.drawLine(6, 6, 14, 14);
			g2.drawLine(14, 6, 6, 14);
		});
	}

	private javax.swing.Icon createCircleIcon(Color color, Consumer<Graphics2D> draw) {
		return new javax.swing.Icon() {
			@Override
			public void paintIcon(Component c, Graphics g, int x, int y) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(color);
				g2.fillOval(x, y, 20, 20);
				g2.setColor(Color.WHITE);
				g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2.translate(x, y);
				draw.accept(g2);
				g2.dispose();
			}

			@Override
			public int getIconWidth() {
				return 20;
			}

			@Override
			public int getIconHeight() {
				return 20;
			}
		};
	}

	private static class Status {
		final boolean installed;
		final String size;

		Status(boolean installed, String size) {
			this.installed = installed;
			this.size = size;
		}
	}
}
