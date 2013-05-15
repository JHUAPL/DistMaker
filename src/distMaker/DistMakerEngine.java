package distMaker;

import glum.gui.panel.generic.MessagePanel;
import glum.gui.panel.generic.PromptPanel;
import glum.gui.panel.task.FullTaskPanel;
import glum.io.IoUtil;
import glum.net.Credential;
import glum.reflect.FunctionRunnable;
import glum.task.Task;
import glum.unit.DateUnit;
import glum.util.ThreadUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import distMaker.gui.PickReleasePanel;
import distMaker.node.Node;
import distMaker.platform.AppleUtils;

public class DistMakerEngine
{
	// State vars
	private URL updateSiteUrl;
	private Release currRelease;
	private Credential refCredential;

	// Gui vars
	private JFrame parentFrame;
	private MessagePanel msgPanel;
	private PromptPanel promptPanel;
	private PickReleasePanel pickVersionPanel;

	public DistMakerEngine(JFrame aParentFrame, URL aUpdateSiteUrl)
	{
		updateSiteUrl = aUpdateSiteUrl;
		currRelease = null;
		refCredential = null;

		parentFrame = aParentFrame;
		msgPanel = new MessagePanel(parentFrame);
		msgPanel.setSize(700, 400);
		promptPanel = new PromptPanel(parentFrame);
		promptPanel.setSize(500, 300);

		initialize();
	}

	/**
	 * Method that will notify the user that updates are being checked for
	 */
	public void checkForUpdates(UpdateCheckListener listener)
	{
		FullTaskPanel taskPanel;
		File installPath;
		String appName, infoMsg;

		// Bail if we do not have a valid release
		if (currRelease == null)
		{
			if (DistUtils.isDevelopersEnvironment() == true)
				displayNotice("Updates are not possible in a developer environment.");
			else
				displayNotice(null);
			return;
		}
		appName = currRelease.getName();

		// Determine the installation path and ensure the entire tree is writable
		installPath = DistUtils.getAppPath().getParentFile();
		if (DistUtils.isFullyWriteable(installPath) == false)
		{
			infoMsg = "The install tree, " + installPath + ", is not completely writable.\n";
			infoMsg += "Please run as the proper user or ensure you are running via writeable media.";
			displayNotice(infoMsg);
			return;
		}

		// Setup our TaskPanel
		taskPanel = new FullTaskPanel(parentFrame, true, false);
		taskPanel.setTitle(appName + ": Checking for updates...");
//		taskPanel.setSize(680, taskPanel.getPreferredSize().height);
		taskPanel.setSize(680, 400);
		taskPanel.setTabSize(2);
		taskPanel.setVisible(true);

		// Launch the actual checking of updates in a separate worker thread
		ThreadUtil.launchRunnable(new FunctionRunnable(this, "checkForUpdatesWorker", taskPanel, listener), "thread-checkForUpdates");
	}

	/**
	 * returns 
	 * @return
	 */
	public UpdateStatus isUpToDate()
	{
	   LoggingTask task = new LoggingTask();
	   String appName = currRelease.getName();
	   List<Release> unsortedReleaseList = DistUtils.getAvailableReleases(task, updateSiteUrl, appName, refCredential);

	   if (unsortedReleaseList == null) {
	      // The update check failed, so return a status of false with a message about the problem
	      String msg = Joiner.on("; ").join(task.getMessages());
	      return new UpdateStatus(msg);
	   }
      // Sort the items, and isolate the newest item
      LinkedList<Release> fullList = Lists.newLinkedList(unsortedReleaseList);
      Collections.sort(fullList);
      Release newestRelease = fullList.removeLast();

      // The check succeeded, so return wether or not the app is up to date.
      return new UpdateStatus(newestRelease.equals(currRelease));
	}
	
	
	/**
	 * Sets in the credentials used to access the update site. If either argument is null, then the credentials will be
	 * cleared out.
	 */
	public void setCredentials(String aUsername, char[] aPassword)
	{
		refCredential = null;
		if (aUsername == null || aPassword == null)
			return;

		refCredential = new Credential(aUsername, aPassword);
	}

