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
package distMaker.jre;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import distMaker.*;
import distMaker.platform.PlatformUtils;
import distMaker.utils.*;
import glum.digest.Digest;
import glum.digest.DigestType;
import glum.io.IoUtil;
import glum.io.ParseUtil;
import glum.net.Credential;
import glum.net.NetUtil;
import glum.task.PartialTask;
import glum.task.Task;
import glum.util.ThreadUtil;
import glum.version.*;

/**
 * Collection of utility methods associated with the DistMaker's AppLauncher.
 * 
 * @author lopeznr1
 */
public class AppLauncherUtils
{
	/**
	 * Returns a list of all the available AppLauncher releases specified at: <br>
	 * {@literal <aUpdateSiteUrl>/launcher/appCatalog.txt}
	 */
	public static List<AppLauncherRelease> getAvailableAppLauncherReleases(Task aTask, URL aUpdateSiteUrl,
			Credential aCredential)
	{
		List<AppLauncherRelease> retL;
		URL catUrl;
		URLConnection connection;
		InputStream inStream;
		BufferedReader bufReader;
		DigestType digestType;
		String errMsg, strLine;

		errMsg = null;
		retL = new ArrayList<>();
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
						aTask.logRegln("Failed to locate DigestType for: " + tokens[1]);
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
					fileLen = ParseUtil.readLong(tokens[2], -1);
					filename = tokens[3];
					version = tokens[4];

					Digest tmpDigest = new Digest(digestType, digestStr);
					retL.add(new AppLauncherRelease(version, filename, tmpDigest, fileLen));
				}
				else
				{
					aTask.logRegln("Unreconized line: " + strLine);
				}
			}
		}
		catch (FileNotFoundException aExp)
		{
			errMsg = "Failed to locate resource: " + catUrl;
		}
		catch (IOException aExp)
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
		else if (retL.size() == 0)
			errMsg = "The catalog appears to be invalid.";

		// Bail if there were issues
		if (errMsg != null)
		{
			aTask.logRegln(errMsg);
			return null;
		}

		return retL;
	}

	/**
	 * Utility method that checks to see if the AppLauncher will need be updated in order to support the specified JRE.
	 * <p>
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
		aTask.logRegln("Your current AppLauncher is not compatible with this release. It will need to be upgraded!");
		aTask.logRegln("\tCurrent ver: " + currVer);
		if (nMinVer != PlainVersion.AbsMin)
			aTask.logRegln("\tMinimum ver: " + nMinVer);
		if (nMaxVer != PlainVersion.AbsMax)
			aTask.logRegln("\tMaximum ver: " + nMaxVer);
		aTask.logRegln("");

		return true;
	}

	/**
	 * Utility method that will return the AppLauncherRelease that satisfies the requirements as specified by the
	 * JreRelease.
	 * <p>
	 * Returns null if no AppLauncherRelease is located that can satisfy the specified JreRelease.
	 * <p>
	 * Any issues that cropped up while searching for a valid AppLauncherRelease will be logged to the specified task,
	 * aTask.
	 * <p>
	 * TODO: Remove the comments below
	 * <p>
	 * corresponding to the AppLauncher that we to the specified version required by this JRE.
	 * <p>
	 * Returns false on any failure.
	 */
	public static AppLauncherRelease updateAppLauncher(Task aTask, JreRelease aJreRelease, File aDestPath,
			URL updateSiteUrl, Credential aCredential)
	{
		// Locate the list of available AppLaunchers
		List<AppLauncherRelease> availL;
		availL = AppLauncherUtils.getAvailableAppLauncherReleases(aTask, updateSiteUrl, aCredential);
		if (availL == null)
		{
			aTask.logRegln("The update site does not have any deployed AppLaunchers.");
			aTask.logRegln(ErrorMsg.ContactSiteAdmin);
			return null;
		}

		if (availL.size() == 0)
		{
			aTask.logRegln("No AppLauncher releases found!");
			aTask.logRegln(ErrorMsg.ContactSiteAdmin);
			return null;
		}

		// Retrieve the AppLauncher that is compatible from the list
		Version nMinVer = aJreRelease.getAppLauncherMinVersion();
		Version nMaxVer = aJreRelease.getAppLauncherMaxVersion();

		AppLauncherRelease pickRelease = null;
		for (AppLauncherRelease aRelease : availL)
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
			aTask.logRegln("No compatible AppLauncher releases have been deployed!");
			aTask.logRegln(ErrorMsg.ContactSiteAdmin);
			return null;
		}

		// Get stats on the release
		Version pickVer = pickRelease.getVersion();
		long fileLen = pickRelease.getFileLen();

		// Retrieve the relative path to the launcher
		String relLauncherPath;
		relLauncherPath = PlatformUtils.getAppLauncherLocation(pickVer);

		// Download the AppLauncher
		Digest targDigest = pickRelease.getDigest();
		aTask.logRegln("Downloading AppLauncher... Version: " + pickVer);
		URL srcUrl = IoUtil.createURL(updateSiteUrl.toString() + "/launcher/" + pickRelease.getFileName());
		File dstFile = new File(aDestPath.getParentFile(), relLauncherPath);
		Task tmpTask = new PartialTask(aTask, aTask.getProgress(), 0.01);
//		Task tmpTask = new PartialTask(aTask, aTask.getProgress(), (tmpFileLen * 0.75) / (releaseSizeFull + 0.00));
//		Task tmpTask = new SilentTask();
		if (MiscUtils.download(tmpTask, srcUrl, dstFile, aCredential, fileLen, targDigest) == false)
			return null;

		// Log the success
		aTask.logRegln("Success updating AppLauncher...");

		return pickRelease;
	}

}
