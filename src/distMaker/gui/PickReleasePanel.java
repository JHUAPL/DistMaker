package distMaker.gui;

import glum.gui.FocusUtil;
import glum.gui.GuiUtil;
import glum.gui.action.ClickAction;
import glum.gui.panel.GlassPanel;
import glum.gui.panel.itemList.*;
import glum.gui.panel.itemList.query.QueryComposer;
import glum.gui.panel.itemList.query.QueryItemHandler;
import glum.unit.ConstUnitProvider;
import glum.unit.DateUnit;
import glum.zio.raw.ZioRaw;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import net.miginfocom.swing.MigLayout;

import com.google.common.collect.Lists;

import distMaker.LookUp;
import distMaker.Release;

public class PickReleasePanel extends GlassPanel implements ActionListener, ZioRaw, ListSelectionListener
{
	// GUI vars
	private JLabel titleL;
	private JRadioButton newestRB, olderRB;
	private ItemListPanel<Release> listPanel;
	private JButton abortB, proceedB;
	private JTextArea infoTA, warnTA;
	private Font smallFont;

	// State vars
	private StaticItemProcessor<Release> myItemProcessor;
	private Release chosenItem;
	private Release installedItem;
	private Release newestItem;

	public PickReleasePanel(Component aParent, Release aInstalledItem)
	{
		super(aParent);

		// State vars
		chosenItem = null;
		installedItem = aInstalledItem;
		newestItem = null;

		// Build the actual GUI
		smallFont = (new JTextField()).getFont();
		buildGuiArea();
		setPreferredSize(new Dimension(250, getPreferredSize().height));

		// Set up some keyboard shortcuts
		FocusUtil.addAncestorKeyBinding(this, "ESCAPE", new ClickAction(abortB));
	}

	/**
	 * Returns the VersionInfo selected by the user. This will be null if the user aborted the action.
	 */
	public Release getChosenItem()
	{
		return chosenItem;
	}

	/**
	 * Sets in the configuration of available versions
	 */
	public void setConfiguration(List<Release> itemList)
	{
		LinkedList<Release> fullList;
		DateUnit dateUnit;
		String currBuildStr, lastBuildStr;
		String currVerStr, lastVerStr;
		String appName, infoMsg;

		// Sort the items, and isolate the newest item
		fullList = Lists.newLinkedList(itemList);
		Collections.sort(fullList);
		newestItem = fullList.removeLast();

		// Retrieve vars of interest
		appName = installedItem.getName();
		dateUnit = new DateUnit("", "yyyyMMMdd HH:mm");
		currBuildStr = dateUnit.getString(installedItem.getBuildTime());
		lastBuildStr = dateUnit.getString(newestItem.getBuildTime());
		currVerStr = installedItem.getVersion();
		lastVerStr = newestItem.getVersion();

		// Update the newest area
		newestRB.setText("Latest: " + lastVerStr + " (" + lastBuildStr + ")");

		// Update the list of available items
		myItemProcessor.setItems(fullList);

		// Update the infoTA
		infoMsg = "The latest release of " + appName + ", " + lastVerStr + ", was built on " + lastBuildStr + ".";
		if (newestItem.equals(installedItem) == true)
			infoMsg += "You have the latest release of " + appName + "!";
		else
			infoMsg += "Your current version is " + currVerStr + ", which was built on: " + currBuildStr;

		infoTA.setText(infoMsg);
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
		JPanel tmpPanel;
		JScrollPane tmpScrollPane;

		// Form the layout
		setLayout(new MigLayout("", "[left][grow][]", "[][][]3[grow][]"));

		// Title Area
		titleL = new JLabel("Please select an update", JLabel.CENTER);
		add(titleL, "growx,span 2,wrap");

		// Info area
		infoTA = GuiUtil.createUneditableTextArea(2, 0);
		add(infoTA, "growx,span,wrap");

		// Latest version area
		newestRB = GuiUtil.createJRadioButton("Unspecified", this, smallFont);
		newestRB.setSelected(true);
		add(newestRB, "span,wrap");

		// Older version area
		olderRB = GuiUtil.createJRadioButton("Select an older release:", this, smallFont);
		add(olderRB, "span,wrap");

		tmpPanel = buildItemListTablePanel();
		tmpPanel.setBorder(new EmptyBorder(0, 15, 0, 0));
		add(tmpPanel, "growx,growy,span,wrap");

		// Link the radio buttons
		GuiUtil.linkRadioButtons(newestRB, olderRB);

		// Warn Area
		warnTA = GuiUtil.createUneditableTextArea(0, 0);
		warnTA.setBorder(null);
		tmpScrollPane = new JScrollPane(warnTA);
		add(tmpScrollPane, "growx,growy,h 40::,span,wrap");

		// Action area
		abortB = GuiUtil.createJButton("Abort", this, smallFont);
		proceedB = GuiUtil.createJButton("Proceed", this, smallFont);
		add(abortB, "align right,span,split 2");
		add(proceedB, "");

		setBorder(new BevelBorder(BevelBorder.RAISED));
	}

	/**
	 * Utility method to build the query item list table
	 */
	private JPanel buildItemListTablePanel()
	{
		QueryComposer<LookUp> aComposer;
		ItemHandler<Release> aItemHandler;
		DateUnit dateUnit;

		dateUnit = new DateUnit("", "yyyyMMMdd HH:mm");

		aComposer = new QueryComposer<LookUp>();
		aComposer.addAttribute(LookUp.Version, String.class, "Version", null);
		aComposer.addAttribute(LookUp.BuildTime, new ConstUnitProvider(dateUnit), "Build Date", null);

		aItemHandler = new QueryItemHandler<Release>(aComposer);
		myItemProcessor = new StaticItemProcessor<Release>();

		listPanel = new ItemListPanel<Release>(aItemHandler, myItemProcessor, false, false);
		listPanel.setSortingEnabled(false);
		listPanel.addListSelectionListener(this);
		return listPanel;
	}

	/**
	 * Synchronizes our GUI vars
	 */
	private void updateGui()
	{
		Release pickItem;
		String warnMsg;
		boolean isEnabled;

		// Determine the selected version
		pickItem = newestItem;
		if (newestRB.isSelected() == false)
			pickItem = listPanel.getSelectedItem();

		// Determine if we are ready to proceed
		isEnabled = (pickItem != null && pickItem.equals(installedItem) == false);
		proceedB.setEnabled(isEnabled);

		// Determine the warnMsg
		warnMsg = null;
		if (pickItem == null)
			warnMsg = "Please select a version to deploy.";
		else if (pickItem.equals(installedItem) == true)
			warnMsg = "This is the currently deployed version. Please select a different version.";
		else if (pickItem.getBuildTime() < installedItem.getBuildTime())
			warnMsg = "Please note, that the currently selected version is older than the currently installed version.";

		warnTA.setText(warnMsg);
	}

}