	/**
	 * Helper method to fully set up this object
	 */
	private void initialize()
	{
		File appPath, cfgFile;
		BufferedReader br;
		DateUnit dateUnit;
		String currInstr, strLine;
		String appName, verName, buildStr;
		long buildTime;

		currRelease = null;
		appName = null;
		verName = null;
		buildStr = null;

		// Locate the official DistMaker configuration file associated with this release
		appPath = DistUtils.getAppPath();
		cfgFile = new File(appPath, "app.cfg");

		// Bail if there is no configuration file
		if (cfgFile.isFile() == false)
		{
			// Alert the user to the incongruence if this is not a developer's build
			if (DistUtils.isDevelopersEnvironment() == false)
				displayNotice(null);

			return;
		}

		// Read in the configuration file
		br = null;
		try
		{
			br = new BufferedReader(new InputStreamReader(new FileInputStream(cfgFile)));

			// Read the lines
			currInstr = "None";
			while (true)
			{
				strLine = br.readLine();

				// Bail once we get to the end of the file
				if (strLine == null)
					break;

				// Skip empty lines / comments
				if (strLine.isEmpty() == true || strLine.startsWith("#") == true)
					; // Nothing to do
				// Record the (current) instruction
				else if (strLine.startsWith("-") == true)
					currInstr = strLine;
				// Process the instruction
				else if (currInstr.equals("-name") == true)
					appName = strLine;
				else if (currInstr.equals("-version") == true)
					verName = strLine;
				else if (currInstr.equals("-buildDate") == true)
					buildStr = strLine;
			}

		}
		catch (IOException aExp)
		{
			aExp.printStackTrace();
		}
		finally
		{
			IoUtil.forceClose(br);
		}

		if (appName == null || verName == null)
		{
			displayNotice(null);
			System.out.println("Failed to properly parse DistMaker config file: " + cfgFile);
			return;
		}

		// Form the installed Release
		dateUnit = new DateUnit("", "yyyyMMMdd HH:mm:ss");

		buildTime = 0;
		if (buildStr != null)
			buildTime = dateUnit.parseString(buildStr, 0);

		currRelease = new Release(appName, verName, buildTime);

		// Notify the user, if the application has been successfully updated
		markSystemFullyStarted();

		// Form the PickReleasePanel
		pickVersionPanel = new PickReleasePanel(parentFrame, currRelease);
		pickVersionPanel.setSize(550, 500); // 320, 350);
	}

	/**
	 * Helper method that does the heavy lifting of the checking for updates.
	 * <P>
	 * This method will be called via reflection.
	 */
	@SuppressWarnings("unused")
	private void checkForUpdatesWorker(FullTaskPanel aTask, UpdateCheckListener listener)
	{
		List<Release> fullList;
		Release chosenItem;
		File installPath, deltaPath;
		String appName;
		boolean isPass;

		// Determine the path to download updates
		installPath = DistUtils.getAppPath().getParentFile();
		deltaPath = new File(installPath, "delta");

		// Status info
		appName = currRelease.getName();
		aTask.infoAppendln("Application: " + appName + " - " + currRelease.getVersion());

		// Retrieve the list of available releases
		aTask.infoAppendln("Checking for updates...\n");
		fullList = DistUtils.getAvailableReleases(aTask, updateSiteUrl, appName, refCredential);
		if (fullList == null)
		{
			aTask.abort();
			return;
		}
		
		// a successful test has been done, so notify the listener
		listener.checkForNewVersionsPerformed();
		
		// Hide the taskPanel
		aTask.setVisible(false);

		// Prompt the user for the Release
		aTask.infoAppendln("Please select the release to install...");
		try
		{
			FunctionRunnable aFuncRunnable;

			aFuncRunnable = new FunctionRunnable(this, "queryUserForInput", aTask, deltaPath, fullList);
			SwingUtilities.invokeAndWait(aFuncRunnable);
		}
		catch (Exception aExp)
		{
			aExp.printStackTrace();
		}

		// Bail if the task has been aborted
		if (aTask.isActive() == false)
			return;

		// Retrieve the chosen item
		chosenItem = pickVersionPanel.getChosenItem();
		if (chosenItem == null)
			return;
		
		// Reshow the taskPanel
		aTask.setVisible(true);

		// Log the user chosen action
		aTask.infoAppendln("\tRelease chosen: " + chosenItem.getVersion());
		if (currRelease.getBuildTime() < chosenItem.getBuildTime())
			aTask.infoAppendln("\t" + appName + " will be updated...");
		else
			aTask.infoAppendln("\t" + appName + " will be reverted...");

		// Form the destination path
		isPass = deltaPath.mkdirs();
		if (isPass == false || aTask.isActive() == false)
		{
			aTask.infoAppendln("Failed to create delta path: " + deltaPath);
			aTask.infoAppendln("Application update aborted.");
			aTask.abort();
			return;
		}

		// Download the release
		isPass = downloadRelease(aTask, chosenItem, deltaPath);
		if (isPass == false || aTask.isActive() == false)
		{
			IoUtil.deleteDirectory(deltaPath);
			aTask.infoAppendln("Application update aborted.");
			aTask.abort();
			return;
		}

		// Notify the user of success
		aTask.infoAppendln(appName + " has been updated to version: " + chosenItem.getVersion() + ".");
		aTask.infoAppendln("These updates will become active when " + appName + " is restarted.");
		aTask.setProgress(1.0);
	}

