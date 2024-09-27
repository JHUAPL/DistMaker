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
package distMaker.utils;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.MalformedURLException;
import java.net.URL;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import distMaker.DistMakerEngine;
import distMaker.DistUtils;
import distMaker.UpdateCheckListener;
import distMaker.gui.MemoryConfigPanel;
/**
 * Utilities related to handling standard GUI behaviors related to a "deployed" runtime environment, i.e., updates to
 * distributions and setting memory. These utilities support two usage patterns.
 * 
 * 1) There is a singleton object that may be configured once and used to add common GUI behaviors in
 * multiple other contexts. For example, "Update" and "Memory" menu items from the singleton may be added
 * to any number of individual windows. This is the preferred and more current way of doing things. This is
 * a two-step process: first call initialize(deployUrl-string) to set up the singleton, then call
 * instance().addDeployMenus() whenever and however often is desired. This is more flexible because
 * most potential callers do not know the deployUrl and have no convenient way to get it.
 * 
 * 2) An older pattern is to make a single call to the static addDeployMenus(parentFrame, deployUrl) method.
 * This creates a single-use DeployUtils and uses it to add the menu items. This is in use by legacy launchers
 * and there is no compelling reason not to support this usage, but new launchers should use pattern 1.
 * 
 * @author peachjm1
 *
 */
public final class DeployUtils {
	// Not final so can be initialized during start-up but then treated as final thereafter.
	private static DeployUtils mutableInstance = null;

	public static DeployUtils instance() {
		if (mutableInstance == null) throw new UnsupportedOperationException("Cannot return deployment utilities; they were not configured.");
		return mutableInstance;
	}

	public static void initialize(String deployUrl) {
		if (mutableInstance != null) throw new UnsupportedOperationException("Cannot reconfigure deployment utilities.");
		mutableInstance = new DeployUtils(deployUrl);
	}

	private final String deployUrl;

	private DeployUtils(String deployUrl) {
		this.deployUrl = deployUrl;
	}

	public void addDeployMenus(final JFrame parentFrame) {
		JMenuBar menuBar = parentFrame.getJMenuBar();
		if (menuBar == null) {
			menuBar = new JMenuBar();
			parentFrame.setJMenuBar(menuBar);
		}

		DistMakerEngine optionalDme = null;
		if (deployUrl != null) {
			  URL url;
			  try {
				  url = new URL(deployUrl);
			  } catch (MalformedURLException e) {
			     throw new RuntimeException("bad update site url:" + e.getMessage(), e);
			  }
	
			  optionalDme = new DistMakerEngine(parentFrame, url);
		}
		final DistMakerEngine dme = optionalDme;

		JMenu updateMenu = new JMenu("Update");
		JMenuItem updateButton = new JMenuItem("Check for updates now ...");
		
		updateMenu.add(updateButton);
		
		JMenu memoryMenu = new JMenu("Memory");
		JMenuItem configureMemoryButton = new JMenuItem("Configure memory...");
		memoryMenu.add(configureMemoryButton);
		
		updateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if(DistUtils.isDevelopersEnvironment()) {
					JOptionPane.showMessageDialog(parentFrame, "Cannot update tool in a developer environment.");
				} else if (dme == null) {
			    	JOptionPane.showMessageDialog(parentFrame, "Unable to locate updates.");
				} else {
					dme.checkForUpdates(UpdateCheckListener.None);
				}
			}
		});
		
		configureMemoryButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if(DistUtils.isDevelopersEnvironment()) {
					JOptionPane.showMessageDialog(parentFrame, "Cannot configure memory in a developer environment.");
				} else {
					MemoryConfigPanel memPanel = new MemoryConfigPanel(parentFrame);
					memPanel.setVisible(true);
				}
			}
		});
		
		
		
		menuBar.add(updateMenu);
		menuBar.add(memoryMenu);
	}

	public static void addDeployMenus(final JFrame parentFrame, String deployUrl) {		
		DeployUtils utils = new DeployUtils(deployUrl);
		utils.addDeployMenus(parentFrame);
	}	
}
