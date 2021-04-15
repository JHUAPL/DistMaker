package distMaker;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import distMaker.jre.JreVersion;
import distMaker.node.*;
import distMaker.utils.*;
import glum.digest.Digest;
import glum.digest.DigestType;
import glum.io.IoUtil;
import glum.io.ParseUtil;
import glum.net.Credential;
import glum.net.NetUtil;
import glum.reflect.ReflectUtil;
import glum.task.Task;
import glum.unit.DateUnit;
import glum.util.ThreadUtil;
import glum.version.PlainVersion;
import glum.version.Version;

/**
 * Collection of utility methods used to access the state of a DistMaker enabled application.
 *
 * @author lopeznr1
 */
public class DistUtils
{
	// -----------------------------------------------------------------------------------------------------------------
	// Start of AppLauncher class loader related vars.
	// Static members that will be automatically configured by the AppLauncher class loader.
	// Do not rename or change the variables below, without changing the AppLauncher class loader.
	// -----------------------------------------------------------------------------------------------------------------
	private static String appLauncherVersion = null;
	private static boolean isDevelopersEnvironment = true;
	private static int updateCode = 0; // Static member to declare the update status, 0: None, 1: Pass, 2: Fail
	private static String updateMsg = null;
	// -----------------------------------------------------------------------------------------------------------------
	// Do not rename or change the variables above, without changing the AppLauncher class loader.
	// End of AppLauncher class loader related vars.
	// -----------------------------------------------------------------------------------------------------------------

	/** Cached value of the AppLauncherVersion. Note the cached value is of type Version rather than String */
	private static Version cAppLauncherVersion;

	/* Static field used only when developing DistMaker. Otherwise this field should always be null. */
	private static File developAppPath = null;

	/**
	 * Utility method to return the version of the AppLauncher that started this process.
	 * <p>
	 * If we are running in a developers environment then this value will be null.
	 */
	public static Version getAppLauncherVersion()
	{
		// Return the cached value
		if (cAppLauncherVersion != null)
			return cAppLauncherVersion;

		// Return null if we are in a developers environment
		if (isDevelopersEnvironment == true)
			return null;

		// Legacy AppLaunchers do not configure this field. All legacy AppLaunchers will be defined as version: 0.0
		if (appLauncherVersion == null && isDevelopersEnvironment == false)
			cAppLauncherVersion = PlainVersion.Zero;
		else
			cAppLauncherVersion = PlainVersion.parse(appLauncherVersion);

		return cAppLauncherVersion;
	}

	/**
	 * Utility method to determine the path where the application is installed.
	 * <p>
	 * If this application is not a formal DistMaker application, then the working directory will be returned.
	 */
	public static File getAppPath()
	{
		File jarPath, currPath, testFile;

		// If we are in developer mode then return the developAppPath
		if (developAppPath != null)
			return developAppPath;

		// Return the working directory if this is a developers build
		if (isDevelopersEnvironment() == true)
			return new File(System.getProperty("user.dir"));

		jarPath = ReflectUtil.getInstalledRootDir(DistUtils.class);

		currPath = jarPath;
		while (currPath != null)
		{
			currPath = currPath.getParentFile();
			testFile = new File(currPath, "app.cfg");
			if (testFile.isFile() == true)
				return currPath;
		}

		// Return default (grandparent to jar) location
		return jarPath.getParentFile().getParentFile();
	}

	/**
	 * Returns the version of DistMaker which is running.
	 */
	public static Version getDistMakerVersion()
	{
		return DistApp.version;
	}

	/**
	 * Returns the JreVersion for the JRE which we are running on.
	 */
	public static JreVersion getJreVersion()
	{
		String jreVer;

		jreVer = System.getProperty("java.version");
		return new JreVersion(jreVer);
	}

	/**
	 * Returns the code associated with the update.
	 */
	public static int getUpdateCode()
	{
		return updateCode;
	}

	/**
	 * Returns the message associated with the update.
	 */
	public static String getUpdateMsg()
	{
		return updateMsg;
	}

	/**
	 * Utility method to determine if this appears to be running in the delevolper's environment (run from eclipse)
	 */
	public static boolean isDevelopersEnvironment()
	{
		return isDevelopersEnvironment;
	}

	/**
	 * Utility method to determine if the JRE is embedded with this application.
	 */
	public static boolean isJreBundled()
	{
		Path rootPath, jrePath;

		// Get the top level folder of our installation
		rootPath = getAppPath().toPath().getParent();

		// Get the path to the JRE
		jrePath = Paths.get(System.getProperty("java.home"));

		// Determine if the java.home JRE is a subdirectory of the top level app folder
		if (jrePath.startsWith(rootPath) == true)
			return true;

		return false;
	}