	/**
	 * Helper method to display a DistMaker information notice
	 */
	private void displayNotice(String aMsg)
	{
		// Default message
		if (aMsg == null)
		{
			aMsg = "This application does not appear to be a properly configured DistMaker application.\n\n";
			aMsg += "Please check installation configuration.";
		}

		// Display the message
		msgPanel.setTitle("Application Updater");
		msgPanel.setInfo(aMsg);
		msgPanel.setVisible(true);
	}

	/**
	 * Helper method to download the specified release.
	 * <P>
	 * Returns true if the release was downloaded properly.
	 */
	private boolean downloadRelease(Task aTask, Release aRelease, File destPath)
	{
		Map<String, Node> staleMap, updateMap;
		Node staleNode, updateNode;
		URL catUrl, staleUrl, updateUrl;
		File catalogFile;
		boolean isPass;

		try
		{
			staleUrl = DistUtils.getAppPath().toURI().toURL();
			updateUrl = IoUtil.createURL(updateSiteUrl.toString() + "/" + aRelease.getName() + "/" + aRelease.getVersion() + "/delta");
		}
		catch (MalformedURLException aExp)
		{
			aTask.infoAppendln(ThreadUtil.getStackTrace(aExp));
			aExp.printStackTrace();
			return false;
		}

		// Download the update catalog to the (local) delta location
		catUrl = IoUtil.createURL(updateUrl.toString() + "/catalog.txt");
		catalogFile = new File(destPath, "catalog.txt");
		if (DistUtils.downloadFile(aTask, catUrl, catalogFile, refCredential) == false)
			return false;

		// Load the map of stale nodes
		catalogFile = new File(DistUtils.getAppPath(), "catalog.txt");
		staleMap = DistUtils.readCatalog(aTask, catalogFile, staleUrl);
		if (staleMap == null)
			return false;

		// Load the map of update nodes
		catalogFile = new File(destPath, "catalog.txt");
		updateMap = DistUtils.readCatalog(aTask, catalogFile, updateUrl);
		if (updateMap == null)
			return false;

		// Download the individual files
		aTask.infoAppendln("Downloading release: " + aRelease.getVersion() + " Nodes: " + updateMap.size());
		for (String aFileName : updateMap.keySet())
		{
			// Bail if we have been aborted
			if (aTask.isActive() == false)
				return false;

			updateNode = updateMap.get(aFileName);
			staleNode = staleMap.get(aFileName);

			// Attempt to use the local copy
			isPass = false;
			if (staleNode != null && updateNode.areContentsEqual(staleNode) == true)
			{
				isPass = staleNode.transferContentTo(aTask, refCredential, destPath);
				if (isPass == true)
					aTask.infoAppendln("\t(L) " + staleNode.getFileName());
			}

			// Use the remote update copy, if we were not able to use a local stale copy
			if (isPass == false && aTask.isActive() == true)
			{
				isPass = updateNode.transferContentTo(aTask, refCredential, destPath);
				if (isPass == true)
					aTask.infoAppendln("\t(R) " + updateNode.getFileName());
			}

			// Log the failure and bail
			if (isPass == false && aTask.isActive() == true)
			{
				aTask.infoAppendln("Failed to download from update site.");
				aTask.infoAppendln("\tSite: " + updateUrl);
				aTask.infoAppendln("\tFile: " + updateNode.getFileName());
				aTask.infoAppendln("\tDest: " + destPath);
				return false;
			}
		}

		// Update the platform configuration files
		isPass = updatePlatformConfigFiles(aTask, aRelease);

		return isPass;
	}

