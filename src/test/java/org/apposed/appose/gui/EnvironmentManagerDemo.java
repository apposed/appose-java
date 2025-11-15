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

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * Demo of the Appose environment manager GUI components.
 *
 * @author Curtis Rueden
 */
public class EnvironmentManagerDemo {

	public static void main(String[] args) {
		// Set system look and feel
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		SwingUtilities.invokeLater(() -> {
			// Create some example environments
			EnvironmentDescriptor[] environments = {
					new EnvironmentDescriptor(
							"Python Data Science",
							"Python 3.10 with numpy, pandas, and matplotlib",
							"data-science",  // Must match builder.name()
							Appose.uv()
									.python("3.10")
									.include("appose", "numpy", "pandas", "matplotlib")
									.name("data-science")
					),
					new EnvironmentDescriptor(
							"Python ML",
							"Python with scikit-learn for machine learning",
							"ml-env",  // Must match builder.name()
							Appose.uv()
									.python("3.11")
									.include("appose", "scikit-learn", "scipy")
									.name("ml-env")
					),
					new EnvironmentDescriptor(
							"Conda Environment",
							"Example conda environment with Python",
							"conda-test",  // Must match builder.name()
							Appose.pixi()
									.conda("python>=3.8", "appose", "numpy")
									.channels("conda-forge")
									.name("conda-test")
					)
			};

			// Create the manager panel
			EnvironmentManagerPanel managerPanel = new EnvironmentManagerPanel(
					"Appose Environment Manager",
					environments
			);

			// Create and show the frame
			JFrame frame = new JFrame("Appose Environment Manager Demo");
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

			// Wrap in scroll pane for better handling of many environments
			JScrollPane scrollPane = new JScrollPane(managerPanel);
			scrollPane.setPreferredSize(new Dimension(800, 600));

			frame.setLayout(new BorderLayout());
			frame.add(scrollPane, BorderLayout.CENTER);

			frame.pack();
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);
		});
	}
}
