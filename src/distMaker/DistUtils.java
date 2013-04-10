package distMaker;

import glum.io.IoUtil;
import glum.net.*;
import glum.reflect.ReflectUtil;
import glum.task.ConsoleTask;
import glum.task.Task;
import glum.unit.DateUnit;

import java.io.*;
import java.net.*;
import java.util.List;

import com.google.common.collect.Lists;

public class DistUtils
{
	// Static members that will be automatically updated by the AppLaunch
	// Static member to declare the update status, 0: None, 1: Pass, 2: Fail
	// This field will be automatically updated by the AppLauncher
	private static boolean isDevelopersEnvironment = true;
	private static int updateCode = 0;
	private static String updateMsg = null;

	/**
	 * Utility method to determine the path where the application is installed.
	 * <P>
	 * If this application is not a formal DistMaker application, then the working directory will be returned.
	 */
	public static File getAppPath()
	{
		File jarPath, currPath, testFile;

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

		// Return default location
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
	 */
	public static boolean downloadFile(Task aTask, URL aUrl, File aFile, Credential aCredential)
	{
		URLConnection connection;
		InputStream inStream;
		OutputStream outStream;
		String errMsg;
		byte[] byteArr;
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
			numBytes = 0;
			while (numBytes != -1)
			{
				numBytes = inStream.read(byteArr);
				if (numBytes > 0)
					outStream.write(byteArr, 0, numBytes);

				// Bail if aTask is aborted
				if (aTask.isActive() == false)
				{
					aTask.infoAppendln("Download of file: " + aFile + " has been aborted!");
					return false;
				}
			}
		}
		catch (IOException aExp)
		{
			errMsg = getErrorCodeMessage(aUrl, connection, aExp, aFile.getName());
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
		fullList = Lists.newArrayList();
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
			errMsg = getErrorCodeMessage(aUpdateUrl, connection, aExp, "releaseInfo.txt");
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
		else if (fullList.size() == 1)
			errMsg = "There are no updates of " + appName + ". Only one release has been made.";

		// Bail if there were issues
		if (errMsg != null)
		{
			aTask.infoAppendln(errMsg);
			return null;
		}

		return fullList;
	}

	/**
	 * Returns the list of files (relative to destPath) that are needed for the specified release.
	 */
	public static List<File> getFileListForRelease(Task aTask, URL aUpdateUrl, Release aRelease, File destPath, Credential aCredential)
	{
		List<File> fullList;
		URL md5sumUrl;
		URLConnection connection;
		InputStream inStream;
		BufferedReader bufReader;
		String errMsg;

		errMsg = null;
		fullList = Lists.newArrayList();
		md5sumUrl = IoUtil.createURL(aUpdateUrl.toString() + "/" + aRelease.getName() + "/" + aRelease.getVersion() + "/delta/md5sum.txt");

		connection = null;
		inStream = null;
		bufReader = null;
		try
		{
			String[] tokens;
			String strLine, filename;

			// Read the contents of the file
			connection = md5sumUrl.openConnection();
			inStream = NetUtil.getInputStream(connection, aCredential);
			bufReader = new BufferedReader(new InputStreamReader(new BufferedInputStream(inStream)));

			// Read the lines
			while (true)
			{
				strLine = bufReader.readLine();

				// Bail once we are done
				if (strLine == null)
					break;

				tokens = strLine.split("\\s+");
				if (strLine.isEmpty() == true || strLine.startsWith("#") == true)
					; // Nothing to do
				else if (tokens.length != 2)
					aTask.infoAppendln("Unreconized line: " + strLine);
				else
				// if (tokens.length == 2)
				{
					filename = tokens[1];
					fullList.add(new File(destPath, filename));
				}
			}
		}
		catch (IOException aExp)
		{
			errMsg = getErrorCodeMessage(aUpdateUrl, connection, aExp, "md5sum.txt");
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
			errMsg = "The md5sum URL appears to be invalid.";

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
	 * 
	 * @param remoteFileName
	 */
	private static String getErrorCodeMessage(URL aUpdateUrl, URLConnection aConnection, IOException aExp, String remoteFileName)
	{
		Result result;
		String errMsg;

		aExp.printStackTrace();
		errMsg = "The update site, " + aUpdateUrl + ", is not available.\n\t";

		result = NetUtil.getResult(aExp, aConnection);
		switch (result)
		{
			case BadCredentials:
			errMsg += "The update site is password protected and bad credentials were provided.";
			break;

			case ConnectFailure:
			case UnreachableHost:
			case UnsupportedConnection:
			errMsg += "The update site appears to be unreachable.";
			break;

			case Interrupted:
			errMsg += "The retrival of the remote file, releaseInfo.txt, has been interrupted.";
			break;

			case InvalidResource:
			errMsg += "The remote file, releaseInfo.txt, does not appear to be valid.";
			break;

			default:
			errMsg += "An undefined error occurred while retrieving the remote file, releaseInfo.txt.";
			break;
		}

		return errMsg;
	}

}
