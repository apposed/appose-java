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

/**
 * Swing GUI components for Appose environment building and monitoring.
 * <p>
 * This package provides lightweight, reusable Swing components for displaying
 * environment build progress and managing Appose environments. These components
 * are optional and require the {@code java.desktop} module.
 * </p>
 * <h2>Core Components</h2>
 * <h3>Build Progress Components</h3>
 * <ul>
 *     <li>{@link org.apposed.appose.gui.BuildProgressPanel} - Reusable JPanel
 *     for displaying build progress with a status label, progress bar, and
 *     scrollable output text area.</li>
 *     <li>{@link org.apposed.appose.gui.BuilderProgressAdapter} - Thread-safe
 *     bridge between Builder subscription callbacks and Swing EDT updates.</li>
 *     <li>{@link org.apposed.appose.gui.ProgressDialogs} - Utility methods for
 *     common dialog patterns (modal and async build dialogs).</li>
 * </ul>
 * <h3>Environment Management Components</h3>
 * <ul>
 *     <li>{@link org.apposed.appose.gui.EnvironmentPanel} - Panel for managing
 *     a single environment with install/update/delete capabilities.</li>
 *     <li>{@link org.apposed.appose.gui.EnvironmentManagerPanel} - Panel for
 *     managing multiple environments with refresh all functionality.</li>
 *     <li>{@link org.apposed.appose.gui.EnvironmentDescriptor} - Describes an
 *     environment for management UI purposes.</li>
 *     <li>{@link org.apposed.appose.gui.EnvironmentUtils} - Utility methods
 *     for environment operations (check status, calculate size, delete, etc.).</li>
 * </ul>
 * <h2>Usage Examples</h2>
 * <h3>Simple Modal Dialog</h3>
 * <pre>
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
 * </pre>
 * <h3>Async Dialog with Callbacks</h3>
 * <pre>
 * ProgressDialogs.showBuildDialogAsync(
 *     parentFrame,
 *     "Building Environment",
 *     Appose.uv().include("cowsay"),
 *     env -> {
 *         // Success callback
 *         System.out.println("Build succeeded: " + env.base());
 *     },
 *     error -> {
 *         // Error callback
 *         System.err.println("Build failed: " + error.getMessage());
 *     }
 * );
 * </pre>
 * <h3>Custom Panel Embedding</h3>
 * <pre>
 * BuildProgressPanel panel = new BuildProgressPanel();
 * BuilderProgressAdapter adapter = new BuilderProgressAdapter(panel);
 *
 * // Embed panel in your UI
 * myFrame.add(panel);
 *
 * // Build in background thread
 * new Thread(() -> {
 *     try {
 *         Environment env = Appose.pixi()
 *             .subscribeProgress(adapter::updateProgress)
 *             .subscribeOutput(adapter::appendOutput)
 *             .subscribeError(adapter::appendError)
 *             .build();
 *     }
 *     catch (BuildException e) {
 *         // Handle error
 *     }
 * }).start();
 * </pre>
 * <h3>Environment Manager</h3>
 * <pre>
 * EnvironmentDescriptor[] envs = {
 *     new EnvironmentDescriptor(
 *         "Python Data Science",
 *         "Python with numpy and pandas",
 *         Appose.uv().python("3.10").include("numpy", "pandas").name("data-sci")
 *     ),
 *     new EnvironmentDescriptor(
 *         "Conda Environment",
 *         Appose.pixi().conda("python>=3.8").name("my-conda")
 *     )
 * };
 * EnvironmentManagerPanel manager = new EnvironmentManagerPanel(
 *     "My Environments",
 *     envs
 * );
 * myFrame.add(manager);
 * </pre>
 * <h2>Thread Safety</h2>
 * <p>
 * All components handle EDT synchronization automatically via
 * {@link org.apposed.appose.gui.BuilderProgressAdapter}. Builder callbacks
 * (which may be called from background threads) are safely dispatched to
 * the EDT before updating Swing components.
 * </p>
 *
 * @author Curtis Rueden
 * @author Claude Code
 */
package org.apposed.appose.gui;
