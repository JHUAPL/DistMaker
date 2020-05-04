package distMaker.jre;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

import distMaker.platform.*;
import distMaker.utils.ParseUtils;
import distMaker.utils.PlainVersion;
import glum.digest.Digest;
import glum.digest.DigestType;
import glum.io.IoUtil;
import glum.io.ParseUtil;
import glum.net.Credential;
import glum.net.NetUtil;
import glum.task.Task;
import glum.util.ThreadUtil;

/**
 * Collection of utility methods that provide JRE related functionality.
 *
 * @author lopeznr1
 */
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
		List<JreRelease> retL;
		URL catUrl;
		URLConnection connection;
		InputStream inStream;
		BufferedReader bufReader;
		String errMsg, strLine;

		errMsg = null;
		retL = new ArrayList<>();
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
						aTask.logRegln("Failed to locate DigestType for: " + tokens[1]);
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

					aTask.logRegln("Unreconized line: " + strLine);
				}
				// Logic to handle the 'F' command: JRE File
				else if (tokens[0].equals("F") == true && tokens.length >= 4 && tokens.length <= 6)
				{
					if (version == null)
					{
						aTask.logRegln("Skipping input: " + strLine);
						aTask.logRegln("\tJRE version has not been specifed. Missing input line: jre,<jreVersion>");
						continue;
					}

					// Parse the JRE release
					Architecture architecture;
					Platform platform;
					String filename, digestStr;
					long fileLen;

					if (tokens.length == 6)
					{
						architecture = ArchitectureUtils.transformToArchitecture(tokens[1]);
						if (architecture == null)
						{
							aTask.logRegln("Skipping input: " + strLine);
							aTask.logRegln("\tFailed to determine the target architecture of the JRE.");
							continue;
						}

						platform = PlatformUtils.transformToPlatform(tokens[2]);
						if (platform == null)
						{
							aTask.logRegln("Skipping input: " + strLine);
							aTask.logRegln("\tFailed to determine the target platform of the JRE.");
							continue;
						}

						filename = tokens[3];
						digestStr = tokens[4];
						fileLen = ParseUtil.readLong(tokens[5], -1);
					}
					else if (tokens.length == 5)
					{
						architecture = Architecture.x64;
						digestStr = tokens[1];
						fileLen = ParseUtil.readLong(tokens[2], -1);

						platform = PlatformUtils.transformToPlatform(tokens[3]);
						if (platform == null)
						{
							aTask.logRegln("Skipping input: " + strLine);
							aTask.logRegln("\tFailed to determine the target platform of the JRE.");
							continue;
						}

						filename = tokens[4];
					}
					else // tokens.length == 4
					{
						architecture = Architecture.x64;
						digestStr = tokens[1];
						fileLen = ParseUtil.readLong(tokens[2], -1);
						filename = tokens[3];

						platform = JreUtils.getPlatformOfJreTarGz(filename);
						if (platform == null)
						{
							aTask.logRegln("Skipping input: " + strLine);
							aTask.logRegln("\tFailed to determine the target platform of the JRE.");
							continue;
						}
					}

					// Form the JreRelease
					Digest tmpDigest = new Digest(digestType, digestStr);
					retL.add(new JreRelease(architecture, platform, version, filename, tmpDigest, fileLen, alMinVer, alMaxVer));
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
	 * Utility method that returns a list of matching JREs. The list will be sorted in order from newest to oldest. All
	 * returned JREs will have a platform that matches aPlatform.
	 */
	public static List<JreRelease> getMatchingPlatforms(List<JreRelease> aJreList, Architecture aArch, Platform aPlat)
	{
		List<JreRelease> retL;

		// Grab all JREs with a matching platforms
		retL = new ArrayList<>();
		for (JreRelease aRelease : aJreList)
		{
			if (aRelease.isSystemMatch(aArch, aPlat) == false)
				continue;

			retL.add(aRelease);
		}

		// Sort the platforms, but reverse the order so that the newest version is first
		Collections.sort(retL);
		Collections.reverse(retL);

		return retL;
	}

	/**
	 * Utility method that returns the platform of the JRE file.
	 * <P>
	 * This only examines the filename to determine the platform.
	 * <P>
	 * This method should be considered deprecated as of DistMaker 0.48
	 */
	@Deprecated
	private static Platform getPlatformOfJreTarGz(String aFileName)
	{
		aFileName = aFileName.toUpperCase();
		if (aFileName.contains("LINUX") == true)
			return Platform.Linux;
		if (aFileName.contains("MACOSX") == true)
			return Platform.Macosx;
		if (aFileName.contains("WINDOWS") == true)
			return Platform.Windows;

		return null;
	}

}
