package distMaker;

import glum.gui.panel.generic.MessagePanel;
import glum.gui.panel.generic.PromptPanel;
import glum.gui.panel.task.FullTaskPanel;
import glum.io.IoUtil;
import glum.net.Credential;
import glum.reflect.FunctionRunnable;
import glum.task.*;
import glum.unit.DateUnit;
import glum.util.ThreadUtil;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.google.common.base.Joiner;

import distMaker.digest.Digest;
import distMaker.digest.DigestUtils;
import distMaker.gui.PickReleasePanel;
import distMaker.jre.*;
import distMaker.node.*;
import distMaker.platform.AppleUtils;
import distMaker.platform.PlatformUtils;

public class DistMakerEngine
{
	// State vars
	private URL updateSiteUrl;
	private AppRelease currRelease;
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
		msgPanel = new MessagePanel(parentFrame, "Untitled", 700, 400);
		promptPanel = new PromptPanel(parentFrame, "Untitled", 500, 300);

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
	 * Returns the currently running release of this software package.
	 * <P>
	 * Note this method may return null if we are:
	 * <LI>running from a developers environment (Ex: Eclipse IDE)
	 * <LI>If the software application was not properly packaged (or has become corrupt) with DistMaker.
	 */
	public AppRelease getCurrentRelease()
	{
		return currRelease;
	}

	/**
	 * returns
	 * 
	 * @return
	 */
	public UpdateStatus isUpToDate()
	{
		LoggingTask task = new LoggingTask();
		String appName = currRelease.getName();
		List<AppRelease> unsortedReleaseList = DistUtils.getAvailableAppReleases(task, updateSiteUrl, appName, refCredential);

		if (unsortedReleaseList == null)
		{
			// The update check failed, so return a status of false with a message about the problem
			String msg = Joiner.on("; ").join(task.getMessages());
			return new UpdateStatus(msg);
		}
		// Sort the items, and isolate the newest item
		LinkedList<AppRelease> fullList = new LinkedList<>(unsortedReleaseList);
		Collections.sort(fullList);
		AppRelease newestRelease = fullList.removeLast();

		// The check succeeded, so return whether or not the app is up to date.
		return new UpdateStatus(newestRelease.equals(currRelease));
	}

