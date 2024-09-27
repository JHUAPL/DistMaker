// Copyright (C) 2024 The Johns Hopkins University Applied Physics Laboratory LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package distMaker.gui;

import java.awt.*;
import java.awt.event.*;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import distMaker.LookUp;
import distMaker.node.AppRelease;
import glum.gui.FocusUtil;
import glum.gui.GuiUtil;
import glum.gui.action.ClickAction;
import glum.gui.panel.GlassPanel;
import glum.gui.panel.itemList.ItemListPanel;
import glum.gui.panel.itemList.StaticItemProcessor;
import glum.gui.panel.itemList.query.*;
import glum.unit.ConstUnitProvider;
import glum.unit.DateUnit;
import net.miginfocom.swing.MigLayout;

/**
 * User input component that allows the user to specify an {@link AppRelease}.
 *
 * @author lopeznr1
 */
public class PickReleasePanel extends GlassPanel implements ActionListener, ItemListener, ListSelectionListener
{
	// Constants
	private static final long serialVersionUID = 1L;
	private static final Color ColorFail = Color.red.darker().darker();

	// GUI vars
	private JLabel titleL;
	private JRadioButton newestRB, olderRB;
	private ItemListPanel<AppRelease, LookUp> listPanel;
	private QueryTableCellRenderer col0Renderer, col1Renderer;
	private JButton abortB, proceedB;
	private JTextArea headTA, infoTA;
	private Font smallFont;

	// State vars
	private StaticItemProcessor<AppRelease> myItemProcessor;
	private AppRelease chosenItem;
	private AppRelease installedItem;
	private AppRelease newestItem;

	/**
	 * Standard Constructor
	 */
	public PickReleasePanel(Component aParent, AppRelease aInstalledItem)
	{
		super(aParent);

		// State vars
		chosenItem = null;
		installedItem = aInstalledItem;
		newestItem = null;

		// Build the actual GUI
		smallFont = (new JTextField()).getFont();
		buildGuiArea();
		setPreferredSize(new Dimension(350, getPreferredSize().height));

		// Set up some keyboard shortcuts
		FocusUtil.addAncestorKeyBinding(this, "ESCAPE", new ClickAction(abortB));
	}

	/**
	 * Returns the Release selected by the user. This will be null if the user aborted the action.
	 */
	public AppRelease getChosenItem()
	{
		return chosenItem;
	}

	/**
	 * Sets in the configuration of available versions
	 */
	public void setConfiguration(List<AppRelease> aItemL)
	{
		DateUnit dateUnit;
		// String currBuildStr;
		String lastBuildStr;
		String currVerStr, lastVerStr;
		String appName, headMsg;

		// Sort the items, and isolate the newest item
		LinkedList<AppRelease> tmpItemL;
		tmpItemL = new LinkedList<>(aItemL);
		Collections.sort(tmpItemL);
		Collections.reverse(tmpItemL);  // reverse the list to show most recent versions on top
		newestItem = tmpItemL.removeFirst();

		// Retrieve vars of interest
		appName = installedItem.getName();
		dateUnit = new DateUnit("", "yyyyMMMdd HH:mm");
		// currBuildStr = dateUnit.getString(installedItem.getBuildTime());
		lastBuildStr = dateUnit.getString(newestItem.getBuildTime());
		currVerStr = installedItem.getVersion();
		lastVerStr = newestItem.getVersion();

		// Update the newest area
		newestRB.setText("Latest: " + lastVerStr + " (" + lastBuildStr + ")");

		// Update the list of available items
		myItemProcessor.setItems(tmpItemL);

		// Update the infoTA
		if (newestItem.equals(installedItem) == true)
		{
			titleL.setText(appName + " is up to date.");
			headMsg = "You are running the latest release " + " (" + lastVerStr + ") that was built on " + lastBuildStr + ". ";
			headMsg += "You may switch to an older release by choosing one of the versions below.";
		}
		else if (installedItem.getBuildTime() > newestItem.getBuildTime())
		{
			titleL.setText(appName + " (Out-Of-Band Release)");
			headMsg = "You are running version " + currVerStr + ". This version has never been released! ";
			headMsg += "You may update to the latest release or switch to an older release by choosing another version below. ";
		}
		else
		{
			titleL.setText(appName + " needs to be updated.");
			headMsg = "You are running version " + currVerStr + ". ";
			headMsg += "You may update to the latest release. You may also switch to an " + "older release by choosing another version below. ";
		}
		headMsg += "\n";

		headTA.setText(headMsg);
		updateGui();
	}

	@Override
	public void actionPerformed(ActionEvent aEvent)
	{
		Object source;

		source = aEvent.getSource();
		if (source == abortB)
		{
			chosenItem = null;
			setVisible(false);
		}
		else if (source == proceedB)
		{
			chosenItem = listPanel.getSelectedItem();
			if (newestRB.isSelected() == true)
				chosenItem = newestItem;

			setVisible(false);
		}
		else
		{
			updateGui();
		}
	}

	@Override
	public void itemStateChanged(ItemEvent aEvent)
	{
		updateGui();
	}

	@Override
	public void valueChanged(ListSelectionEvent aEvent)
	{
		// Update only after the user has released the mouse
		if (aEvent.getValueIsAdjusting() == true)
			return;

		updateGui();
	}

