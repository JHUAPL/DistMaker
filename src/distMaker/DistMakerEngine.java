package distMaker;

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

import distMaker.gui.PickReleasePanel;

public class DistMakerEngine
{
	// State vars
	private URL updateUrl;
	private Release currRelease;
	private Credential refCredential;

	// Gui vars
	private JFrame parentFrame;
	private PickReleasePanel pickVersionPanel;

	public DistMakerEngine(JFrame aParentFrame, URL aUpdateUrl)
	{
		updateUrl = aUpdateUrl;
		refCredential = null;
		parentFrame = aParentFrame;

		initialize();
	}

	/**
	 * Method that will notify the user that updates are being checked for
	 */
	public void checkForUpdates()
	{
		FullTaskPanel taskPanel;
		String appName;

		appName = currRelease.getName();

		// Setup our TaskPanel
		taskPanel = new FullTaskPanel(parentFrame, true, false);
		taskPanel.setTitle(appName + ": Checking for updates...");
		taskPanel.setSize(640, taskPanel.getPreferredSize().height);
		taskPanel.setVisible(true);

		// Launch the actual checking of updates in a separate worker thread
		ThreadUtil.launchRunnable(new FunctionRunnable(this, "checkForUpdatesWorker", taskPanel), "thread-checkForUpdates");
	}

	/**
	 * Notification that the corresponding application has been fully initialized.
	 */
	public void markSystemFullyStarted()
	{
		// TODO: Flush this out
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
		String appName, verName, deployStr;
		long deployTime;

		currRelease = null;
		appName = null;
		verName = null;
		deployStr = null;

		// Locate the official DistMaker configuration file associated with this release
		appPath = DistUtils.getAppPath();
		cfgFile = new File(appPath, "app.cfg");

		// Bail if there is no configuration file
		if (cfgFile.isFile() == false)
			return;

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
				else if (currInstr.equals("-deployDate") == true)
					deployStr = strLine;
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

		if (appName == null || verName == null || deployStr == null)
		{
			System.out.println("Failed to properly parse DistMaker config file: " + cfgFile);
			return;
		}

		// Form the installed Release
		dateUnit = new DateUnit("", "yyyyMMMdd HH:mm:ss");
		deployTime = dateUnit.parseString(deployStr, 0);
		currRelease = new Release(appName, verName, deployTime);

		// Form the PickReleasePanel
		pickVersionPanel = new PickReleasePanel(parentFrame, currRelease);
	}

	/**
	 * Helper method that does the heavy lifting of the checking for updates.
	 * <P>
	 * This method will be called via reflection.
	 */
	@SuppressWarnings("unused")
	private void checkForUpdatesWorker(Task aTask)
	{
		List<Release> fullList;
		Release chosenItem;
		File destPath;
		String appName;
		boolean isPass;

		// Determine the destination where to drop the release
		destPath = new File(DistUtils.getAppPath().getParentFile(), "delta");

		// Status info
		appName = currRelease.getName();
		aTask.infoAppendln("Application: " + appName + " - " + currRelease.getVersion() + '\n');

		// Retrieve the list of available releases
		aTask.infoAppendln("Checking for updates...\n");
		fullList = DistUtils.getAvailableReleases(aTask, updateUrl, appName, refCredential);
		if (fullList == null)
			return;

		// Prompt the user for the Release
		aTask.infoAppendln("Please select the release to install...");
		pickVersionPanel.setConfiguration(fullList);
		pickVersionPanel.setVisibleAsModal();
		chosenItem = pickVersionPanel.getChosenItem();
		if (chosenItem == null)
		{
			aTask.infoAppendln("No release specified. Update has been aborted.");
			aTask.abort();
			return;
		}

		// Log the user chosen action
		aTask.infoAppendln("\tRelease chosen: " + chosenItem.getVersion());
		if (currRelease.getDeployTime() < chosenItem.getDeployTime())
			aTask.infoAppendln("\t" + appName + " will be updated...");
		else
			aTask.infoAppendln("\t" + appName + " will be reverted...");

		// Download the release
		isPass = downloadRelease(aTask, chosenItem, destPath);
		if (isPass == false || aTask.isActive() == false)
		{
			IoUtil.deleteDirectory(destPath);
			aTask.infoAppendln("Application update aborted.");
			return;
		}

		// Notify the user of success
		aTask.infoAppendln(appName + " has been updated to version: " + chosenItem.getDeployTime() + ".");
		aTask.infoAppendln("These updates will become active when " + appName + " is restarted.");
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

		// Compute some baseline vars
		baseUrlStr = updateUrl.toString() + "/" + aRelease.getVersion() + "/";
		clipLen = destPath.getAbsolutePath().length();

		// Download the individual files
		for (File aFile : fileList)
		{
			// Bail if we have been aborted
			if (aTask.isActive() == false)
				return false;

			srcUrlStr = baseUrlStr + aFile.getAbsolutePath().substring(clipLen);
			srcUrl = IoUtil.createURL(srcUrlStr);

			aTask.infoAppendln(srcUrlStr + " -> " + aFile);
			aFile.getParentFile().mkdirs();
			isPass = IoUtil.copyUrlToFile(srcUrl, aFile);// , Username, Password);
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

}