	/**
	 * Sets in the credentials used to access the update site. If either argument is null, then the credentials will be cleared out.
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
		catch(IOException aExp)
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
			System.err.println("Failed to properly parse DistMaker config file: " + cfgFile);
			return;
		}

		// Form the installed Release
		dateUnit = new DateUnit("", "yyyyMMMdd HH:mm:ss");

		buildTime = 0;
		if (buildStr != null)
			buildTime = dateUnit.parseString(buildStr, 0);

		currRelease = new AppRelease(appName, verName, buildTime);

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
		List<AppRelease> fullList;
		AppRelease chosenItem;
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
		fullList = DistUtils.getAvailableAppReleases(aTask, updateSiteUrl, appName, refCredential);
		if (fullList == null)
		{
			aTask.abort();
			return;
		}

		// a successful test has been done, so notify the listener
		listener.checkForNewVersionsPerformed();

		// In case there is only the current version, don't show the update selection panel.
		// Just show a short message that everything is up to date, and abort.
		// (This check used to be in the getAvailableReleases() call above, but I needed
		// that to not throw an error for the case of only one release, so I moved that
		// check here.)
		if (fullList.size() == 1)
		{
			if (fullList.get(0).equals(currRelease))
			{
				// There is only one release out there, and its the same
				// as the one being run, so there is nothing to update.
				String msg = "There are no updates of " + appName + ". Only one release has been made.";
				aTask.infoAppendln(msg);
				aTask.abort();
				return;
			}
		}

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
		catch(Exception aExp)
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
	private boolean downloadRelease(Task aTask, AppRelease aRelease, File destPath)
	{
		AppCatalog staleCat, updateCat;
		Node staleNode, updateNode;
		URL catUrl, staleUrl, updateUrl;
		File catalogFile;
		Task mainTask, tmpTask;
		double progressVal;
		long tmpFileLen;
		boolean isPass;

		try
		{
			staleUrl = DistUtils.getAppPath().toURI().toURL();
			updateUrl = IoUtil.createURL(updateSiteUrl.toString() + "/" + aRelease.getName() + "/" + aRelease.getVersion() + "/delta");
		}
		catch(MalformedURLException aExp)
		{
			aTask.infoAppendln(ThreadUtil.getStackTrace(aExp));
			aExp.printStackTrace();
			return false;
		}

		// Download the update catalog to the (local) delta location (Progress -> [0% - 1%])
		catUrl = IoUtil.createURL(updateUrl.toString() + "/catalog.txt");
		catalogFile = new File(destPath, "catalog.txt");
		if (DistUtils.downloadFile(new PartialTask(aTask, 0.00, 0.01), catUrl, catalogFile, refCredential, -1L, null) == false)
			return false;

		// Load the stale catalog
		catalogFile = new File(DistUtils.getAppPath(), "catalog.txt");
		staleCat = DistUtils.readAppCatalog(aTask, catalogFile, staleUrl);
		if (staleCat == null)
			return false;

		// Load the update catalog
		catalogFile = new File(destPath, "catalog.txt");
		updateCat = DistUtils.readAppCatalog(aTask, catalogFile, updateUrl);
		if (updateCat == null)
			return false;

		// Determine the total number of bytes to be transferred and set up the mainTask
		long releaseSizeFull = 0L, releaseSizeCurr = 0L;
		for (Node aNode : updateCat.getAllNodesList())
		{
			if (aNode instanceof FileNode)
				releaseSizeFull += ((FileNode)aNode).getFileLen();
		}

		// Set up the mainTask for downloading of remote content (Progress -> [1% - 95%])
		mainTask = new PartialTask(aTask, 0.01, 0.94);

		// Ensure our JRE version is sufficient for this release
		JreVersion currJreVer = DistUtils.getJreVersion();
		JreVersion targJreVer = updateCat.getJreVersion();
		if (targJreVer != null && JreVersion.getBetterVersion(targJreVer, currJreVer) != currJreVer)
		{
			List<JreRelease> jreList;

			aTask.infoAppendln("Your current JRE is too old. It will need to be updated!");
			aTask.infoAppendln("\tCurrent  JRE: " + currJreVer.getLabel());
			aTask.infoAppendln("\tMinimun  JRE: " + targJreVer.getLabel() + "\n");

			// Get list of all available JREs
			jreList = JreUtils.getAvailableJreReleases(aTask, updateSiteUrl, refCredential);
			if (jreList == null)
			{
				aTask.infoAppendln("The update site has not had any JREs deployed.");
				aTask.infoAppendln("Please contact the update site adminstartor.");
				return false;
			}
			if (jreList.size() == 0)
			{
				aTask.infoAppendln("No JRE releases found!");
				aTask.infoAppendln("Please contact the update site adminstartor.");
				return false;
			}

			// Retrieve the latest appropriate JreRelease
			String platform = DistUtils.getPlatform();
			jreList = JreUtils.getMatchingPlatforms(jreList, platform);
			if (jreList.size() == 0)
			{
				aTask.infoAppendln("There are no JRE releases available for the platform: " + platform + "!");
				return false;
			}

			JreRelease pickJre = jreList.get(0);
			JreVersion pickJreVer = pickJre.getVersion();

			if (JreVersion.getBetterVersion(pickJreVer, targJreVer) == currJreVer)
			{
				aTask.infoAppendln("The latest available JRE on the update site is not recent enought!");
				aTask.infoAppendln("Minimun required JRE: " + targJreVer.getLabel());
				aTask.infoAppendln("Latest available JRE: " + pickJreVer.getLabel());
				return false;
			}

			// Update the number of bytes to be retrieved to take into account the JRE which we will be downloading
			// and form the appropriate tmpTask
			tmpFileLen = pickJre.getFileLen();
			releaseSizeFull += tmpFileLen;
			tmpTask = new PartialTask(mainTask, mainTask.getProgress(), tmpFileLen / (releaseSizeFull + 0.00));

			// Download the JRE
			Digest targDigest, testDigest;
			targDigest = pickJre.getDigest();
			MessageDigest msgDigest;
			msgDigest = DigestUtils.getDigest(targDigest.getType());
			mainTask.infoAppendln("Downloading JRE... Version: " + pickJre.getVersion().getLabel());
			URL srcUrl = IoUtil.createURL(updateSiteUrl.toString() + "/jre/" + pickJre.getFileName());
			File dstFile = new File(new File(destPath, "jre"), pickJre.getFileName());
			DistUtils.downloadFile(tmpTask, srcUrl, dstFile, refCredential, tmpFileLen, msgDigest);

			// Validate that the JRE was downloaded successfully
			testDigest = new Digest(targDigest.getType(), msgDigest.digest());
			if (targDigest.equals(testDigest) == false)
			{
				aTask.infoAppendln("The download of the JRE appears to be corrupted.");
				aTask.infoAppendln("\tFile: " + dstFile);
				aTask.infoAppendln("\t\tExpected " + targDigest.getDescr());
				aTask.infoAppendln("\t\tRecieved " + testDigest.getDescr() + "\n");
				return false;
			}
			releaseSizeCurr += tmpFileLen;
			progressVal = releaseSizeCurr / (releaseSizeFull + 0.00);
			mainTask.setProgress(progressVal);

			// TODO: Unpack the JRE at the proper location and then delete it
			File jrePath = PlatformUtils.getJreLocation(pickJre);
			try
			{
				int finish_me_and_clean_me;
//				Files.createTempDirectory(dstFile, prefix, attrs)
//				Files.createTempDir();
				
				
				File unpackPath;
				unpackPath = new File(destPath, "jre/unpack");
				unpackPath.mkdirs();
				MiscUtils.unTar(dstFile, unpackPath);
				
				File[] fileArr;
				fileArr = unpackPath.listFiles();
				if (fileArr.length != 1 && fileArr[0].isDirectory() == false)
					throw new Exception("Expected only one (top level) folder to be unpacked. Items extracted: " + fileArr.length + "   Path: " + unpackPath);
				
				File jreUnpackedPath;
				jreUnpackedPath = fileArr[0];
				
				// Moved the unpacked file to the "standardized" jrePath
				jreUnpackedPath.renameTo(jrePath);
//				MiscUtils.unTar(dstFile, jrePath);
				
				// TODO: Remove the unpacked folder...
			}
			catch(Exception aExp)
			{
				aTask.infoAppendln("Failed to untar archive. The update has been aborted.");
				aTask.infoAppendln("\tTar File: " + dstFile);
				aTask.infoAppendln("\tDestination: " + jrePath);
				return false;
			}

			// TODO: Update the launch script or launch config file to point to the proper JRE
			if (PlatformUtils.setJreLocation(jrePath) == false)
			{
				aTask.infoAppendln("Failed to update the configuration to point to the updated JRE!");
				aTask.infoAppendln("\tCurrent JRE: " + currJreVer.getLabel());
				aTask.infoAppendln("\t Chosen JRE: " + pickJreVer.getLabel());
				return false;
			}

			return true;

			// TODO: Eventually remove the old JRE - perhaps from the app launcher
		}

		// Download the individual application files
		mainTask.infoAppendln("Downloading release: " + aRelease.getVersion() + " Nodes: " + updateCat.getAllNodesList().size());
		for (Node aNode : updateCat.getAllNodesList())
		{
			// Bail if we have been aborted
			if (mainTask.isActive() == false)
				return false;

			updateNode = aNode;
			staleNode = staleCat.getNode(updateNode.getFileName());
			tmpFileLen = 0L;
			if (updateNode instanceof FileNode)
				tmpFileLen = ((FileNode)updateNode).getFileLen();
			tmpTask = new PartialTask(mainTask, mainTask.getProgress(), tmpFileLen / (releaseSizeFull + 0.00));

			// Attempt to use the local copy
			isPass = false;
			if (staleNode != null && updateNode.areContentsEqual(staleNode) == true)
			{
				// Note we pass the SilentTask since
				// - This should be fairly fast since this should result in a local disk copy
				// - This may fail, (but the failuer is recoverable and this serves just as an optimization)
//				isPass = staleNode.transferContentTo(tmpTask, refCredential, destPath);
				isPass = staleNode.transferContentTo(new SilentTask(), refCredential, destPath);
				if (isPass == true)
					mainTask.infoAppendln("\t(L) " + staleNode.getFileName());
			}

			// Use the remote update copy, if we were not able to use a local stale copy
			if (isPass == false && mainTask.isActive() == true)
			{
				isPass = updateNode.transferContentTo(tmpTask, refCredential, destPath);
				if (isPass == true)
					mainTask.infoAppendln("\t(R) " + updateNode.getFileName());
			}

			// Log the failure and bail
			if (isPass == false && mainTask.isActive() == true)
			{
				mainTask.infoAppendln("Failed to download from update site.");
				mainTask.infoAppendln("\tSite: " + updateUrl);
				mainTask.infoAppendln("\tFile: " + updateNode.getFileName());
				mainTask.infoAppendln("\tDest: " + destPath);
				return false;
			}

			// Update the progress
			releaseSizeCurr += tmpFileLen;
			progressVal = releaseSizeCurr / (releaseSizeFull + 0.00);
			mainTask.setProgress(progressVal);
		}

		// Update the platform configuration files
		isPass = updatePlatformConfigFiles(aTask, aRelease);

		return isPass;
	}

	/**
	 * Notification that the corresponding application has been fully initialized. This helper method will notify the user on the status of any update.
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
	private void queryUserForInput(Task aTask, File deltaPath, List<AppRelease> fullList)
	{
		AppRelease chosenItem;

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
	private boolean updatePlatformConfigFiles(Task aTask, AppRelease aRelease)
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
			else
				errMsg = AppleUtils.updateVersion(pFile, aRelease.getVersion());

			if (errMsg != null)
			{
				aTask.infoAppendln(errMsg);
				return false;
			}
		}

		return true;
	}

}
