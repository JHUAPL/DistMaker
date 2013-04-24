package distMaker.gui;

import glum.gui.FocusUtil;
import glum.gui.GuiUtil;
import glum.gui.action.ClickAction;
import glum.gui.panel.GlassPanel;
import glum.gui.panel.itemList.ItemListPanel;
import glum.gui.panel.itemList.StaticItemProcessor;
import glum.gui.panel.itemList.query.QueryComposer;
import glum.gui.panel.itemList.query.QueryItemHandler;
import glum.gui.panel.itemList.query.QueryTableCellRenderer;
import glum.unit.ConstUnitProvider;
import glum.unit.DateUnit;
import glum.zio.raw.ZioRaw;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;
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
   private static final long serialVersionUID = 1L;

   // GUI vars
	private JLabel titleL;
	private JRadioButton newestRB, olderRB;
	private ItemListPanel<Release> listPanel;
	private QueryTableCellRenderer col0Renderer, col1Renderer;
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
	 * Returns the Release selected by the user. This will be null if the user aborted the action.
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
		// String currBuildStr;
		String lastBuildStr;
		String currVerStr, lastVerStr;
		String appName, infoMsg;

		// Sort the items, and isolate the newest item
		fullList = Lists.newLinkedList(itemList);
		Collections.sort(fullList);
		newestItem = fullList.removeLast();

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
		myItemProcessor.setItems(fullList);

		// Update the infoTA
		if (newestItem.equals(installedItem) == true) {
         titleL.setText(appName + " is up to date.");
         infoMsg = "You are running the latest release "
               + " (" + lastVerStr + ") that was built on " + lastBuildStr + ". ";
         infoMsg += "You may switch to an older release by choosing one of the versions below.";
		} else {
         titleL.setText(appName + " needs to be updated.");
			infoMsg = "You are running version is " + currVerStr + ". ";
         infoMsg += "You may update to the latest release or even to an "
               + "older relase by choosing another version below. ";
		}
		infoMsg += "\n";

		infoTA.setText(infoMsg);
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

		// Form the layout
		setLayout(new MigLayout("", "[left][grow][]", "[]40[][][]3[grow]10[]"));

		// Title Area
		titleL = new JLabel("Please select an update", JLabel.CENTER); // this text gets replaced once the curent version status is known
		add(titleL, "growx,span 2,wrap");

		// Info area
		infoTA = GuiUtil.createUneditableTextArea(2, 0);
		add(infoTA, "w 0::,growx,span,wrap");

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
		add(warnTA, "w 0::,growx,span,wrap");

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
		QueryItemHandler<Release> aItemHandler;
		DateUnit dateUnit;

		dateUnit = new DateUnit("", "yyyyMMMdd HH:mm");

		aComposer = new QueryComposer<LookUp>();
		aComposer.addAttribute(LookUp.Version, String.class, "Version", null);
		aComposer.addAttribute(LookUp.BuildTime, new ConstUnitProvider(dateUnit), "Build Date", null);

		col0Renderer = new QueryTableCellRenderer();
		col1Renderer = new QueryTableCellRenderer();
		col1Renderer.setUnit(dateUnit);
		aComposer.setRenderer(LookUp.Version, col0Renderer);
		aComposer.setRenderer(LookUp.BuildTime, col1Renderer);

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

		// Update the older release area
		isEnabled = olderRB.isSelected();
		GuiUtil.setEnabled(listPanel, isEnabled);
		col0Renderer.setEnabled(isEnabled);
		col1Renderer.setEnabled(isEnabled);

		// Determine the warnMsg
		warnMsg = null;
		if (pickItem == null)
			warnMsg = "Select the version you want to switch to.";
		else if (pickItem.equals(installedItem) == true)
			warnMsg = "You cannot update to the same version you are running. You can dwitch to a different version.";
		else if (pickItem.getBuildTime() < installedItem.getBuildTime())
			warnMsg = "Please note that your current selection will revert back to an older version than the currently installed version.";

		warnTA.setText(warnMsg);
	}

}
