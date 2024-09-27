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

import static distMaker.platform.MemUtils.GB_SIZE;
import static distMaker.platform.MemUtils.KB_SIZE;
import static distMaker.platform.MemUtils.MB_SIZE;

import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.management.ManagementFactory;
import java.util.List;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.google.common.collect.Range;

import distMaker.ErrorDM;
import distMaker.platform.MemUtils;
import distMaker.platform.PlatformUtils;
import glum.gui.FocusUtil;
import glum.gui.GuiUtil;
import glum.gui.action.ClickAction;
import glum.gui.component.GLabel;
import glum.gui.component.GSlider;
import glum.gui.panel.GlassPanel;
import glum.gui.panel.generic.MessagePanel;
import glum.io.ParseUtil;
import glum.unit.ByteUnit;
import glum.util.ThreadUtil;
import net.miginfocom.swing.MigLayout;

/**
 * User input component that configures the applications memory usage. Changes will not take effect until the
 * application is restarted.
 *
 * @author lopeznr1
 */
public class MemoryConfigPanel extends GlassPanel implements ActionListener, ListSelectionListener
{
	/** Unused - but added to eliminate warning due to poorly designed java.io.Serializable interface. */
	private static final long serialVersionUID = 1L;

	// GUI vars
	private JLabel titleL;
	private GLabel maxMemL, currMemL, targMemL;
	private GSlider targMemS;
	private JButton applyB, closeB, resetB;
	private JTextArea infoTA, warnTA;
	private MessagePanel warnPanel;

	// State vars
	private Range<Double> memSizeRange;
	private long currMemSize;
	private long instMemSize;
	private long targMemSize;

	/**
	 * Constructor where the developer specifies the max heap memory. Be careful about using this method, as if a value
	 * is specified too large, then the program may become non operational on the next run.
	 * <p>
	 * Should the program become non operational then the end user would have to manually configure the config/script
	 * files by hand or a reinstall would be required.
	 */
	public MemoryConfigPanel(Component aParent, long aMaxMemSize)
	{
		super(aParent);

		// State vars
		double minMemSize = roundToMB(256 * MB_SIZE);
		double maxMemSize = roundToMB(aMaxMemSize);
		memSizeRange = Range.closed(minMemSize, maxMemSize);
		initialize();

		// Build the actual GUI
		buildGuiArea();
		warnPanel = new MessagePanel(this, "Memory");
		warnPanel.setSize(400, 150);

		// Set up some keyboard shortcuts
		FocusUtil.addAncestorKeyBinding(this, "ESCAPE", new ClickAction(applyB));
		updateGui();
	}

	/**
	 * Constructor where the DistMaker framework attempts to determine the appropriate maxMexSize. Should, the DistMaker
	 * framework fail to determine the installed system memory, then 4GB will be assumed as the installed system memory.
	 */
	public MemoryConfigPanel(Component aParent)
	{
		this(aParent, MemUtils.getInstalledSystemMemory());
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
		// Retrieve the targMemSize
		targMemSize = (long) targMemS.getModelValue();
		targMemSize = roundToMB(targMemSize);

		try
		{
			// Delegate the updating of the memory
			PlatformUtils.setMaxHeapMem(targMemSize);
		}
		catch (ErrorDM aExp)
		{
			String subjectStr = aExp.getSubject();
			if (subjectStr == null)
				subjectStr = "Application Configuration Error";

			String messageStr = aExp.getMessage();
			if (aExp.getCause() != null)
				messageStr += "\n\n" + ThreadUtil.getStackTrace(aExp.getCause());

			// Show the user the details of the failure
			warnPanel.setTitle(subjectStr);
			warnPanel.setInfo(messageStr);
			warnPanel.setVisible(true);
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
		double minMemSize = memSizeRange.lowerEndpoint();
		double maxMemSize = memSizeRange.upperEndpoint();
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

		maxSteps = (int) ((maxMemSize - minMemSize) / MB_SIZE);
		targMemS = new GSlider(this, memSizeRange, maxSteps);
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
		add(warnTA, "w 0:325:,growx,span,wrap");

		// Action area
		applyB = GuiUtil.createJButton("Apply", this, smallFont);
		resetB = GuiUtil.createJButton("Reset", this, smallFont);
		closeB = GuiUtil.createJButton("Close", this, smallFont);
		add(applyB, "align right,span,split 3");
		add(resetB, "");
		add(closeB, "");

		// Configure the slider to be aware of the new memory range
		targMemS.setModelRange(memSizeRange);
		targMemS.setModelValue(currMemSize);
	}

	/**
	 * Helper method to compute and initialize relevant state vars.
	 */
	private void initialize()
	{
		List<String> argL;
		String memStr;

		// Retrieve the default max memory value as specified to the JVM
		currMemSize = Runtime.getRuntime().maxMemory();

		// Parse the args to see if we could locate the -Xmx JVM argument
		argL = ManagementFactory.getRuntimeMXBean().getInputArguments();
		for (String aArg : argL)
		{
			if (aArg.startsWith("-Xmx") == true)
			{
				memStr = aArg.toUpperCase().substring(4);
				if (memStr.endsWith("K") == true)
					currMemSize = ParseUtil.readLong(memStr.substring(0, memStr.length() - 1), currMemSize) * KB_SIZE;
				else if (memStr.endsWith("M") == true)
					currMemSize = ParseUtil.readLong(memStr.substring(0, memStr.length() - 1), currMemSize) * MB_SIZE;
				else if (memStr.endsWith("G") == true)
					currMemSize = ParseUtil.readLong(memStr.substring(0, memStr.length() - 1), currMemSize) * GB_SIZE;
				else
					currMemSize = ParseUtil.readLong(memStr, currMemSize);

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
	 * <p>
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
		targMemSize = (long) targMemS.getModelValue();
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
