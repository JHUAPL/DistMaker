package distMaker;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
import distMaker.platform.PlatformUtils;
import distMaker.utils.Version;
import glum.gui.panel.generic.MessagePanel;
import glum.gui.panel.generic.PromptPanel;
import glum.gui.panel.task.FullTaskPanel;
import glum.io.IoUtil;
import glum.net.Credential;
import glum.task.*;
import glum.unit.DateUnit;
import glum.util.ThreadUtil;

public class DistMakerEngine
{
	// Constants
	private final String NonDistmakerAppMsg = "This application does not appear to be a properly configured DistMaker application.\n\n" + "Please check installation configuration.";

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
	public void checkForUpdates(UpdateCheckListener aListener)
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
				displayNotice(NonDistmakerAppMsg);
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
		Runnable tmpRunnable = () -> checkForUpdatesWorker(taskPanel, aListener);
		ThreadUtil.launchRunnable(tmpRunnable, "thread-checkForUpdates");
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
	 * Returns the URL where software updates for this application are retrieved from.
	 */
	public URL getUpdateSite()
	{
		return updateSiteUrl;
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
				displayNotice(NonDistmakerAppMsg);

			return;
		}

		// Read in the configuration file
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(cfgFile))))
		{
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

		if (appName == null || verName == null)
		{
			displayNotice(NonDistmakerAppMsg);
			System.err.println("Failed to properly parse DistMaker config file: " + cfgFile);
			return;
		}

		// Form the installed Release
		dateUnit = new DateUnit("", "yyyyMMMdd HH:mm:ss");

		buildTime = 0;
		if (buildStr != null)
			buildTime = dateUnit.parseString(buildStr, 0);

		currRelease = new AppRelease(appName, verName, buildTime);

		// Form the PickReleasePanel
		pickVersionPanel = new PickReleasePanel(parentFrame, currRelease);
		pickVersionPanel.setSize(550, 500);

		// Notify the user of (any) update results
		showUpdateResults();
	}

	/**
	 * Helper method that does the heavy lifting of the checking for updates.
	 * <P>
	 * This method will be called via reflection.
	 */
	private void checkForUpdatesWorker(FullTaskPanel aTask, UpdateCheckListener aListener)
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
		aListener.checkForNewVersionsPerformed();

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
			Runnable tmpRunnable = () -> queryUserForInput(aTask, deltaPath, fullList);
			SwingUtilities.invokeAndWait(tmpRunnable);
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
		try
		{
			isPass = downloadAppRelease(aTask, chosenItem, deltaPath);
		}
		catch(Throwable aThrowable)
		{
			IoUtil.deleteDirectory(deltaPath);
			aTask.infoAppendln("An error occurred while trying to perform an update.");
			aTask.infoAppendln("Application update aborted.");
			aTask.infoAppendln("\nStackTrace:\n" + ThreadUtil.getStackTraceClassic(aThrowable));
			aTask.abort();
			return;
		}
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
	 * Helper method to show an informative message on msgPanel.
	 */
	private void displayNotice(String aMsg)
	{
		Runnable silentRunnable = () ->
		{
			; // Nothing to do
		};

		// Delegate to displayNoticeAndExecute
		displayNoticeAndExecute(aMsg, silentRunnable, false);
	}

	/**
	 * Helper method to show an informative message on msgPanel and execute the specified runnable.
	 * <P>
	 * If isModal == true then aRunnable will only be executed after the msgPanel has been accepted.
	 * <P>
	 * The runnable will not be run until the parentFrame is visible.
	 */
	private void displayNoticeAndExecute(final String aMsg, final Runnable aRunnable, final boolean isModal)
	{
		// Transform tabs to 3 spaces
		final String infoMsg = aMsg.replace("\t", "    ");

		// If the parentFrame is not visible then execute the code once it is made visible
		if (parentFrame.isVisible() == false)
		{
			parentFrame.addComponentListener(new ComponentAdapter()
			{
				@Override
				public void componentShown(ComponentEvent aEvent)
				{
					// Show the message panel and wait for user to close panel
					msgPanel.setTitle("Application Updater");
					msgPanel.setInfo(infoMsg);
					if (isModal == true)
						msgPanel.setVisibleAsModal();
					else
						msgPanel.setVisible(true);

					// Execute aRunnable's logic
					aRunnable.run();

					// Deregister for events after the parentFrame is made visible
					parentFrame.removeComponentListener(this);
				}
			});

			return;
		}

		// Show the message panel and wait for user to close panel
		msgPanel.setTitle("Application Updater");
		msgPanel.setInfo(infoMsg);
		if (isModal == true)
			msgPanel.setVisibleAsModal();
		else
			msgPanel.setVisible(true);

		// Execute aRunnable's logic
		aRunnable.run();
	}

	/**
	 * Helper method to download the specified release.
	 * <P>
	 * Returns true if the release was downloaded properly.
	 */
	private boolean downloadAppRelease(Task aTask, AppRelease aRelease, File aDestPath)
	{
		AppCatalog staleCat, updateCat;
		Node staleNode, updateNode;
		URL catUrl, staleUrl, updateUrl;
		File catalogFile;
		Task mainTask, tmpTask;
		double progressVal;
		long tmpFileLen;

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

		// Load the stale catalog
		catalogFile = new File(DistUtils.getAppPath(), "catalog.txt");
		staleCat = DistUtils.readAppCatalog(aTask, catalogFile, staleUrl);
		if (staleCat == null)
			return false;

		// Download the update app catalog to the (local) delta location (Progress -> [0% - 1%])
		File appNewPath = new File(aDestPath, "app");
		appNewPath.mkdirs();
		catUrl = IoUtil.createURL(updateUrl.toString() + "/catalog.txt");
		catalogFile = new File(appNewPath, "catalog.txt");
		if (DistUtils.downloadFile(new PartialTask(aTask, 0.00, 0.01), catUrl, catalogFile, refCredential, -1L, null) == false)
			return false;

		// Load the update catalog
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

		// Ensure our JRE version is compatible for this release
		JreRelease targJre = null;
		AppLauncherRelease targAppLauncher = null;
		JreVersion currJreVer = DistUtils.getJreVersion();
		Version currAppLauncherVer = DistUtils.getAppLauncherVersion();
		if (updateCat.isJreVersionCompatible(currJreVer) == false)
		{
			// Bail if we failed to download a compatible JRE
			JreUpdateResult tmpJreUpdateResult;
			tmpJreUpdateResult = downloadJreUpdate(mainTask, updateCat, aDestPath, releaseSizeFull);
			if (tmpJreUpdateResult == null)
				return false;

			targJre = tmpJreUpdateResult.targJre;
			targAppLauncher = tmpJreUpdateResult.targAppLauncher;

			// Update the progress to reflect the downloaded / updated JRE
			releaseSizeCurr += targJre.getFileLen();
			releaseSizeFull += targJre.getFileLen();
			progressVal = releaseSizeCurr / (releaseSizeFull + 0.00);
			mainTask.setProgress(progressVal);
		}

		// Download the individual application files
		mainTask.infoAppendln("Downloading release: " + aRelease.getVersion() + " Nodes: " + updateCat.getAllNodesList().size());
		for (Node aNode : updateCat.getAllNodesList())
		{
			boolean isPass;

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
				// - This may fail, (but the failure is recoverable and this serves just as an optimization)
//				isPass = staleNode.transferContentTo(tmpTask, refCredential, destPath);
				isPass = staleNode.transferContentTo(new SilentTask(), refCredential, appNewPath);
				if (isPass == true)
					mainTask.infoAppendln("\t(L) " + staleNode.getFileName());
			}

			// Use the remote update copy, if we were not able to use a local stale copy
			if (isPass == false && mainTask.isActive() == true)
			{
				isPass = updateNode.transferContentTo(tmpTask, refCredential, appNewPath);
				if (isPass == true)
					mainTask.infoAppendln("\t(R) " + updateNode.getFileName());
			}

			// Log the failure and bail
			if (isPass == false && mainTask.isActive() == true)
			{
				mainTask.infoAppendln("Failed to download from update site.");
				mainTask.infoAppendln("\tSite: " + updateUrl);
				mainTask.infoAppendln("\tFile: " + updateNode.getFileName());
				mainTask.infoAppendln("\tDest: " + appNewPath);
				return false;
			}

			// Update the progress
			releaseSizeCurr += tmpFileLen;
			progressVal = releaseSizeCurr / (releaseSizeFull + 0.00);
			mainTask.setProgress(progressVal);
		}
		mainTask.infoAppendln("Finished downloading release.\n");

		// Update the platform configuration files
		try
		{
			PlatformUtils.updateAppRelease(aRelease);
		}
		catch(ErrorDM aExp)
		{
			aTask.infoAppendln("Failed updating application configuration.");
			MiscUtils.printErrorDM(aTask, aExp, 1);
			return false;
		}

		// Retrieve the reference to the appCfgFile
		File appCfgFile = PlatformUtils.getConfigurationFile();

		// Create the delta.cmd file which provides the Updater with the clean activities to perform
		// (based on fail / pass conditions)
		File deltaCmdFile = new File(aDestPath, "delta.cmd");
		try (FileWriter tmpFW = new FileWriter(deltaCmdFile))
		{
			File rootPath = DistUtils.getAppPath().getParentFile();

			// Write the section: fail
			tmpFW.write("# Define the fail section (clean up for failure)\n");
			tmpFW.write("sect,fail\n");
			if (targJre != null)
			{
				JreVersion targJreVer = targJre.getVersion();
				tmpFW.write("copy," + "delta/" + appCfgFile.getName() + ".old," + MiscUtils.getRelativePath(rootPath, appCfgFile) + "\n");
				tmpFW.write("reboot,trash," + PlatformUtils.getJreLocation(targJreVer) + "\n");
			}
			if (targAppLauncher != null)
			{
				Version targAppLauncherVer = targAppLauncher.getVersion();
				tmpFW.write("reboot,trash," + PlatformUtils.getAppLauncherLocation(targAppLauncherVer) + "\n");
			}
			tmpFW.write("exit\n\n");

			// Write the section: pass
			tmpFW.write("# Define the pass section (clean up for success)\n");
			tmpFW.write("sect,pass\n");
			if (targJre != null)
				tmpFW.write("trash," + PlatformUtils.getJreLocation(currJreVer) + "\n");
			if (targAppLauncher != null)
				tmpFW.write("trash," + PlatformUtils.getAppLauncherLocation(currAppLauncherVer) + "\n");
			tmpFW.write("exit\n\n");

			// Write the section: reboot
			tmpFW.write("# Define the reboot section\n");
			tmpFW.write("sect,reboot\n");
			tmpFW.write("exit\n\n");

			// Write the section: test
			tmpFW.write("# Define the test section\n");
			tmpFW.write("sect,test\n");
			tmpFW.write("exit\n\n");
		}
		catch(IOException aExp)
		{
			aTask.infoAppendln("Failed to generate the delta.cfg file.");
			aTask.infoAppendln(ThreadUtil.getStackTrace(aExp));
			return false;
		}

		// We are done if there was no updated JRE
		if (targJre == null)
			return true;

		// Since an updated JRE was needed...
		// Moved the JRE (unpacked folder) from its drop path to the proper location
		JreVersion targJreVer = targJre.getVersion();
		File installPath = DistUtils.getAppPath();
		File jreDropPath = new File(aDestPath, JreUtils.getExpandJrePath(targJreVer));
		File jreTargPath = new File(installPath.getParentFile(), PlatformUtils.getJreLocation(targJreVer));
		jreTargPath.getParentFile().setWritable(true);
		if (jreDropPath.renameTo(jreTargPath) == false)
		{
			aTask.infoAppendln("Failed to move the updated JRE to its target location!");
			aTask.infoAppendln("\t Current path: " + jreDropPath);
			aTask.infoAppendln("\tOfficial path: " + jreTargPath);
			return false;
		}

		// Backup the application configuration
		File origAppCfgFile = new File(aDestPath, appCfgFile.getName() + ".old");
		try
		{
			Files.copy(appCfgFile.toPath(), origAppCfgFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
		}
		catch(IOException aExp)
		{
			aTask.infoAppendln("Failed to backup application configuration file: " + appCfgFile);
			aTask.infoAppendln(ThreadUtil.getStackTrace(aExp));
			return false;
		}

		// Update the application configuration to reflect the proper JRE
		try
		{
			PlatformUtils.setJreVersion(targJre.getVersion());
			if (targAppLauncher != null)
				PlatformUtils.setAppLauncher(targAppLauncher);
		}
		catch(ErrorDM aExp)
		{
			aTask.infoAppendln("Failed to update the configuration to point to the updated JRE!");
			aTask.infoAppendln("\tCurrent JRE: " + currJreVer.getLabel());
			aTask.infoAppendln("\t Chosen JRE: " + targJre.getVersion().getLabel());
			MiscUtils.printErrorDM(aTask, aExp, 1);

			// Remove the just installed JRE
			IoUtil.deleteDirectory(jreTargPath);

			// Restore the application configuration
			try
			{
				Files.copy(origAppCfgFile.toPath(), appCfgFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
			}
			catch(IOException aExp2)
			{
				throw new ErrorDM(aExp2, "Failed to restore application configuration. Application may be unstable!");
			}

			return false;
		}

		return true;
	}

	/**
	 * Class used to store a complex 'tuple' value.
	 * <P>
	 * This object is used to store the results of a successful JRE update.
	 */
	private class JreUpdateResult
	{
		// Attributes
		public final JreRelease targJre;
		public final AppLauncherRelease targAppLauncher;

		private JreUpdateResult(JreRelease aJreRelease, AppLauncherRelease aAppLauncherRelease)
		{
			targJre = aJreRelease;
			targAppLauncher = aAppLauncherRelease;
		}
	}

	/**
	 * Helper method to download a compatible JreRelease for the AppCatalog to the specified destPath.
	 * <P>
	 * On success the JreVersion that was downloaded is returned.
	 */
	private JreUpdateResult downloadJreUpdate(Task aTask, AppCatalog aUpdateCat, File aDestPath, long releaseSizeFull)
	{
		List<JreRelease> jreList;

		// Ensure our JRE version is compatible for this release
		JreVersion currJreVer = DistUtils.getJreVersion();

		// Let the user know why their version is not compatible
		String updnStr = "downgraded";
		if (aUpdateCat.isJreVersionTooOld(currJreVer) == true)
			updnStr = "upgraded";
		aTask.infoAppendln("Your current JRE is not compatible with this release. It will need to be " + updnStr + "!");
		aTask.infoAppendln("\tCurrent  JRE: " + currJreVer.getLabel());
		aTask.infoAppendln("\tMinimum  JRE: " + aUpdateCat.getMinJreVersion().getLabel());
		JreVersion tmpJreVer = aUpdateCat.getMaxJreVersion();
		if (tmpJreVer != null)
			aTask.infoAppendln("\tMaximum  JRE: " + tmpJreVer.getLabel());
		aTask.infoAppendln("");

		// Bail if we are running a non-bundled JRE
		if (DistUtils.isJreBundled() == false)
		{
			aTask.infoAppend("This is the non bundled JRE version of the application. You are running the system JRE. ");
			aTask.infoAppendln("Please update the JRE (or path) to reflect a compatible JRE version.\n");
			return null;
		}

		// Get list of all available JREs
		jreList = JreUtils.getAvailableJreReleases(aTask, updateSiteUrl, refCredential);
		if (jreList == null)
		{
			aTask.infoAppendln("The update site has not had any JREs deployed.");
			aTask.infoAppendln("Please contact the update site adminstartor.");
			return null;
		}
		if (jreList.size() == 0)
		{
			aTask.infoAppendln("No JRE releases found!");
			aTask.infoAppendln("Please contact the update site adminstartor.");
			return null;
		}

		// Retrieve the latest appropriate JreRelease
		String archStr = PlatformUtils.getArchitecture();
		String platStr = PlatformUtils.getPlatform();
		jreList = JreUtils.getMatchingPlatforms(jreList, archStr, platStr);
		if (jreList.size() == 0)
		{
			aTask.infoAppendln("There are no JRE releases available for platform: (" + archStr + ") " + platStr + "!");
			return null;
		}

		// Retrieve the JRE that is compatible from the list
		JreRelease pickJre = aUpdateCat.getCompatibleJre(jreList);
		if (pickJre == null)
		{
			aTask.infoAppendln("There are no compatible JREs found on the deploy site. Available JREs: " + jreList.size());
			for (JreRelease aJreRelease : jreList)
				aTask.infoAppendln("\t" + aJreRelease.getFileName() + "   --->   (JRE: " + aJreRelease.getVersion().getLabel() + ")");
			aTask.infoAppendln("\nPlease contact the update site adminstartor.");
			return null;
		}
		JreVersion pickJreVer = pickJre.getVersion();

		// Update the AppLauncher if required
		AppLauncherRelease pickAppLauncher = null;
		if (AppLauncherUtils.isAppLauncherUpdateNeeded(aTask, pickJre) == true)
		{
			pickAppLauncher = AppLauncherUtils.updateAppLauncher(aTask, pickJre, aDestPath, updateSiteUrl, refCredential);
			if (pickAppLauncher == null)
				return null;
			aTask.infoAppendln("");
		}

		// Update the number of bytes to be retrieved to take into account the JRE which we will be downloading
		long tmpFileLen = pickJre.getFileLen();
		releaseSizeFull += tmpFileLen;

		// Download the JRE
		Digest targDigest, testDigest;
		targDigest = pickJre.getDigest();
		MessageDigest msgDigest;
		msgDigest = DigestUtils.getDigest(targDigest.getType());
		aTask.infoAppendln("Downloading JRE... Version: " + pickJreVer.getLabel());
		URL srcUrl = IoUtil.createURL(updateSiteUrl.toString() + "/jre/" + pickJreVer.getLabel() + "/" + pickJre.getFileName());
		File dstFile = new File(aDestPath, pickJre.getFileName());
		Task tmpTask = new PartialTask(aTask, aTask.getProgress(), (tmpFileLen * 0.75) / (releaseSizeFull + 0.00));
		if (DistUtils.downloadFile(tmpTask, srcUrl, dstFile, refCredential, tmpFileLen, msgDigest) == false)
			return null;

		// Validate that the JRE was downloaded successfully
		testDigest = new Digest(targDigest.getType(), msgDigest.digest());
		if (targDigest.equals(testDigest) == false)
		{
			aTask.infoAppendln("The download of the JRE appears to be corrupted.");
			aTask.infoAppendln("\tFile: " + dstFile);
			aTask.infoAppendln("\t\tExpected " + targDigest.getDescr());
			aTask.infoAppendln("\t\tReceived " + testDigest.getDescr() + "\n");
			return null;
		}

		// Unpack the JRE at the unpack location
		aTask.infoAppendln("Finshed downloading JRE. Unpacking JRE...");
		File jreRootPath = null;
		File jreTargPath = new File(aDestPath, JreUtils.getExpandJrePath(pickJreVer));
		try
		{
			// Create the working unpack folder where the JRE will be initially unpacked to.
			File unpackPath = new File(aDestPath, "unpack");
			unpackPath.mkdirs();

			// Unpack the JRE to the working unpack folder. Ensure that the unpacked JRE results in 1 top level folder.
			tmpTask = new PartialTask(aTask, aTask.getProgress(), (tmpFileLen * 0.25) / (releaseSizeFull + 0.00));
			MiscUtils.unTar(tmpTask, dstFile, unpackPath);
			File[] fileArr = unpackPath.listFiles();
			if (fileArr.length != 1 && fileArr[0].isDirectory() == false)
				throw new Exception("Expected only one (top level) folder to be unpacked. Items extracted: " + fileArr.length + "   Path: " + unpackPath);
			jreRootPath = fileArr[0];

			// Moved the unpacked JRE to aDestPath/jre/ folder and remove the working unpack folder and the tar.gz file
			jreRootPath.renameTo(jreTargPath);
			unpackPath.delete();
			dstFile.delete();
		}
		catch(Exception aExp)
		{
			aTask.infoAppendln("Failed to properly untar archive. The update has been aborted.");
			aTask.infoAppendln("\tTar File: " + dstFile);
			aTask.infoAppendln("\tDestination: " + jreTargPath);

			String errMsg = ThreadUtil.getStackTrace(aExp);
			aTask.infoAppend("\nStack Trace:\n" + errMsg);
			return null;
		}

		// Return the results
		return new JreUpdateResult(pickJre, pickAppLauncher);
	}

	/**
	 * Helper method that "reverts" an update. After this method is called the DistMaker application's configuration
	 * should be in the same state as before an update was applied.
	 * <P>
	 * It is necessary to do this, since the user may later cancel the update request and it is important to leave the
	 * program and configuration files in a stable state.
	 * <P>
	 * An update will be reverted by doing:
	 * <UL>
	 * <LI>Reverting the configuration to the currently running JRE and AppRelease
	 * <LI>Executing the 'fail' section of the file: delta/delta.cfg
	 * <LI>Removing the delta directory
	 * <LI>Removing the delta.cfg file
	 * </UL>
	 * <P>
	 * There should not be any issues with this roll back process. However if there are, a best effort will be made to
	 * continue rolling back the updates - note that the application might be in an unstable state - and may not be able
	 * to be restarted.
	 */
	private void revertUpdate(Task aTask)
	{
		// Revert our application's configuration (which will be loaded when it is restarted) to reflect the proper JRE
		try
		{
			JreVersion currJreVer = DistUtils.getJreVersion();
			PlatformUtils.setJreVersion(currJreVer);
		}
		catch(ErrorDM aExp)
		{
			aTask.infoAppendln("Failed to revert application's JRE!");
			aTask.infoAppendln("\tApplication may be in an unstable state.");
			MiscUtils.printErrorDM(aTask, aExp, 1);
		}

		// Revert any platform specific config files
		try
		{
			PlatformUtils.updateAppRelease(currRelease);
		}
		catch(ErrorDM aExp)
		{
			aTask.infoAppendln("Failed to revert application configuration!");
			aTask.infoAppendln("\tApplication may be in an unstable state.");
			MiscUtils.printErrorDM(aTask, aExp, 1);
		}

		// Determine the path to the delta (update) folder
		File rootPath = DistUtils.getAppPath().getParentFile();
		File deltaPath = new File(rootPath, "delta");

		// Execute any trash commands from the "fail" section of the delta.cmd file
		File deltaCmdFile = new File(deltaPath, "delta.cmd");
		try (BufferedReader br = MiscUtils.openFileAsBufferedReader(deltaCmdFile))
		{
			String currSect = null;
			while (true)
			{
				String inputStr = br.readLine();

				// Delta command files should always have a proper exit and thus never arrive here
				if (inputStr == null)
					throw new ErrorDM("Command file (" + deltaCmdFile + ") is incomplete.");

				// Ignore empty lines and comments
				if (inputStr.isEmpty() == true || inputStr.startsWith("#") == true)
					continue;

				// Tokenize the input and retrieve the command
				String[] strArr = inputStr.split(",");
				String cmdStr = strArr[0];

				// Skip to next line when we read a new section
				if (strArr.length == 2 && cmdStr.equals("sect") == true)
				{
					currSect = strArr[1];
					continue;
				}

				// Skip to the next line if we are not in the "fail" section
				if (currSect != null && currSect.equals("fail") == false)
					continue;
				else if (currSect == null)
					throw new ErrorDM("Command specified outside of section. Command: " + inputStr);

				// Bail if we reach the exit command
				if (strArr.length == 1 && cmdStr.equals("exit") == true)
					break;

				// Strip off the reboot command and get the actual command. It is safe to
				// execute reboot,* commands now since the actual update is not running yet.
				if (inputStr.startsWith("reboot,") == true)
				{
					strArr = inputStr.substring(7).split(",");
					cmdStr = strArr[0];
				}

				// Handle the trash command
				if (cmdStr.equals("trash") == true && strArr.length == 2)
				{
					String delTargStr = strArr[1];

					// Ensure we are not looking at a "hollow" trash command
					if (delTargStr.isEmpty() == true)
						throw new ErrorDM("File (" + deltaCmdFile + ") has invalid input: " + inputStr);

					// Resolve the argument to the corresponding file and ensure it is relative to our rootPath
					File trashFile = new File(rootPath, delTargStr).getCanonicalFile();
					if (MiscUtils.getRelativePath(rootPath, trashFile) == null)
						throw new ErrorDM("File (" + trashFile + ") is not relative to folder: " + rootPath);

					if (trashFile.isFile() == true)
					{
						if (trashFile.delete() == false)
							throw new ErrorDM("Failed to delete file: " + trashFile);
					}
					else if (trashFile.isDirectory() == true)
					{
						if (IoUtil.deleteDirectory(trashFile) == false)
							throw new ErrorDM("Failed to delete folder: " + trashFile);
					}
					else
					{
						throw new ErrorDM("File type is not recognized: " + trashFile);
					}
				}

				// Handle the copy command
				else if (cmdStr.equals("copy") == true && strArr.length == 3)
				{
					// Resolve the arguments to the corresponding files
					File srcFile = new File(rootPath, strArr[1]).getCanonicalFile();
					File dstFile = new File(rootPath, strArr[2]).getCanonicalFile();

					// Ensure we will only change files relative to our rootPath
					if (MiscUtils.getRelativePath(rootPath, srcFile) == null)
						throw new ErrorDM("Source file (" + srcFile + ") is not relative to folder: " + rootPath);
					if (MiscUtils.getRelativePath(rootPath, dstFile) == null)
						throw new ErrorDM("Destination file (" + srcFile + ") is not relative to folder: " + rootPath);

					// Copy the srcFile to the (non existing) dstFile
					if (srcFile.exists() == false)
						throw new ErrorDM("Source file does not exist: " + srcFile);
					if (dstFile.exists() == true && dstFile.delete() == false)
						throw new ErrorDM("Failed to remove prexisting file at destination: " + dstFile);
					if (srcFile.renameTo(dstFile) == false)
						throw new ErrorDM("Failed to copy source (" + srcFile + ") to destination: " + dstFile);
				}

				// Handle the move command
				else if (cmdStr.equals("move") == true && strArr.length == 3)
				{
					// Resolve the arguments to the corresponding files
					File srcFile = new File(rootPath, strArr[1]).getCanonicalFile();
					File dstFile = new File(rootPath, strArr[2]).getCanonicalFile();

					// Ensure we will only change files relative to our rootPath
					if (MiscUtils.getRelativePath(rootPath, srcFile) == null)
						throw new ErrorDM("Source (" + srcFile + ") is not relative to folder: " + rootPath);
					if (MiscUtils.getRelativePath(rootPath, dstFile) == null)
						throw new ErrorDM("Destination (" + srcFile + ") is not relative to folder: " + rootPath);

					// Move the srcFile to the (folder) dstFile
					if (srcFile.exists() == false)
						throw new ErrorDM("Source file does not exist: " + srcFile);
					if (dstFile.isDirectory() == true)
						dstFile = new File(dstFile, srcFile.getName());
					if (srcFile.renameTo(dstFile) == false)
						throw new ErrorDM("Failed to move source (" + srcFile + ") to destination: " + dstFile);
				}

				// Unrecognized command
				else
				{
					throw new ErrorDM("Input string is not recognized: " + inputStr);
				}
			}
		}
		catch(IOException aExp)
		{
			aTask.infoAppendln("Failed to revert application configuration!");
			aTask.infoAppendln("\tApplication may be in an unstable state.");
			aTask.infoAppendln(ThreadUtil.getStackTrace(aExp));
		}

		// Remove the entire delta folder
		if (IoUtil.deleteDirectory(deltaPath) == false)
			throw new ErrorDM("Failed to delete folder: " + deltaPath);
	}

	/**
	 * Helper method that prompts the user for forms of input depending on the state of the App
	 * <P>
	 * This method will be called via reflection.
	 */
	private void queryUserForInput(Task aTask, File aDeltaPath, List<AppRelease> aFullList)
	{
		AppRelease chosenItem;

		// Query the user, if the wish to destroy the old update
		if (aDeltaPath.isDirectory() == true)
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

			// Revert the update
			revertUpdate(aTask);
		}

		// Query the user of the version to update to
		pickVersionPanel.setConfiguration(aFullList);
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
	 * Notification that the corresponding application has been fully initialized. This helper method will notify the
	 * user on the status of any update.
	 */
	private void showUpdateResults()
	{
		String appName, infoMsg;
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
			infoMsg = "The application, " + currRelease.getName() + ", has been updated to ";
			infoMsg += "version: " + currRelease.getVersion();
		}
		// Update failed
		else
		{
			infoMsg = "There was an issue while updating the " + appName + " application.\n";
			infoMsg += "The application, " + appName + ", is currently at version: " + currRelease.getVersion() + "\n\n";
			infoMsg += DistUtils.getUpdateMsg();
		}

		// Setup the runnable that will clean up our delta folder
		Runnable cleanDeltaRunnable = new Runnable()
		{
			@Override
			public void run()
			{
				// Remove the delta folder (if it exists)
				File deltaPath = new File(DistUtils.getAppPath().getParentFile(), "delta");
				if (deltaPath.isDirectory() == false)
					return;

				if (IoUtil.deleteDirectory(deltaPath) == false)
					System.err.println("Failed to remove delta path. Cleanup after update was not fully completed.");
			}
		};

		// Show the message panel and execute cleanDeltaRunnable
		displayNoticeAndExecute(infoMsg, cleanDeltaRunnable, true);
	}

}
