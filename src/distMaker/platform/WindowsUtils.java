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
package distMaker.platform;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import distMaker.*;
import distMaker.jre.AppLauncherRelease;
import distMaker.jre.JreVersion;

/**
 * Collection of utility methods specific to the Windows platform.
 *
 * @author lopeznr1
 */
public class WindowsUtils
{
	/**
	 * Returns the l4j runtime configuration file. If one can not be determined then this method will return null.
	 * <p>
	 * If the configuration file is determined but does not exist, then an empty configuration file will be created.
	 * <p>
	 * Note this method looks for a file that ends in .l4j.cfg, or an exe file and creates the corresponding config file.
	 * <p>
	 * If there are multiple .exe or .l4j.cfg files, then this method may grab the wrong file and fail.
	 * <p>
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
	 * Utility method to update the configuration to reflect the specified AppLauncher version.
	 * <p>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateAppLauncher(AppLauncherRelease aRelease, File aConfigFile)
	{
		int zzz_incomplete_logic;
		throw new ErrorDM("The logic is incomplete.");
	}

	/**
	 * Utility method to update the configuration to reflect the specified JRE version.
	 * <p>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateJreVersion(JreVersion aJreVersion, File aConfigFile)
	{
		int zzz_incomplete_logic;
		throw new ErrorDM("The logic is incomplete.");
	}

	/**
	 * Utility method to update the specified max memory (-Xmx) value in the text file (aFile) to the specified
	 * maxMemVal.
	 * <p>
	 * Note this method is very brittle, and assumes that there is a single value where the string, -Xmx, is specified in
	 * the script. It assumes this string will be surrounded by a single space character on each side.
	 * <p>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateMaxMem(long numBytes, File aConfigFile)
	{
		List<String> inputL;
		String strLine, updateStr;
		boolean isProcessed;

		// Bail if the configFile is not writable
		if (aConfigFile.setWritable(true) == false)
			throw new ErrorDM("The config file is not writeable: " + aConfigFile);

		isProcessed = false;
		inputL = new ArrayList<>();

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

				inputL.add(strLine);
			}

			// Create a new max heap input config line if one is not specified
			if (isProcessed == false)
			{
				strLine = MemUtils.transformMaxMemHeapString("-Xmx256m", numBytes);
				inputL.add(strLine);
				inputL.add("\n");
				isProcessed = true;
			}
		}
		catch(Exception aExp)
		{
			throw new ErrorDM(aExp, "Failed while processing the config file: " + aConfigFile);
		}

		// Update the script
		MiscUtils.writeDoc(aConfigFile, inputL);
	}

}