	/**
	 * Notification that the corresponding application has been fully initialized. This helper method will notify the
	 * user on the status of any update.
	 */
	private void markSystemFullyStarted()
	{
		String appName, msg;
		int updateCode;

		updateCode = DistUtils.getUpdateCode();
		appName = currRelease.getName();

		// No update
		if (updateCode == 0)
		{
			return;
		}
		// Update passed
		else if (updateCode == 1)
		{
			msg = "The application, " + currRelease.getName() + ", has been updated to ";
			msg += "version: " + currRelease.getVersion();
		}
		// Update failed
		else
		// if (updateCode == 2)
		{
			msg = "There was an issue while updating the " + appName + " application.\n";
			msg += "The application, " + appName + ", is currently at version: " + currRelease.getVersion() + "\n\n";

			msg += DistUtils.getUpdateMsg();
		}

		displayNotice(msg);
	}

	/**
	 * Helper method that prompts the user for forms of input depending on the state of the App
	 * <P>
	 * This method will be called via reflection.
	 */
	@SuppressWarnings("unused")
	private void queryUserForInput(Task aTask, File deltaPath, List<Release> fullList)
	{
		Release chosenItem;

		// Query the user, if the wish to destroy the old update
		if (deltaPath.isDirectory() == true)
		{
			promptPanel.setTitle("Overwrite recent update?");
			promptPanel.setInfo("An update has already been downloaded... If you proceed this update will be removed. Proceed?");
			promptPanel.setVisibleAsModal();
			if (promptPanel.isAccepted() == false)
			{
				aTask.infoAppendln("Previous update will not be overwritten.");
				aTask.abort();
				return;
			}

			// Remove the retrieved update, and restore the platform configuration files to this (running) release
			// It is necessary to do this, since the user may later cancel the update request and it is important to
			// leave the program and configuration files in a stable state.
			IoUtil.deleteDirectory(deltaPath);
			updatePlatformConfigFiles(aTask, currRelease);
		}

		// Query the user of the version to update to
		pickVersionPanel.setConfiguration(fullList);
		pickVersionPanel.setVisibleAsModal();
		chosenItem = pickVersionPanel.getChosenItem();
		if (chosenItem == null)
		{
			aTask.infoAppendln("No release specified. Update has been aborted.");
			aTask.abort();
			return;
		}
	}

	/**
	 * Helper method to update platform specific configuration files
	 */
	private boolean updatePlatformConfigFiles(Task aTask, Release aRelease)
	{
		File installPath, pFile;
		String errMsg;

		// Get the top level install path
		installPath = DistUtils.getAppPath().getParentFile();

		// Apple specific platform files
		pFile = new File(installPath, "Info.plist");
		if (pFile.isFile() == false)
			pFile = new File(installPath.getParentFile(), "Info.plist");

		if (pFile.isFile() == true)
		{
			errMsg = null;
			if (pFile.setWritable(true) == false)
				errMsg = "Failure. No writable permmisions for file: " + pFile;
			else if (AppleUtils.updateVersion(pFile, aRelease.getVersion()) == false)
				errMsg = "Failure. Failed to update file: " + pFile;

			if (errMsg != null)
			{
				aTask.infoAppendln(errMsg);
				return false;
			}
		}

		return true;
	}

}
