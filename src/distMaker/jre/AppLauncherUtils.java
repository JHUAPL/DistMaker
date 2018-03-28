package distMaker.jre;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import distMaker.DistUtils;
import distMaker.digest.Digest;
import distMaker.digest.DigestType;
import distMaker.digest.DigestUtils;
import distMaker.platform.PlatformUtils;
import distMaker.utils.ParseUtils;
import distMaker.utils.PlainVersion;
import distMaker.utils.Version;
import distMaker.utils.VersionUtils;
import glum.gui.GuiUtil;
import glum.io.IoUtil;
import glum.net.Credential;
import glum.net.NetUtil;
import glum.task.PartialTask;
import glum.task.Task;
import glum.util.ThreadUtil;

public class AppLauncherUtils
{
	/**
	 * Returns a list of all the available AppLauncher releases specified at: <BR>
	 * {@literal <aUpdateSiteUrl>/launcher/appCatalog.txt}
	 */
	public static List<AppLauncherRelease> getAvailableAppLauncherReleases(Task aTask, URL aUpdateSiteUrl, Credential aCredential)
	{
		List<AppLauncherRelease> retList;
		URL catUrl;
		URLConnection connection;
		InputStream inStream;
		BufferedReader bufReader;
		DigestType digestType;
		String errMsg, strLine;

		errMsg = null;
		retList = new ArrayList<>();
		catUrl = IoUtil.createURL(aUpdateSiteUrl.toString() + "/launcher/appCatalog.txt");

		// Default to DigestType of MD5
		digestType = DigestType.MD5;

		inStream = null;
		bufReader = null;
		try
		{
			// Read the contents of the file
			connection = catUrl.openConnection();
			inStream = NetUtil.getInputStream(connection, aCredential);
			bufReader = new BufferedReader(new InputStreamReader(new BufferedInputStream(inStream)));

			// Read the lines
			while (true)
			{
				strLine = bufReader.readLine();

				// Bail once we are done
				if (strLine == null)
					break;

				// Ignore comments and empty lines
				if (strLine.isEmpty() == true || strLine.startsWith("#") == true)
					continue;

				String[] tokens;
				tokens = strLine.split(",", 5);
				if (tokens.length == 2 && tokens[0].equals("name") == true && tokens[1].equals("AppLauncher") == true)
					; // Nothing to do - we just entered the "AppLauncher" section
				// Logic to handle the 'exit' command
				else if (tokens.length >= 1 && tokens[0].equals("exit") == true)
				{
					// We support exit commands with 3 tokens. All others
					// we will just exit.
					if (tokens.length != 3)
						break;

					String targName = tokens[1];
					String needVer = tokens[2];
					if (ParseUtils.shouldExitLogic(targName, needVer) == true)
						break;
				}
				// Logic to handle the 'digest' command
				else if (tokens.length == 2 && tokens[0].equals("digest") == true)
				{
					DigestType tmpDigestType;

					tmpDigestType = DigestType.parse(tokens[1]);
					if (tmpDigestType == null)
						aTask.infoAppendln("Failed to locate DigestType for: " + tokens[1]);
					else
						digestType = tmpDigestType;
				}
				// Logic to handle the 'F' command: AppLauncher File
				else if (tokens.length == 5 && tokens[0].equals("F") == true)
				{
					String filename, digestStr, version;
					long fileLen;

					// Form the JreRelease
					digestStr = tokens[1];
					fileLen = GuiUtil.readLong(tokens[2], -1);
					filename = tokens[3];
					version = tokens[4];

					Digest tmpDigest = new Digest(digestType, digestStr);
					retList.add(new AppLauncherRelease(version, filename, tmpDigest, fileLen));
				}
				else
				{
					aTask.infoAppendln("Unreconized line: " + strLine);
				}
			}
		}
		catch(FileNotFoundException aExp)
		{
			errMsg = "Failed to locate resource: " + catUrl;
		}
		catch(IOException aExp)
		{
			errMsg = ThreadUtil.getStackTrace(aExp);
		}
		finally
		{
			IoUtil.forceClose(inStream);
			IoUtil.forceClose(bufReader);
		}

		// See if we are in a valid state
		if (errMsg != null)
			; // Nothing to do, as an earlier error has occurred
		else if (retList.size() == 0)
			errMsg = "The catalog appears to be invalid.";

		// Bail if there were issues
		if (errMsg != null)
		{
			aTask.infoAppendln(errMsg);
			return null;
		}

		return retList;
	}

