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

import java.io.*;
import java.net.URL;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import distMaker.gui.PickReleasePanel;

public class DistMakerEngine
{
	// State vars
	private URL updateUrl;
	private Release currRelease;
	private Credential refCredential;

	// Gui vars
	private JFrame parentFrame;
	private MessagePanel msgPanel;
	private PromptPanel promptPanel;
	private PickReleasePanel pickVersionPanel;

	public DistMakerEngine(JFrame aParentFrame, URL aUpdateUrl)
	{
		updateUrl = aUpdateUrl;
		currRelease = null;
		refCredential = null;

		parentFrame = aParentFrame;
		msgPanel = new MessagePanel(parentFrame);
		msgPanel.setSize(375, 180);
		promptPanel = new PromptPanel(parentFrame);
		promptPanel.setSize(300, 150);

		initialize();
	}

	/**
	 * Method that will notify the user that updates are being checked for
	 */
	public void checkForUpdates()
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
		taskPanel.setSize(640, taskPanel.getPreferredSize().height);
		taskPanel.setTabSize(2);
		taskPanel.setVisible(true);

		// Launch the actual checking of updates in a separate worker thread
		ThreadUtil.launchRunnable(new FunctionRunnable(this, "checkForUpdatesWorker", taskPanel), "thread-checkForUpdates");
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

				// Update the current instruction, if one specified
				if (strLine.startsWith("-") == true)
					currInstr = strLine;
				// Skip empty lines / comments
				else if (strLine.isEmpty() == true || strLine.startsWith("#") == true)
					; // Nothing to do
				// Process the name instruction
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
	}

	/**
	 * Helper method that does the heavy lifting of the checking for updates.
	 * <P>
	 * This method will be called via reflection.
	 */
	@SuppressWarnings("unused")
	private void checkForUpdatesWorker(final Task aTask)
	{
		final List<Release> fullList;
		Release chosenItem;
		final File installPath, destPath;
		String appName;
		boolean isPass;

		// Determine the path to download updates
		installPath = DistUtils.getAppPath().getParentFile();
		destPath = new File(installPath, "delta");

		// Status info
		appName = currRelease.getName();
		aTask.infoAppendln("Application: " + appName + " - " + currRelease.getVersion());

		// Retrieve the list of available releases
		aTask.infoAppendln("Checking for updates...\n");
		fullList = DistUtils.getAvailableReleases(aTask, updateUrl, appName, refCredential);
		if (fullList == null)
		{
			aTask.abort();
			return;
		}

		// Prompt the user for the Release
		aTask.infoAppendln("Please select the release to install...");
		try
		{
			SwingUtilities.invokeAndWait(new Runnable()
			{
				@Override
				public void run()
				{
					// Query the user, if the wish to destroy the old update
					if (destPath.isDirectory() == true)
					{
						promptPanel.setTitle("Overwrite recent update?");
						promptPanel.setInfo("An update has already been downloaded... If you proceed this update will be removed. Proceed?");
						promptPanel.setVisibleAsModal();
						if (promptPanel.isAccepted() == false)
						{
							aTask.abort();
							return;
						}

						IoUtil.deleteDirectory(destPath);
					}

					// Query the user of the version to update to
					pickVersionPanel.setConfiguration(fullList);
					pickVersionPanel.setVisibleAsModal();
				}
			});
		}
		catch (Exception aExp)
		{
			aExp.printStackTrace();
		}
//		pickVersionPanel.setVisibleAsModal();
		chosenItem = pickVersionPanel.getChosenItem();
		if (chosenItem == null)
		{
			aTask.infoAppendln("No release specified. Update has been aborted.");
			aTask.abort();
			return;
		}

		// Log the user chosen action
		aTask.infoAppendln("\tRelease chosen: " + chosenItem.getVersion());
		if (currRelease.getBuildTime() < chosenItem.getBuildTime())
			aTask.infoAppendln("\t" + appName + " will be updated...");
		else
			aTask.infoAppendln("\t" + appName + " will be reverted...");

		// Download the release
		isPass = downloadRelease(aTask, chosenItem, destPath);
		if (isPass == false || aTask.isActive() == false)
		{
			IoUtil.deleteDirectory(destPath);
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
	 * Helper method to download the specified release.
	 * <P>
	 * Returns true if the release was downloaded properly.
	 */
	private boolean downloadRelease(Task aTask, Release aRelease, File destPath)
	{
		List<File> fileList;
		String baseUrlStr, srcUrlStr;
		URL srcUrl;
		int clipLen;
		boolean isPass;

		// Retrieve the list of files to download
		fileList = DistUtils.getFileListForRelease(aTask, updateUrl, aRelease, destPath, refCredential);
		if (fileList == null)
			return false;

		// Compute some baseline vars
		baseUrlStr = updateUrl.toString() + "/" + aRelease.getName() + "/" + aRelease.getVersion() + "/" + "delta/";
		clipLen = destPath.getAbsolutePath().length() + 1;

		// Download the individual files
		aTask.infoAppendln("Downloading release: " + aRelease.getVersion() + " Files: " + fileList.size());
		for (File aFile : fileList)
		{
			// Bail if we have been aborted
			if (aTask.isActive() == false)
				return false;

			srcUrlStr = baseUrlStr + aFile.getAbsolutePath().substring(clipLen);
			srcUrl = IoUtil.createURL(srcUrlStr);

//			aTask.infoAppendln("\t" + srcUrlStr + " -> " + aFile);
			aTask.infoAppendln("\t(U) " + aFile);
			aFile.getParentFile().mkdirs();
			isPass = DistUtils.downloadFile(aTask, srcUrl, aFile, refCredential);
			if (isPass == false)
			{
				aFile.delete();
				aTask.infoAppendln("Failed to download resource: " + srcUrl);
				aTask.infoAppendln("\tSource: " + srcUrlStr);
				aTask.infoAppendln("\tDest: " + aFile);
				return false;
			}
		}

		return true;
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

}
