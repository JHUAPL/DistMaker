package distMaker.platform;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import distMaker.DistUtils;
import distMaker.MiscUtils;

public class WindowsUtils
{
	/**
	 * Utility method to update the JRE to reflect the specified path.
	 * <P>
	 * TODO: Complete this comment and method.
	 */
	public static boolean updateJrePath(File aPath)
	{
		int zios_finish;
		return false;
	}

	/**
	 * Utility method to update the specified max memory (-Xmx) value in the text file (aFile) to the specified maxMemVal.
	 * <P>
	 * Note this method is very brittle, and assumes that there is a single value where the string, -Xmx, is specified in the script. It assumes this string will
	 * be surrounded by a single space character on each side.
	 */
	public static String updateMaxMem(long numBytes)
	{
		File configFile;
		List<String> inputList;
		String strLine, updateStr;
		boolean isProcessed;

		// Bail if we fail to locate the configFile.
		configFile = getConfigFile();
		if (configFile != null && configFile.isFile() == true)
			return "The config file could not be located.";

		isProcessed = false;
		inputList = new ArrayList<>();

		// Process our input
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(configFile))))
		{
			// Read the lines
			while (true)
			{
				strLine = br.readLine();
				if (strLine == null)
					break;

				updateStr = MemUtils.transformMaxMemHeapString(strLine, numBytes);
				if (updateStr != null)
				{
					isProcessed = true;
					strLine = updateStr;
				}

				inputList.add(strLine);
			}

			// Create a new max heap input config line if one is not specified
			if (isProcessed == false)
			{
				strLine = MemUtils.transformMaxMemHeapString("-Xmx256m", numBytes);
				inputList.add(strLine);
				inputList.add("\n");
				isProcessed = true;
			}
		}
		catch(Exception aExp)
		{
			aExp.printStackTrace();
			return "Failed while processing the config file: " + configFile;
		}

		// Update the script
		System.out.println("Updating contents of file: " + configFile);
		if (MiscUtils.writeDoc(configFile, inputList) == false)
			return "Failed to write the config file: " + configFile;

		// On success return null
		return null;
	}

	/**
	 * Returns the l4j runtime configuration file. If one can not be determined then this method will return null.
	 * <P>
	 * If the configuration file is determined but does not exist, then an empty configuration file will be created.
	 * <P>
	 * Note this method looks for a file that ends in .l4j.cfg, or an exe file and creates the corresponding config file.
	 * <P>
	 * If there are multiple .exe or .l4j.cfg files, then this method may grab the wrong file and fail.
	 */
	public static File getConfigFile()
	{
		File[] fileArr;
		File installPath;
		File retFile;

		installPath = DistUtils.getAppPath().getParentFile();
		fileArr = installPath.listFiles();

		// Attempt to locate the <appExe>.l4j.ini file
		retFile = null;
		for (File aFile : fileArr)
		{
			if (aFile.getName().endsWith(".l4j.ini") == true)
				retFile = aFile;
		}

		if (retFile == null)
		{
			for (File aFile : fileArr)
			{
				if (aFile.getName().endsWith(".exe") == true)
					retFile = new File(aFile.getParentFile(), aFile.getName().substring(0, aFile.getName().length() - 4) + ".l4j.ini");
			}
		}

		if (retFile == null)
			return null;

		if (retFile.isFile() == false)
		{
			try
			{
				retFile.createNewFile();
			}
			catch(IOException aExp)
			{
				aExp.printStackTrace();
				return null;
			}
		}

		System.out.println("Windows config file: " + retFile);
		return retFile;
	}

}