	/**
	 * Utility method that checks to see if the AppLauncher will need be updated in order to support the specified JRE.
	 * <P>
	 * Returns false if the AppLauncher does NOT need be updated. Otherwise true will be returned and the version info
	 * will be logged to the specified aTask.
	 */
	public static boolean isAppLauncherUpdateNeeded(Task aTask, JreRelease aJreRelease)
	{
		Version currVer, nMinVer, nMaxVer;

		// Bail if the installed version of AppLauncher is equal to or later than the required version
		currVer = DistUtils.getAppLauncherVersion();
		nMinVer = aJreRelease.getAppLauncherMinVersion();
		nMaxVer = aJreRelease.getAppLauncherMaxVersion();
		if (VersionUtils.isInRange(currVer, nMinVer, nMaxVer) == true)
			return false;

		// Log the fact that we need to update our AppLauncher
		aTask.infoAppendln("Your current AppLauncher is not compatible with this release. It will need to be upgraded!");
		aTask.infoAppendln("\tCurrent ver: " + currVer);
		if (nMinVer != PlainVersion.AbsMin)
			aTask.infoAppendln("\tMinimum ver: " + nMinVer);
		if (nMaxVer != PlainVersion.AbsMax)
			aTask.infoAppendln("\tMaximum ver: " + nMaxVer);
		aTask.infoAppendln("");

		return true;
	}

	/**
	 * Utility method that will return the AppLauncherRelease that satisfies the requirements as specified by the
	 * JreRelease.
	 * <P>
	 * Returns null if no AppLauncherRelease is located that can satisfy the specified JreRelease.
	 * <P>
	 * Any issues that cropped up while searching for a valid AppLauncherRelease will be logged to the specified task,
	 * aTask.
	 * <P>
	 * TODO: Remove the comments below
	 * <P>
	 * corresponding to the AppLauncher that we to the specified version required by this JRE.
	 * <P>
	 * Returns false on any failure.
	 */
	public static AppLauncherRelease updateAppLauncher(Task aTask, JreRelease aJreRelease, File aDestPath, URL updateSiteUrl, Credential aCredential)
	{
		// Locate the list of available AppLaunchers
		List<AppLauncherRelease> availList;
		availList = AppLauncherUtils.getAvailableAppLauncherReleases(aTask, updateSiteUrl, aCredential);
		if (availList == null)
		{
			aTask.infoAppendln("The update site does not have any deployed AppLaunchers.");
			aTask.infoAppendln("Please contact the update site adminstartor.");
			return null;
		}

		if (availList.size() == 0)
		{
			aTask.infoAppendln("No AppLauncher releases found!");
			aTask.infoAppendln("Please contact the update site adminstartor.");
			return null;
		}

		// Retrieve the AppLauncher that is compatible from the list
		Version nMinVer = aJreRelease.getAppLauncherMinVersion();
		Version nMaxVer = aJreRelease.getAppLauncherMaxVersion();

		AppLauncherRelease pickRelease = null;
		for (AppLauncherRelease aRelease : availList)
		{
			Version evalVer = aRelease.getVersion();
			if (VersionUtils.isInRange(evalVer, nMinVer, nMaxVer) == false)
				continue;

			pickRelease = aRelease;
			break;
		}

		// Bail if no compatible release could be found
		if (pickRelease == null)
		{
			aTask.infoAppendln("No compatible AppLauncher releases have been deployed!");
			aTask.infoAppendln("Please contact the update site adminstartor.");
			return null;
		}

		// Get stats on the release
		Version pickVer = pickRelease.getVersion();
		long fileLen = pickRelease.getFileLen();

		// Retrieve the relative path to the launcher
		String relLauncherPath;
		relLauncherPath = PlatformUtils.getAppLauncherLocation(pickVer);

		// Download the AppLauncher
		Digest targDigest, testDigest;
		MessageDigest msgDigest;
		targDigest = pickRelease.getDigest();
		msgDigest = DigestUtils.getDigest(targDigest.getType());
		aTask.infoAppendln("Downloading AppLauncher... Version: " + pickVer);
		URL srcUrl = IoUtil.createURL(updateSiteUrl.toString() + "/launcher/" + pickRelease.getFileName());
		File dstFile = new File(aDestPath.getParentFile(), relLauncherPath);
		Task tmpTask = new PartialTask(aTask, aTask.getProgress(), 0.01);
//		Task tmpTask = new PartialTask(aTask, aTask.getProgress(), (tmpFileLen * 0.75) / (releaseSizeFull + 0.00));
//		Task tmpTask = new SilentTask();
		if (DistUtils.downloadFile(tmpTask, srcUrl, dstFile, aCredential, fileLen, msgDigest) == false)
		{
			aTask.infoAppendln("Failed to download updated AppLauncher.");
			aTask.infoAppendln("\tSource: " + srcUrl);
			aTask.infoAppendln("\tFile: " + dstFile);
			return null;
		}

		// Validate that the AppLauncher was downloaded successfully
		testDigest = new Digest(targDigest.getType(), msgDigest.digest());
		if (targDigest.equals(testDigest) == false)
		{
			aTask.infoAppendln("The download of the AppLauncher appears to be corrupted.");
			aTask.infoAppendln("\tFile: " + dstFile);
			aTask.infoAppendln("\t\tExpected " + targDigest.getDescr());
			aTask.infoAppendln("\t\tReceived " + testDigest.getDescr());
			return null;
		}

		// Log the success
		aTask.infoAppendln("Success updating AppLauncher...");

		return pickRelease;
	}

}
