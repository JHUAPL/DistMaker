package distMaker.gui;

import glum.gui.FocusUtil;
import glum.gui.GuiUtil;
import glum.gui.action.ClickAction;
import glum.gui.component.*;
import glum.gui.panel.GlassPanel;
import glum.gui.panel.generic.MessagePanel;
import glum.unit.ByteUnit;
import glum.zio.raw.ZioRaw;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.List;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import distMaker.DistUtils;
import distMaker.platform.AppleFileUtil;
import distMaker.platform.LinuxFileUtil;

import net.miginfocom.swing.MigLayout;

public class MemoryConfigPanel extends GlassPanel implements ActionListener, ZioRaw, ListSelectionListener
{
	private static final long KB_SIZE = 1024;
	private static final long MB_SIZE = 1024 * 1024;
	private static final long GB_SIZE = 1024 * 1024 * 1024;

	// GUI vars
	private JLabel titleL;
	private GLabel maxMemL, currMemL, targMemL;
	private GSlider targMemS;
	private JButton applyB, closeB, resetB;
	private JTextArea infoTA, warnTA;
	private MessagePanel warnPanel;

	// State vars
	private long minMemSize, maxMemSize;
	private long currMemSize;
	private long instMemSize;
	private long targMemSize;

	public MemoryConfigPanel(Component aParent, long aMaxMemSize)
	{
		super(aParent);

		// State vars
		minMemSize = roundToMB(256 * MB_SIZE);
		maxMemSize = roundToMB(aMaxMemSize);
		initialize();

		// Build the actual GUI
		buildGuiArea();
		setPreferredSize(new Dimension(325, getPreferredSize().height));
		warnPanel = new MessagePanel(this);
		warnPanel.setSize(400, 150);

		// Set up some keyboard shortcuts
		FocusUtil.addAncestorKeyBinding(this, "ESCAPE", new ClickAction(applyB));
		updateGui();
	}

