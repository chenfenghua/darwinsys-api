/* Copyright (c) Ian F. Darwin, http://www.darwinsys.com/, 2004-2006.
 * $Id$
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS''
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.darwinsys.sql;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;

import com.darwinsys.genericui.SuccessFailureUI;
import com.darwinsys.io.TextAreaWriter;
import com.darwinsys.swingui.SuccessFailureBarSwing;
import com.darwinsys.swingui.UtilGUI;
import com.darwinsys.util.Verbosity;

/**
 * A simple GUI to run one set of commands.
 * XXX There should be a ProgressMonitor or a JProgressBar(Indeterminate) when running commands
 */
@SuppressWarnings("serial")
public class SQLRunnerGUI  {

	private static final int DISPLAY_COLUMNS = 70;

	final Preferences p = Preferences.userNodeForPackage(SQLRunnerGUI.class);

	final SuccessFailureUI bar;

	final JFrame mainWindow;

	final JTextArea inputTextArea, outputTextArea;

	final JButton runButton;

	final PrintWriter out;

	final List<Configuration> configurations;
	final JComboBox connectionsList;
	final JCheckBox passwdPromptCheckBox;
	final JComboBox modeList;
	final JDialog busyDialog;
	Thread commandRunnerThread;

	Connection conn;

	private SQLRunnerErrorHandler eHandler = new SQLRunnerErrorHandler() {

		public void handleError(Exception e) {

				JOptionPane.showMessageDialog(mainWindow,
					"<html><p>Error: <font color='red'>" + e,
					"Oops", JOptionPane.ERROR_MESSAGE);
				e.printStackTrace();
		}

	};

	/**
	 * Allow the Application to provide its own error handler.
	 * @param eHandler
	 */
	public void setErrorHandler(SQLRunnerErrorHandler eHandler) {
		this.eHandler = eHandler;
	}

	/**
	 * Main method; ignores arguments.
	 */
	public static void main(String[] args) {
		String config = null;
		if (args.length != 0) {
			for (int i = 0; i < args.length; i++) {
				String arg = args[i];
				if ("-c".equals(arg) && args.length > i) {
					config = args[i+1];
				}
			}
		}
		SQLRunnerGUI prog = new SQLRunnerGUI();
		if (config != null) {
			prog.setConfig(config);
		}
	}


	/**
	 * Set the selected Configuration Object in the Connections chooser
	 * from a given Configuration Name passed as a String.
	 * @param config The chosen name.
	 */
	private void setConfig(String config) {
		if (config == null) {
			throw new NullPointerException("Configuration name may not be null");
		}
		for (Configuration configListItem : configurations) {
			if (config.equals(configListItem.toString())) {
				connectionsList.setSelectedItem(configListItem);
				return;
			}
		}
		System.err.printf("Warning: Configuration %s not found", config);
	}

	/**
	 * This is the all-important action for the Run button! Run the current SQL input
	 * string with the given settings
	 */
	Action runAction = new AbstractAction("Run") {

		/** Called each time the user presses the Run button */
		public void actionPerformed(ActionEvent evt) {

			// Run this under a its own Thread, so we don't block the EventDispatch thread...

			commandRunnerThread = new Thread() {
                public void run() {
					Dimension dlgBounds = busyDialog.getSize();
					dlgBounds.width = mainWindow.getSize().width;
					busyDialog.setSize(dlgBounds);
					try {
						String command = inputTextArea.getText().trim();
						if (command == null || command.length() == 0) {
							JOptionPane.showMessageDialog(mainWindow,
									"Command window is empty", "Out of order", JOptionPane.WARNING_MESSAGE);
							return;
						}
						runButton.setEnabled(false);
						Configuration config = (Configuration) connectionsList.getSelectedItem();
						if (passwdPromptCheckBox.isSelected() || !config.hasPassword()) {
							String pass = getPassword("Connection password for " + config.getName());
							config.setDbPassword(pass);
						}
						bar.reset();
						busyDialog.setVisible(true);

						conn =  ConnectionUtil.getConnection(config);

						SQLRunner.setVerbosity(Verbosity.QUIET);
						SQLRunner prog = new SQLRunner(conn, null, "t");
						prog.setOutputFile(out);
						prog.setOutputMode((OutputMode) modeList.getSelectedItem());

						// RUN THE SQL
						prog.runStatement(command);
						conn.close();
						bar.showSuccess();	// If no exception thrown
					} catch (Exception e) {
						bar.showFailure();
						eHandler.handleError(e);
					} finally {
						if (conn != null) {
						    try {
						        conn.close();
						    } catch (SQLException e) {
						        // We just don't care at this point....
						    }
                        }
						runButton.setEnabled(true);
						busyDialog.setVisible(false);
					}
				}

			};
			commandRunnerThread.start();
		}

	};

