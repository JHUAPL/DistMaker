package distMaker.jre;

import glum.gui.GuiUtil;
import glum.io.IoUtil;
import glum.net.Credential;
import glum.net.NetUtil;
import glum.task.Task;
import glum.util.ThreadUtil;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

import distMaker.digest.Digest;
import distMaker.digest.DigestType;
import distMaker.platform.PlatformUtils;

public class JreUtils
{
	/**
	 * Returns a list of all the available JRE releases specified in &lt;aUpdateSiteUrl&gt;/jre/jreCatalog.txt
	 */
	public static List<JreRelease> getAvailableJreReleases(Task aTask, URL aUpdateSiteUrl, Credential aCredential)
	{
		List<JreRelease> retList;
		URL catUrl;
		URLConnection connection;
		InputStream inStream;
		BufferedReader bufReader;
		DigestType digestType;
		String errMsg, strLine;
		String version;

		errMsg = null;
		retList = new ArrayList<>();
		catUrl = IoUtil.createURL(aUpdateSiteUrl.toString() + "/jre/jreCatalog.txt");

		// Default to DigestType of MD5
		digestType = DigestType.MD5;
		version = null;

		inStream = null;
		bufReader = null;
		try
		{
			String[] tokens;

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

				tokens = strLine.split(",", 4);
				if (strLine.isEmpty() == true || strLine.startsWith("#") == true)
					; // Nothing to do
				else if (tokens.length >= 1 && tokens[0].equals("exit") == true)
					break; // Bail once we get the 'exit' command
				else if (tokens.length == 2 && tokens[0].equals("name") == true && tokens[1].equals("JRE") == true)
					; // Nothing to do - we just entered the "JRE" section
				else if (tokens.length == 2 && tokens[0].equals("digest") == true)
				{
					DigestType tmpDigestType;

					tmpDigestType = DigestType.parse(tokens[1]);
					if (tmpDigestType == null)
						aTask.infoAppendln("Failed to locate DigestType for: " + tokens[1]);
					else
						digestType = tmpDigestType;
				}
				else if (tokens.length == 2 && tokens[0].equals("jre") == true)
				{
					version = tokens[1];
				}
				else if (tokens.length == 4 && tokens[0].equals("F") == true)
				{
					String platform, filename, digestStr;
					long fileLen;

					if (version == null)
					{
						aTask.infoAppendln("Skipping input: " + strLine);
						aTask.infoAppendln("\tJRE version has not been specifed. Missing input line: jre,<jreVersion>");
						continue;
					}

					// Form the JreRelease
					digestStr = tokens[1];
					fileLen = GuiUtil.readLong(tokens[2], -1);
					filename = tokens[3];

					platform = PlatformUtils.getPlatformOfJreTarGz(filename);
					if (platform == null)
					{
						aTask.infoAppendln("Skipping input: " + strLine);
						aTask.infoAppendln("\tFailed to determine the target platform of the JRE.");
						continue;
					}

					retList.add(new JreRelease(platform, version, filename, new Digest(digestType, digestStr), fileLen));
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
	 * Utility method that returns a list of matching JREs. The list will be sorted in order from newest to oldest. All returned JREs will have a platform that
	 * matches aPlatform.
	 */
	public static List<JreRelease> getMatchingPlatforms(List<JreRelease> aJreList, String aPlatform)
	{
		List<JreRelease> retList;

		// Grab all JREs with a matching platforms
		retList = new ArrayList<>();
		for (JreRelease aRelease : aJreList)
		{
			if (aRelease.isPlatformMatch(aPlatform) == false)
				continue;

			retList.add(aRelease);
		}

		// Sort the platforms, but reverse the order so that the newest version is first
		Collections.sort(retList);
		Collections.reverse(retList);

		return retList;
	}

}
