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

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link BuildProgressPanel} and {@link BuilderProgressAdapter}.
 *
 * @author Curtis Rueden
 * @author Claude Code
 */
public class BuildProgressPanelTest {

	@Test
	public void testPanelCreation() {
		BuildProgressPanel panel = new BuildProgressPanel();
		assertEquals("Ready", panel.getStatusLabel().getText());
		assertFalse(panel.getProgressBar().isIndeterminate());
		assertEquals(0, panel.getProgressBar().getValue());
		assertEquals("", panel.getOutputArea().getText());
	}

	@Test
	public void testPanelUpdatesOnEDT() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);

		SwingUtilities.invokeLater(() -> {
			BuildProgressPanel panel = new BuildProgressPanel();

			// Test status update
			panel.setStatus("Testing...");
			assertEquals("Testing...", panel.getStatusLabel().getText());

			// Test progress update - determinate
			panel.setProgress(5, 10);
			assertFalse(panel.getProgressBar().isIndeterminate());
			assertEquals(10, panel.getProgressBar().getMaximum());
			assertEquals(5, panel.getProgressBar().getValue());

			// Test progress update - indeterminate
			panel.setProgress(0, 0);
			assertTrue(panel.getProgressBar().isIndeterminate());

			// Test text append
			panel.appendText("Hello\n");
			panel.appendText("World\n");
			assertEquals("Hello\nWorld\n", panel.getOutputArea().getText());

			// Test clear
			panel.clearText();
			assertEquals("", panel.getOutputArea().getText());

			latch.countDown();
		});

		assertTrue(latch.await(5, TimeUnit.SECONDS), "EDT updates did not complete in time");
	}

	@Test
	public void testAdapterThreadSafety() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);

		SwingUtilities.invokeLater(() -> {
			BuildProgressPanel panel = new BuildProgressPanel();
			BuilderProgressAdapter adapter = new BuilderProgressAdapter(panel);

			// Simulate callbacks from background thread
			Thread backgroundThread = new Thread(() -> {
				adapter.updateProgress("Building...", 3, 10);
				adapter.appendOutput("Processing files...\n");
				adapter.appendError("Warning: deprecated API\n");

				// Give EDT time to process
				try {
					Thread.sleep(100);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}

				SwingUtilities.invokeLater(() -> {
					assertEquals("Building...", panel.getStatusLabel().getText());
					assertEquals(3, panel.getProgressBar().getValue());
					assertEquals(10, panel.getProgressBar().getMaximum());
					assertTrue(panel.getOutputArea().getText().contains("Processing files..."));
					assertTrue(panel.getOutputArea().getText().contains("[ERROR] Warning: deprecated API"));
					latch.countDown();
				});
			});

			backgroundThread.start();
		});

		assertTrue(latch.await(5, TimeUnit.SECONDS), "Adapter thread safety test did not complete in time");
	}
}
