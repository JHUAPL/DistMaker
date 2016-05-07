package distMaker.platform;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import distMaker.*;
import distMaker.jre.JreVersion;

public class WindowsUtils
{
	/**
	 * Returns the l4j runtime configuration file. If one can not be determined then this method will return null.
	 * <P>
	 * If the configuration file is determined but does not exist, then an empty configuration file will be created.
	 * <P>
	 * Note this method looks for a file that ends in .l4j.cfg, or an exe file and creates the corresponding config file.
	 * <P>
	 * If there are multiple .exe or .l4j.cfg files, then this method may grab the wrong file and fail.
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
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
			throw new ErrorDM("The config file could not be located.");

		if (retFile.isFile() == false)
		{
			try
			{
				retFile.createNewFile();
			}
			catch(IOException aExp)
			{
				throw new ErrorDM(aExp, "A default config file could not be created.");
			}
		}

		System.out.println("Windows config file: " + retFile);
		return retFile;
	}

	/**
	 * Utility method to update the configuration to reflect the specified JRE version.
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateJreVersion(JreVersion aJreVersion)
	{
		// Utilize the system configFile and delegate.
		updateJreVersion(aJreVersion, getConfigFile());
	}

	/**
	 * Utility method to update the configuration to reflect the specified JRE version.
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateJreVersion(JreVersion aJreVersion, File aConfigFile)
	{
		int zzz_incomplete_logic;
		throw new ErrorDM("The logic is incomplete.");
	}

	/**
	 * Utility method to update the specified max memory (-Xmx) value in the text file (aFile) to the specified maxMemVal.
	 * <P>
	 * Note this method is very brittle, and assumes that there is a single value where the string, -Xmx, is specified in the script. It assumes this string will
	 * be surrounded by a single space character on each side.
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateMaxMem(long numBytes)
	{
		// Utilize the system configFile and delegate.
		updateMaxMem(numBytes, getConfigFile());
	}

	/**
	 * Utility method to update the specified max memory (-Xmx) value in the text file (aFile) to the specified maxMemVal.
	 * <P>
	 * Note this method is very brittle, and assumes that there is a single value where the string, -Xmx, is specified in the script. It assumes this string will
	 * be surrounded by a single space character on each side.
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateMaxMem(long numBytes, File aConfigFile)
	{
		List<String> inputList;
		String strLine, updateStr;
		boolean isProcessed;

		// Bail if the configFile is not writable
		aConfigFile = getConfigFile();
		if (aConfigFile.setWritable(true) == false)
			throw new ErrorDM("The config file is not writeable: " + aConfigFile);

		isProcessed = false;
		inputList = new ArrayList<>();

		// Process our input
		try (BufferedReader br = MiscUtils.openFileAsBufferedReader(aConfigFile))
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
			throw new ErrorDM(aExp, "Failed while processing the config file: " + aConfigFile);
		}

		// Update the script
		MiscUtils.writeDoc(aConfigFile, inputList);
	}

}
