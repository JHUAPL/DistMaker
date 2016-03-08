package distMaker;

import glum.gui.GuiUtil;
import glum.io.IoUtil;
import glum.net.*;
import glum.reflect.ReflectUtil;
import glum.task.ConsoleTask;
import glum.task.Task;
import glum.unit.DateUnit;
import glum.util.ThreadUtil;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

import distMaker.node.*;

public class DistUtils
{
	// Static members that may be automatically updated by the AppLauncher class loader.
	// Do not rename or change these variables, without changing the AppLauncher class loader.
	private static boolean isDevelopersEnvironment = true;
	private static int updateCode = 0; // Static member to declare the update status, 0: None, 1: Pass, 2: Fail
	private static String updateMsg = null;

	// Static field used only when developing DistMaker. Otherwise this field should always be null.
	private static File developAppPath = null;

	/**
	 * Utility method to determine the path where the application is installed.
	 * <P>
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
	 * Downloads the specified file from srcUrl to destFile. Returns true on success
	 * <P>
	 * Note the passed in task's progress will be updated from 0% to 100% at file download completion, If the specified
	 * file size is invalid (aFileSize <= 0) or the download turns out to be bigger than the specified size then there
	 * will be no progress update while the file is being downloaded - only at completion.
	 */
	@SuppressWarnings("resource")
	public static boolean downloadFile(Task aTask, URL aUrl, File aFile, Credential aCredential, long aFileSize)
	{
		URLConnection connection;
		InputStream inStream;
		OutputStream outStream;
		String errMsg;
		byte[] byteArr;
		double progressVal;
		long cntByteFull, cntByteCurr;
		int numBytes;

		// Ensure we have a valid aTask
		if (aTask == null)
			aTask = new ConsoleTask();

		// Allocate space for the byte buffer
		byteArr = new byte[10000];

		// Perform the actual copying
		inStream = null;
		outStream = null;
		connection = null;
		try
		{
			// Open the src stream (with a 30 sec connect timeout)
			connection = aUrl.openConnection();
//			connection.setConnectTimeout(30 * 1000);
//			connection.setReadTimeout(90 * 1000);

			// Open the input/output streams
			inStream = NetUtil.getInputStream(connection, aCredential);
			outStream = new FileOutputStream(aFile);

			// Copy the bytes from the instream to the outstream
			cntByteFull = aFileSize;
			cntByteCurr = 0;
			numBytes = 0;
			while (numBytes != -1)
			{
				numBytes = inStream.read(byteArr);
				if (numBytes > 0)
				{
					outStream.write(byteArr, 0, numBytes);
					cntByteCurr += numBytes;
				}

				// Update the progressVal to reflect the download progress. Note however that we do update the
				// progress to 100% since that would change the task to be flagged as inactive and thus cause
				// the download to be aborted prematurely.
				// TODO: In the future Tasks should not be marked as inactive based on progress values
				progressVal = 0;
				if (cntByteFull > 0)
				{
					progressVal = (cntByteCurr + 0.0) / cntByteFull;
					if (progressVal >= 1.0)
						progressVal = 0.99;
				}
				aTask.setProgress(progressVal);

				// Bail if aTask is aborted
				if (aTask.isActive() == false)
				{
					aTask.infoAppendln("File transfer request has been aborted...");
					aTask.infoAppendln("\tFile: " + aFile + " Bytes transferred: " + cntByteCurr);
					return false;
				}
			}

			// Mark aTask's progress as complete since the file was downloaded.
			aTask.setProgress(1.0);
		}
		catch (IOException aExp)
		{
			errMsg = getErrorCodeMessage(aUrl, connection, aExp);
			aTask.infoAppendln(errMsg);
			return false;
		}
		finally
		{
			IoUtil.forceClose(inStream);
			IoUtil.forceClose(outStream);
		}

		return true;
	}