	/**
	 * Returns the list of available releases.
	 */
	public static List<AppRelease> getAvailableAppReleases(Task aTask, URL aUpdateUrl, String appName,
			Credential aCredential)
	{
		List<AppRelease> fullL;
		AppRelease workAR;
		URL catUrl;
		URLConnection connection;
		InputStream inStream;
		BufferedReader bufReader;
		String errMsg;

		errMsg = null;
		fullL = new ArrayList<>();
		catUrl = IoUtil.createURL(aUpdateUrl.toString() + "/" + appName + "/" + "appCatalog.txt");

		workAR = null;
		connection = null;
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
				String strLine;
				strLine = bufReader.readLine();

				// Bail once we are done
				if (strLine == null)
					break;

				// Ignore comments and empty lines
				if (strLine.isEmpty() == true || strLine.startsWith("#") == true)
					continue;

				String[] tokenArr;
				tokenArr = strLine.split(",");
				if (tokenArr.length == 2 && tokenArr[0].equals("name") == true)
					; // Nothing to do
				// Logic to handle the 'exit' command
				else if (tokenArr.length >= 1 && tokenArr[0].equals("exit") == true)
				{
					// We support exit commands with 3 tokens. All others
					// we will just exit.
					if (tokenArr.length != 3)
						break;

					String targName = tokenArr[1];
					String needVer = tokenArr[2];
					if (ParseUtils.shouldExitLogic(targName, needVer) == true)
						break;
				}
				// Logic to handle a distribution release
				else if (tokenArr.length == 3 && tokenArr[0].equals("R") == true)
				{
					DateUnit dateUnit;
					String verName;
					long buildTime;

					verName = tokenArr[1];

					dateUnit = new DateUnit("", "yyyyMMMdd HH:mm:ss");
					buildTime = dateUnit.parseString(tokenArr[2], 0);

					// Record the prior AppRelease
					if (workAR != null)
						fullL.add(workAR);

					workAR = new AppRelease(appName, verName, buildTime);
				}
				// Record any comments and associate with the current AppRelease
				else if (tokenArr.length >= 2 && tokenArr[0].equals("info") == true)
				{
					// We support the 'info,msg' instruction. Otherwise we just ignore info instructions silently
					if (tokenArr[1].equals("msg") == true && workAR != null)
					{
						// Retokenize to ensure at most we get only 3 tokens
						tokenArr = strLine.split(",", 3);

						String infoMsg;
						infoMsg = workAR.getInfoMsg();
						if (infoMsg != null)
							infoMsg += "\n";
						else
							infoMsg = "";
						if (tokenArr.length > 2)
							infoMsg += tokenArr[2];

						// Form an updated AppRelease with the updated infoMsg
						workAR = new AppRelease(workAR.getName(), workAR.getVersion(), workAR.getBuildTime(), infoMsg);
					}

				}
				else
				{
					aTask.logRegln("Unreconized line: " + strLine);
				}
			}