	/**
	 * Builds the main GUI area
	 */
	private void buildGuiArea()
	{
		JPanel listPanel, mainPanel;

		// Form the layout
		setLayout(new MigLayout("", "[left][grow][]", "[]"));

		// Title Area: Note that the default text gets replaced once the current version status is known
		titleL = new JLabel("Please select an update", JLabel.CENTER);
		add(titleL, "growx,span 2,wrap");

		// Header area
		headTA = GuiUtil.createUneditableTextArea(2, 0);
		add(headTA, "w 0::,growx,span,wrap");

		// Latest version area
		newestRB = GuiUtil.createJRadioButton(this, "Unspecified", smallFont);
//		newestRB.setSelected(true);

		// Older version area
		olderRB = GuiUtil.createJRadioButton(this, "Select an older release:", smallFont);

		listPanel = buildItemListTablePanel();
		listPanel.setBorder(new EmptyBorder(0, 15, 0, 0));

		mainPanel = new JPanel(new MigLayout("", "0[left,grow]", "0[][]3[grow]0"));
		mainPanel.add(newestRB, "wrap");
		mainPanel.add(olderRB, "wrap");
		mainPanel.add(listPanel, "growx,growy");

		// Link the radio buttons
		GuiUtil.linkRadioButtons(newestRB, olderRB);

		// Info Area
		JScrollPane tmpSP;
		infoTA = GuiUtil.createUneditableTextArea(0, 0);
		infoTA.setTabSize(3);
		tmpSP = new JScrollPane(infoTA);
		tmpSP.setBorder(new EmptyBorder(0, 5, 0, 5));

		JSplitPane tmpPane;
		tmpPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, mainPanel, tmpSP);
		tmpPane.setBorder(null);
		tmpPane.setResizeWeight(0.15);
//		tmpPane.setDividerLocation(0.50);
		add(tmpPane, "growx,growy,pushy,span,wrap");

		// Action area
		abortB = GuiUtil.createJButton("Abort", this, smallFont);
		proceedB = GuiUtil.createJButton("Proceed", this, smallFont);
		add(abortB, "align right,span,split 2");
		add(proceedB, "");

		// Set the initial state
		newestRB.setSelected(true);
	}

	/**
	 * Utility method to build the query item list table
	 */
	private JPanel buildItemListTablePanel()
	{
		QueryComposer<LookUp> tmpComposer;
		QueryItemHandler<AppRelease, LookUp> tmpIH;
		DateUnit dateUnit;

		dateUnit = new DateUnit("", "yyyyMMMdd HH:mm");

		tmpComposer = new QueryComposer<>();
		tmpComposer.addAttribute(LookUp.Version, String.class, "Version", null);
		tmpComposer.addAttribute(LookUp.BuildTime, new ConstUnitProvider(dateUnit), "Build Date", null);

		col0Renderer = new QueryTableCellRenderer();
		col1Renderer = new QueryTableCellRenderer();
		col1Renderer.setUnit(dateUnit);
		tmpComposer.setRenderer(LookUp.Version, col0Renderer);
		tmpComposer.setRenderer(LookUp.BuildTime, col1Renderer);

		tmpIH = new QueryItemHandler<>();
		myItemProcessor = new StaticItemProcessor<>();

		listPanel = new ItemListPanel<>(tmpIH, myItemProcessor, tmpComposer, false);
		listPanel.setSortingEnabled(false);
		listPanel.addListSelectionListener(this);
		return listPanel;
	}

	/**
	 * Synchronizes our GUI vars
	 */
	private void updateGui()
	{
		AppRelease pickItem;
		String failMsg, infoMsg;
		boolean isEnabled;

		// Determine the selected version
		pickItem = newestItem;
		if (newestRB.isSelected() == false)
			pickItem = listPanel.getSelectedItem();

		// Determine if we are ready to proceed
		isEnabled = (pickItem != null && pickItem.equals(installedItem) == false);
		proceedB.setEnabled(isEnabled);

		// Update the older release area
		isEnabled = olderRB.isSelected();
		GuiUtil.setEnabled(listPanel, isEnabled);
		col0Renderer.setEnabled(isEnabled);
		col1Renderer.setEnabled(isEnabled);

		// Determine the warnMsg
		failMsg = null;
		infoMsg = null;
		if (pickItem == null)
			failMsg = "Select the version you want to switch to.";
		else if (pickItem.equals(installedItem) == true)
			failMsg = "You cannot update to the same version you are running. You can switch to a different version.";
		else if (pickItem.getBuildTime() < installedItem.getBuildTime())
			infoMsg = "Please note that your current selection will revert back to an older version than the currently installed version.";

		// Add the app release notes
		if (pickItem != null)
		{
			String noteMsg;
			noteMsg = pickItem.getInfoMsg();
			if (noteMsg != null)
			{
				if (infoMsg != null)
					infoMsg += "\n\n" + noteMsg;
				else
					infoMsg = noteMsg;
			}
		}

		Color tmpColor = Color.BLACK;
		if (failMsg != null)
			tmpColor = ColorFail;

		String tmpMsg = failMsg;
		if (tmpMsg == null)
			tmpMsg = infoMsg;

		infoTA.setText(tmpMsg);
		infoTA.setForeground(tmpColor);
	}

}