	/**
	 * Returns the list of available releases.
	 */
	public static List<Release> getAvailableReleases(Task aTask, URL aUpdateUrl, String appName, Credential aCredential)
	{
		List<Release> fullList;
		URL catUrl;
		URLConnection connection;
		InputStream inStream;
		BufferedReader bufReader;
		DateUnit dateUnit;
		String errMsg;

		errMsg = null;
		fullList = new ArrayList<>();
		catUrl = IoUtil.createURL(aUpdateUrl.toString() + "/" + appName + "/" + "releaseInfo.txt");

		connection = null;
		inStream = null;
		bufReader = null;
		try
		{
			String[] tokens;
			String strLine, verName;
			long buildTime;

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

				tokens = strLine.split(",");
				if (strLine.isEmpty() == true || strLine.startsWith("#") == true)
					; // Nothing to do
				else if (tokens.length == 2 && tokens[0].equals("name") == true)
					; // Nothing to do
				else if (tokens.length != 2)
					aTask.infoAppendln("Unreconized line: " + strLine);
				else
				// if (tokens.length == 2)
				{
					verName = tokens[0];

					dateUnit = new DateUnit("", "yyyyMMMdd HH:mm:ss");
					buildTime = dateUnit.parseString(tokens[1], 0);

					fullList.add(new Release(appName, verName, buildTime));
				}
			}
		}
		catch (IOException aExp)
		{
			errMsg = getErrorCodeMessage(aUpdateUrl, connection, aExp);
		}
		finally
		{
			IoUtil.forceClose(inStream);
			IoUtil.forceClose(bufReader);
		}

		// See if we are in a valid state
		if (errMsg != null)
			; // Nothing to do, as an earlier error has occured
		else if (fullList.size() == 0)
			errMsg = "The update URL appears to be invalid.";

		// Bail if there were issues
		if (errMsg != null)
		{
			aTask.infoAppendln(errMsg);
			return null;
		}

		return fullList;
	}

	/**
	 * Helper method that converts an IOException to an understandable message
	 */
	private static String getErrorCodeMessage(URL aUpdateUrl, URLConnection aConnection, IOException aExp)
	{
		URL fetchUrl;
		Result result;
		String errMsg;

		// Dump the stack trace
		aExp.printStackTrace();

		// Form a user friendly exception
		errMsg = "The update site, " + aUpdateUrl + ", is not available.\n\t";

		result = NetUtil.getResult(aExp, aConnection);
		switch (result)
		{
			case BadCredentials:
			errMsg += "The update site is password protected and bad credentials were provided.\n";
			break;

			case ConnectFailure:
			case UnreachableHost:
			case UnsupportedConnection:
			errMsg += "The update site appears to be unreachable.\n";
			break;

			case Interrupted:
			errMsg += "The retrival of the remote file has been interrupted.\n";
			break;

			case InvalidResource:
			errMsg += "The remote file does not appear to be valid.\n";
			break;

			default:
			errMsg += "An undefined error occurred while retrieving the remote file.\n";
			break;
		}

		// Log the URL which we failed on
		fetchUrl = aConnection.getURL();
		errMsg += "\tURL: " + fetchUrl + "\n";

		return errMsg;
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
	 * Returns a map of Nodes which describe the full content of an update specified in <aUpdateUrl>/catalog.txt
	 */
	public static Map<String, Node> readCatalog(Task aTask, File catalogFile, URL aUpdateUrl)
	{
		Map<String, Node> retMap;
		InputStream inStream;
		BufferedReader bufReader;
		String errMsg;

		errMsg = null;
		retMap = new LinkedHashMap<>();

		inStream = null;
		bufReader = null;
		try
		{
			String[] tokens;
			String strLine, filename, md5sum;
			long fileLen;

			// Read the contents of the file
			inStream = new FileInputStream(catalogFile);
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
				else if (tokens.length == 2 && tokens[0].equals("P") == true)
				{
					filename = tokens[1];
					retMap.put(filename, new PathNode(aUpdateUrl, filename));
				}
				else if (tokens.length == 4 && tokens[0].equals("F") == true)
				{
					md5sum = tokens[1];
					fileLen = GuiUtil.readLong(tokens[2], -1);
					filename = tokens[3];
					retMap.put(filename, new FileNode(aUpdateUrl, filename, md5sum, fileLen));
				}
				else
				{
					aTask.infoAppendln("Unreconized line: " + strLine);
				}
			}
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
		else if (retMap.size() == 0)
			errMsg = "The catalog appears to be invalid.";

		// Bail if there were issues
		if (errMsg != null)
		{
			aTask.infoAppendln(errMsg);
			return null;
		}

		return retMap;
	}

	/**
	 * Utility method to switch the DistMaker library into debug mode. You should never call this method unless you are
	 * modifying the DistMaker library.
	 * <P>
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