	@Override
	public void actionPerformed(ActionEvent aEvent)
	{
		Object source;

		source = aEvent.getSource();
		if (source == applyB)
		{
			applyChanges();
		}
		else if (source == resetB)
		{
			targMemS.setModelValue(currMemSize);
		}
		else if (source == closeB)
		{
			setVisible(false);
		}

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
	 * Helper method to update the DistMaker distribution to reflect the user preferences.
	 */
	public void applyChanges()
	{
		File installPath, pFile, scriptFile;
		String errMsg;
		boolean isValidPlatform;

		// Retrive the targMemSize
		targMemSize = (long)targMemS.getModelValue();
		targMemSize = roundToMB(targMemSize);
		
		// Get the top level install path
		installPath = DistUtils.getAppPath().getParentFile();
		isValidPlatform = false;

		// Apple specific platform files
		pFile = new File(installPath, "Info.plist");
		if (pFile.isFile() == false)
			pFile = new File(installPath.getParentFile(), "Info.plist");

		if (pFile.isFile() == true)
		{
			isValidPlatform = true;
			
			errMsg = null;
			if (pFile.setWritable(true) == false)
				errMsg = "Failure. No writable permmisions for file: " + pFile;
			else if (AppleFileUtil.updateMaxMem(pFile, targMemSize) == false)
				errMsg = "Failure. Failed to update file: " + pFile;

			if (errMsg != null)
			{
				warnPanel.setTitle("Failed setting Apple properties.");
				warnPanel.setInfo(errMsg);
				warnPanel.setVisible(true);
				return;
			}
		}
		
		// Linux specific platform files
		scriptFile = new File(installPath, "runEcho");
		if (scriptFile.isFile() == true)
		{
			isValidPlatform = true;
			
			errMsg = null;
			if (scriptFile.setWritable(true) == false)
				errMsg = "Failure. No writable permmisions for file: " + scriptFile;
			else if (LinuxFileUtil.updateMaxMem(scriptFile, targMemSize) == false)
				errMsg = "Failure. Failed to update file: " + scriptFile;

			if (errMsg != null)
			{
				warnPanel.setTitle("Failed setting Linux configuration.");
				warnPanel.setInfo(errMsg);
				warnPanel.setVisible(true);
				return;
			}
		}
		
		// Bail if no valid platform found
		if (isValidPlatform == false)
		{
			errMsg = "This does not appear to be a valid DistMaker build. Memory changes will not take effect.";
			
			warnPanel.setTitle("No valid DistMaker platform located.");
			warnPanel.setInfo(errMsg);
			warnPanel.setVisible(true);
			return;
		}
		
		// Update our state vars
		instMemSize = targMemSize;
	}

	/**
	 * Builds the main GUI area
	 */
	private void buildGuiArea()
	{
		JLabel tmpL;
		JComponent tmpComp;
		ByteUnit byteUnit;
		Font smallFont;
		int maxSteps;

		// Form the layout
		setLayout(new MigLayout("", "[right][grow][]", "[]"));

		// Title Area
		titleL = new JLabel("App Memory Configuration", JLabel.CENTER); // this text gets replaced once the curent version
																								// status is known
		add(titleL, "gapbottom 15,growx,span 2,wrap");

//GTextField maxMemTF;
		byteUnit = new ByteUnit(2);
		smallFont = new JTextField().getFont();

		// Stat area
		tmpL = new JLabel("System memory: ");
		maxMemL = new GLabel(byteUnit, smallFont);
		maxMemL.setValue(maxMemSize);
		add(tmpL, "");
		add(maxMemL, "growx,span,wrap");

		tmpL = new JLabel("Current max memory: ");
		currMemL = new GLabel(byteUnit, smallFont);
		currMemL.setValue(currMemSize);
		add(tmpL, "");
		add(currMemL, "growx,span,wrap");

		// Configure area
		tmpComp = GuiUtil.createDivider();
		add(tmpComp, "gaptop 15,gapbottom 10,growx,h 4!,span,wrap");

		maxSteps = (int)((maxMemSize - minMemSize) / MB_SIZE);
		targMemS = new GSlider(this, maxSteps, minMemSize, maxMemSize);
		targMemS.setModelValue(currMemSize);
		add(targMemS, "growx,span,wrap");

		tmpL = new JLabel("Target max memory: ");
		targMemL = new GLabel(byteUnit, smallFont);
		add(tmpL, "");
		add(targMemL, "growx,span,wrap");

		// Info area
		infoTA = GuiUtil.createUneditableTextArea(2, 0);
		add(infoTA, "w 0::,growx,span");

		// Warn Area
		warnTA = GuiUtil.createUneditableTextArea(0, 0);
		add(warnTA, "w 0::,growx,span,wrap");

		// Action area
		applyB = GuiUtil.createJButton("Apply", this, smallFont);
		resetB = GuiUtil.createJButton("Reset", this, smallFont);
		closeB = GuiUtil.createJButton("Close", this, smallFont);
		add(applyB, "align right,span,split 3");
		add(resetB, "");
		add(closeB, "");

		setBorder(new BevelBorder(BevelBorder.RAISED));

		// Configure the slider to be aware of the new memory range
		targMemS.setModelRange(256 * MB_SIZE, maxMemSize);
		targMemS.setModelValue(currMemSize);
	}

	/**
	 * Helper method to compute and initialize relevant state vars.
	 */
	private void initialize()
	{
		List<String> argList;
		String memStr;

		// Retrieve the default max memory value as specified to the JVM
		currMemSize = Runtime.getRuntime().maxMemory();

		// Parse the args to see if we could locate the -Xmx JVM argument
		argList = ManagementFactory.getRuntimeMXBean().getInputArguments();
		for (String aArg : argList)
		{
			if (aArg.startsWith("-Xmx") == true)
			{
				memStr = aArg.toUpperCase().substring(4);
				if (memStr.endsWith("K") == true)
					currMemSize = GuiUtil.readLong(memStr.substring(0, memStr.length() - 1), currMemSize) * KB_SIZE;
				else if (memStr.endsWith("M") == true)
					currMemSize = GuiUtil.readLong(memStr.substring(0, memStr.length() - 1), currMemSize) * MB_SIZE;
				else if (memStr.endsWith("G") == true)
					currMemSize = GuiUtil.readLong(memStr.substring(0, memStr.length() - 1), currMemSize) * GB_SIZE;
				else
					currMemSize = GuiUtil.readLong(memStr, currMemSize);

//System.out.println(" ---> Parsed mem value: " + new ByteUnit(2).getString(currMemSize));
			}
//System.out.println("Arg: " + aArg);
		}

		instMemSize = currMemSize;

//		ManagementFactory.getOperatingSystemMXBean();
	}

	/**
	 * Utility method to round (floor) values to the nearest megabyte. The returned value is guaranteed to be at least 1
	 * megabyte.
	 * <P>
	 * The input value, aSize, should be specified in bytes, and the returned value will be specified in bytes.
	 */
	private long roundToMB(long aSize)
	{
		aSize = aSize / MB_SIZE;
		aSize = aSize * MB_SIZE;

		if (aSize == 0)
			aSize = MB_SIZE;

		return aSize;
	}

	/**
	 * Synchronizes our GUI vars
	 */
	private void updateGui()
	{
		String infoStr;
		boolean isEnabled;

		// Target area
		targMemSize = (long)targMemS.getModelValue();
		targMemSize = roundToMB(targMemSize);

		targMemS.setModelValue(targMemSize);
		targMemL.setValue(targMemSize);

		// Update the infoTA
		if (targMemSize != instMemSize)
			infoStr = "Changes have not been applied.";
		else if (targMemSize == currMemSize)
			infoStr = "There are no changes.";
		else
			infoStr = "Changes will take effect when the application is restarted.";

		infoTA.setText(infoStr);

		// Update action buttons
		isEnabled = targMemSize != instMemSize;
		applyB.setEnabled(isEnabled);

		isEnabled = targMemSize != currMemSize;
		resetB.setEnabled(isEnabled);
	}

}