	/**
	 * Action to cancel the database if it is taking too long... Use with caution.
	 */
	Action cancelAction = new 	AbstractAction("Cancel Database Action") {
		public void actionPerformed(ActionEvent e) {
			if (commandRunnerThread.isAlive()) {
				try {
					if (conn != null) {
						conn.close();
					} else {
						commandRunnerThread.interrupt();
					}
				} catch (Exception ex) {
					System.err.println("Well what did you expect? I caught this exception:");
					ex.printStackTrace();
				}
			}
		}
	};

	/**
	 * Constructor for main GUI
	 */
	public SQLRunnerGUI() {
		mainWindow = new JFrame("SQLRunner");
		mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		final Container controlsArea = new JPanel();
		mainWindow.add(controlsArea, BorderLayout.NORTH);

		configurations = ConnectionUtil.getConfigurations();
		connectionsList = new JComboBox(configurations.toArray(new Configuration[configurations.size()]));
		// when you change to a different database you don't want to remember the "force passwd prompt" setting
		connectionsList.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				passwdPromptCheckBox.setSelected(false);
			}
		});
		controlsArea.add(new JLabel("Connection"));
		controlsArea.add(connectionsList);
		passwdPromptCheckBox = new JCheckBox("Ask for passwd");
		controlsArea.add(passwdPromptCheckBox);

		final JComboBox inTemplateChoice = new JComboBox();
		// XXX Of course these should come from Properties and be editable...
		inTemplateChoice.addItem("Input Template:");
		inTemplateChoice.addItem("SELECT * from TABLE where x = y");
		inTemplateChoice.addItem("INSERT into TABLE(col,col) VALUES(val,val)");
		inTemplateChoice.addItem("UPDATE TABLE set x = y where x = y");
		controlsArea.add(inTemplateChoice);

		final JButton inTemplateButton = new JButton("Apply Template");
		controlsArea.add(inTemplateButton);

		modeList = new JComboBox();
		for (OutputMode mode : OutputMode.values()) {
			modeList.addItem(mode);
		}
		controlsArea.add(new JLabel("Output Format:"));
		controlsArea.add(modeList);

		runButton = new JButton(runAction);
		controlsArea.add(runButton);

		// used by Run...
		busyDialog = new JDialog(mainWindow, "Running...");
		JProgressBar busyIndicator = new JProgressBar();
		busyIndicator.setIndeterminate(true);
		busyDialog.add(busyIndicator, BorderLayout.CENTER);
		JPanel bottomPanel = new JPanel();
		bottomPanel.add(new JButton(cancelAction));
		busyDialog.add(bottomPanel, BorderLayout.SOUTH);
		busyDialog.pack();
		busyDialog.setLocationRelativeTo(mainWindow);

		inputTextArea = new JTextArea(6, DISPLAY_COLUMNS);
		JScrollPane inputAreaScrollPane = new JScrollPane(inputTextArea);
		inputAreaScrollPane.setBorder(BorderFactory.createTitledBorder("SQL Command"));

		outputTextArea = new JTextArea(20, DISPLAY_COLUMNS);
		JScrollPane outputAreaScrollPane = new JScrollPane(outputTextArea);
		outputAreaScrollPane.setBorder(BorderFactory.createTitledBorder("SQL Results"));

		inTemplateButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (inTemplateChoice.getSelectedIndex() == 0) {
					return;
				}
				inputTextArea.setText((String)inTemplateChoice.getSelectedItem());
			}
		});

		JButton clearOutput = new JButton("Clear Output");
		clearOutput.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		        outputTextArea.setText("");
                bar.reset();
		    }
		});
        controlsArea.add(clearOutput);

        mainWindow.add(new JSplitPane(JSplitPane.VERTICAL_SPLIT,
					inputAreaScrollPane,
					outputAreaScrollPane), BorderLayout.CENTER);


		out = new PrintWriter(new TextAreaWriter(outputTextArea));

		bar = new SuccessFailureBarSwing(mainWindow.getBackground(), 400, 20);
		bar.reset();
		mainWindow.add((JComponent)bar, BorderLayout.SOUTH);

		mainWindow.pack();
		UtilGUI.monitorWindowPosition(mainWindow, p);
		mainWindow.setVisible(true);
		inputTextArea.requestFocusInWindow();
	}

		@SuppressWarnings("serial")
		private String getPassword(String prompt) {
			final JDialog input = new JDialog(mainWindow, "Prompt", true);
			input.setLayout(new FlowLayout());
			input.add(new JLabel(prompt));
			JPasswordField textField = new JPasswordField(10);
			input.add(textField);
			Action okAction = new AbstractAction("OK") {

				public void actionPerformed(ActionEvent e) {
					input.dispose();
				}

			};
			textField.addActionListener(okAction);
			JButton ok = new JButton(okAction);
			input.add(ok);
			input.pack();
			input.setLocationRelativeTo(mainWindow);
			input.setVisible(true);	// BLOCKING

			return new String(textField.getPassword());
		}

}