			// Add the last AppRelease
			if (workAR != null)
				fullL.add(workAR);
		}
		catch (IOException aExp)
		{
			// Friendly error message
			errMsg = NetUtil.getErrorCodeMessage(aUpdateUrl, connection, aExp);

			// Add the stack trace
			errMsg += "\n\n" + ThreadUtil.getStackTrace(aExp);
		}
		finally
		{
			IoUtil.forceClose(inStream);
			IoUtil.forceClose(bufReader);
		}

		// See if we are in a valid state
		if (errMsg != null)
			; // Nothing to do, as an earlier error has occurred
		else if (fullL.size() == 0)
			errMsg = "The update URL appears to be invalid.";

		// Bail if there were issues
		if (errMsg != null)
		{
			aTask.logRegln(errMsg);
			return null;
		}

		return fullL;
	}

	/**
	 * Utility method to determine if the specified path is fully writable by this process. This is done by making sure
	 * that all folders and child folders are writable by the current process. Note after this method is called, all
	 * folders will have the write permission bit set.
	 */
	public static boolean isFullyWriteable(File aPath)
	{
		// There is no known way to change, the write bit to true, in windows,
		// so by default, assume the path is writable. This method is totally unreliable on
		// the Windows platform (Gives bogus results for files on CDs).
		// TODO: See if File.canWrite(), returns the proper value on Windows
		if (System.getProperty("os.name").startsWith("Windows") == true)
			return true;

		if (aPath.isDirectory() == false)
			throw new RuntimeException("Specified path is not a folder: " + aPath);

		// Change the reference path to be writable
		if (aPath.setWritable(true) == false)
			return false;

		// Recurse on all child folders
		for (File aFile : aPath.listFiles())
		{
			// Check the child folder (recursively)
			if (aFile.isDirectory() == true && isFullyWriteable(aFile) == false)
				return false;
		}

		return true;
	}

	/**
	 * Returns the AppCatalog which describe the full content of an update specified in <aUpdateUrl>/catalog.txt
	 */
	public static AppCatalog readAppCatalog(Task aTask, File aCatalogFile, URL aUpdateUrl)
	{
		List<Node> nodeL;
		JreVersion minJreVersion, maxJreVersion;
		String errMsg, strLine;

		errMsg = null;
		nodeL = new ArrayList<>();
		minJreVersion = null;
		maxJreVersion = null;

		// Default to DigestType of MD5
		DigestType digestType;
		digestType = DigestType.MD5;

		try (BufferedReader tmpBR = new BufferedReader(
				new InputStreamReader(new BufferedInputStream(new FileInputStream(aCatalogFile))));)
		{
			String[] tokens;

			// Read the contents of the file
			while (true)
			{
				strLine = tmpBR.readLine();

				// Bail once we are done
				if (strLine == null)
					break;

				tokens = strLine.split(",", 4);
				if (strLine.isEmpty() == true || strLine.startsWith("#") == true)
					; // Nothing to do
				else if (tokens.length >= 1 && tokens[0].equals("exit") == true)
					break; // Bail once we get the 'exit' command
				else if (tokens.length == 2 && tokens[0].equals("P") == true)
				{
					String filename;

					// Form the PathNode
					filename = tokens[1];
					nodeL.add(new PathNode(aUpdateUrl, filename));
				}
				else if (tokens.length == 4 && tokens[0].equals("F") == true)
				{
					String filename, digestStr;
					long fileLen;

					// Form the FileNode
					digestStr = tokens[1];
					fileLen = ParseUtil.readLong(tokens[2], -1);
					filename = tokens[3];
					nodeL.add(new FileNode(aUpdateUrl, filename, new Digest(digestType, digestStr), fileLen));
				}
				else if (tokens.length == 2 && tokens[0].equals("digest") == true)
				{
					DigestType tmpDigestType;

					tmpDigestType = DigestType.parse(tokens[1]);
					if (tmpDigestType == null)
						aTask.logRegln("Failed to locate DigestType for: " + tokens[1]);
					else
						digestType = tmpDigestType;
				}
				else if ((tokens.length == 2 || tokens.length == 3) && tokens[0].equals("jre") == true)
				{
					if (minJreVersion != null)
					{
						aTask.logRegln("JRE version has already been specified. Current ver: " + minJreVersion.getLabel()
						+ " Requested ver: " + tokens[1] + ". Skipping...");
						continue;
					}

					minJreVersion = new JreVersion(tokens[1]);
					if (tokens.length == 3)
						maxJreVersion = new JreVersion(tokens[2]);
				}
				else
				{
					aTask.logRegln("Unreconized line: " + strLine);
				}
			}
		}
		catch (IOException aExp)
		{
			errMsg = ThreadUtil.getStackTrace(aExp);
		}

		// See if we are in a valid state
		if (errMsg != null)
			; // Nothing to do, as an earlier error has occurred
		else if (nodeL.size() == 0)
			errMsg = "The catalog appears to be invalid.";

		// Bail if there were issues
		if (errMsg != null)
		{
			aTask.logRegln(errMsg);
			return null;
		}

		return new AppCatalog(nodeL, minJreVersion, maxJreVersion);
	}

	/**
	 * Utility method to switch the DistMaker library into debug mode. You should never call this method unless you are
	 * modifying the DistMaker library.
	 * <p>
	 * This functionality only exists to allow rapid development of DistMaker
	 */
	public static void setDebugDeveloperDetails(int aUpdateCode, String aUpdateMsg, File aDevelopAppPath)
	{
		// If we are not in developers mode, then the developer made a mistake, and their request will be ignored
		if (isDevelopersEnvironment != true)
		{
			System.out.println("The DistMaker package is not running in a developers environment!");
			System.out.println("   DistUtils.setDebugDeveloperDetails() method is being ignored...");
			return;
		}

		// Switch the variables to developer debug mode
		isDevelopersEnvironment = false;
		updateCode = aUpdateCode;
		updateMsg = aUpdateMsg;

		developAppPath = aDevelopAppPath;

		// Log to the console that DistMaker has been configured into developer debug mode
		System.out.println("DistMaker has been forced into developer debug mode.");
		System.out.println("  AppPath has been forced to: " + developAppPath);
		System.out.println("  Regular users should never see this logic flow!");
	}

}
