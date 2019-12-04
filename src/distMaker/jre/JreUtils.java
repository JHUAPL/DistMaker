package distMaker.jre;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import distMaker.digest.Digest;
import distMaker.digest.DigestType;
import distMaker.utils.ParseUtils;
import distMaker.utils.PlainVersion;
import glum.gui.GuiUtil;
import glum.io.IoUtil;
import glum.net.Credential;
import glum.net.NetUtil;
import glum.task.Task;
import glum.util.ThreadUtil;

public class JreUtils
{
	/**
	 * Returns the relative path a JRE should be expanded to.
	 * <P>
	 * Namely legacy JRE versions (versions prior to Java 9) will be expanded to:<BR>
	 * <B>{@code <jre><version>}</B><BR>
	 * while non legacy versions will be expanded to something like:<BR>
	 * <B>{@code <jre>-<version>}</B>
	 */
	public static String getExpandJrePath(JreVersion aJreVersion)
	{
		String version;

		version = aJreVersion.getLabel();
		if (version.startsWith("1.") == true)
			return "jre" + version;
		else
			return "jre-" + version;
	}

	/**
	 * Returns a list of all the available JRE releases specified at: <BR>
	 * {@literal <aUpdateSiteUrl>/jre/jreCatalog.txt}
	 */
	public static List<JreRelease> getAvailableJreReleases(Task aTask, URL aUpdateSiteUrl, Credential aCredential)
	{
		List<JreRelease> retList;
		URL catUrl;
		URLConnection connection;
		InputStream inStream;
		BufferedReader bufReader;
		String errMsg, strLine;

		errMsg = null;
		retList = new ArrayList<>();
		catUrl = IoUtil.createURL(aUpdateSiteUrl.toString() + "/jre/jreCatalog.txt");

		// Default to DigestType of MD5
		PlainVersion alMinVer = PlainVersion.Zero;
		PlainVersion alMaxVer = PlainVersion.AbsMax;
		DigestType digestType = DigestType.MD5;
		JreVersion version = null;

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

				String[] tokens = strLine.split(",", 5);
				if (tokens[0].equals("name") == true && tokens.length == 2 && tokens[1].equals("JRE") == true)
					; // Nothing to do - we just entered the "JRE" section
				// Logic to handle the 'exit' command
				else if (tokens[0].equals("exit") == true && tokens.length >= 1)
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
				else if (tokens[0].equals("digest") == true && tokens.length == 2)
				{
					DigestType tmpDigestType = DigestType.parse(tokens[1]);
					if (tmpDigestType == null)
						aTask.infoAppendln("Failed to locate DigestType for: " + tokens[1]);
					else
						digestType = tmpDigestType;
				}
				// Logic to handle the 'jre' command
				else if (tokens[0].equals("jre") == true && tokens.length == 2)
				{
					version = new JreVersion(tokens[1]);

					// On any new JRE version reset the default required AppLauncher versions
					alMinVer = PlainVersion.Zero;
					alMaxVer = new PlainVersion(0, 99, 0);
				}
				// Logic to handle the 'require' command: JRE File
				else if (tokens[0].equals("require") == true && tokens.length >= 3)
				{
					String target;

					// Process the require,AppLauncher instruction
					target = tokens[1];
					if (target.equals("AppLauncher") == true && (tokens.length == 3 || tokens.length == 4))
					{
						alMinVer = PlainVersion.parse(tokens[2]);
						alMaxVer = new PlainVersion(0, 99, 0);
						if (tokens.length == 4)
							alMaxVer = PlainVersion.parse(tokens[3]);

						continue;
					}

					aTask.infoAppendln("Unreconized line: " + strLine);
				}
				// Logic to handle the 'F' command: JRE File
				else if (tokens[0].equals("F") == true && tokens.length >= 4 && tokens.length <= 6)
				{
					if (version == null)
					{
						aTask.infoAppendln("Skipping input: " + strLine);
						aTask.infoAppendln("\tJRE version has not been specifed. Missing input line: jre,<jreVersion>");
						continue;
					}

					// Parse the JRE release
					String archStr, platStr, filename, digestStr;
					long fileLen;

					if (tokens.length == 6)
					{
						archStr = tokens[1];
						platStr = tokens[2];
						filename = tokens[3];
						digestStr = tokens[4];
						fileLen = GuiUtil.readLong(tokens[5], -1);
					}
					else if (tokens.length == 5)
					{
						archStr = "x64";
						digestStr = tokens[1];
						fileLen = GuiUtil.readLong(tokens[2], -1);
						platStr = tokens[3];
						if (platStr.equalsIgnoreCase("apple") == true)
							platStr = "macosx";
						filename = tokens[4];
					}
					else // tokens.length == 4
					{
						archStr = "x64";
						digestStr = tokens[1];
						fileLen = GuiUtil.readLong(tokens[2], -1);
						filename = tokens[3];

						platStr = JreUtils.getPlatformOfJreTarGz(filename);
						if (platStr == null)
						{
							aTask.infoAppendln("Skipping input: " + strLine);
							aTask.infoAppendln("\tFailed to determine the target platform of the JRE.");
							continue;
						}
					}

					// Form the JreRelease
					Digest tmpDigest = new Digest(digestType, digestStr);
					retList.add(new JreRelease(archStr, platStr, version, filename, tmpDigest, fileLen, alMinVer, alMaxVer));
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
	 * Utility method that returns a list of matching JREs. The list will be sorted in order from newest to oldest. All
	 * returned JREs will have a platform that matches aPlatform.
	 */
	public static List<JreRelease> getMatchingPlatforms(List<JreRelease> aJreList, String aArchStr, String aPlatStr)
	{
		List<JreRelease> retList;

		// Grab all JREs with a matching platforms
		retList = new ArrayList<>();
		for (JreRelease aRelease : aJreList)
		{
			if (aRelease.isSystemMatch(aArchStr, aPlatStr) == false)
				continue;

			retList.add(aRelease);
		}

		// Sort the platforms, but reverse the order so that the newest version is first
		Collections.sort(retList);
		Collections.reverse(retList);

		return retList;
	}

	/**
	 * Utility method that returns the platform of the JRE file.
	 * <P>
	 * This only examines the filename to determine the platform.
	 * <P>
	 * This method should be considered deprecated as of DistMaker 0.48
	 */
	@Deprecated
	private static String getPlatformOfJreTarGz(String aFileName)
	{
		aFileName = aFileName.toUpperCase();
		if (aFileName.contains("LINUX") == true)
			return "Linux";
		if (aFileName.contains("MACOSX") == true)
			return "Macosx";
		if (aFileName.contains("WINDOWS") == true)
			return "Windows";

		return null;
	}

}
