package distMaker;

import glum.io.IoUtil;
import glum.net.*;
import glum.reflect.ReflectUtil;
import glum.task.Task;
import glum.unit.DateUnit;

import java.io.*;
import java.net.*;
import java.util.List;

import com.google.common.collect.Lists;

public class DistUtils
{
	/**
	 * Utility method to determine the path where the application is installed.
	 * <P>
	 * If this application is not a formal DistMaker application, then the parent folder corresponding to the folder
	 * which contains this jar will be returned.
	 */
	public static File getAppPath()
	{
		File jarPath, currPath, testFile;

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
	 * Utility method to determine if this appears to be a delevolper's release (run from eclipse)
	 */
	public static boolean isDevelopersRelease(Class<?> refClass)
	{
		String dataPath;
		URL aUrl;

		// Attempt to determine the default data directory
		aUrl = refClass.getResource(refClass.getSimpleName() + ".class");
		// System.out.println("URL:" + aUrl);
		try
		{
			dataPath = aUrl.toURI().toString();
			dataPath = URLDecoder.decode(dataPath, "UTF-8");
		}
		catch (Exception aExp)
		{
			dataPath = aUrl.getPath();
			try
			{
				dataPath = URLDecoder.decode(dataPath, "UTF-8");
			}
			catch (Exception aExp2)
			{
				;
			}
		}

		// Remove the jar file component from the path "!*.jar"
		if (dataPath.lastIndexOf("!") != -1)
			return false;
		else
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
			Result aResult;

			aExp.printStackTrace();
			errMsg = "The update site, " + aUpdateUrl + ", is not available.\n\t";

			aResult = NetUtil.getResult(aExp, connection);
			switch (aResult)
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
				errMsg += "The retrival of the catalog file has been interrupted.";
				break;

				case InvalidResource:
				errMsg += "The remote catalog file, releaseInfo.txt, does not appear to be valid.";
				break;

				default:
				errMsg += "An undefined error occurred while retrieving the remote catalog file.";
				break;
			}
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
			Result aResult;

			aExp.printStackTrace();
			errMsg = "The update site, " + aUpdateUrl + ", is not available.\n\t";

			aResult = NetUtil.getResult(aExp, connection);
			switch (aResult)
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
				errMsg += "The retrival of the md5sum file has been interrupted.";
				break;

				case InvalidResource:
				errMsg += "The remote md5sum file, md5sumInfo.txt, does not appear to be valid.";
				break;

				default:
				errMsg += "An undefined error occurred while retrieving the remote md5sum file.";
				break;
			}
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

}