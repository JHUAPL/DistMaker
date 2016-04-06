package distMaker.platform;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import distMaker.DistUtils;
import distMaker.MiscUtils;

public class LinuxUtils
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
	 * Utility method to update the specified maxMem var in the script (aFile) to the requested number of bytes.
	 * <P>
	 * Note this method assumes the specified file is a shell script built by DistMaker where the var maxMem holds the proper (right side) specification for the
	 * JVM's -Xmx value.
	 * <P>
	 * If the maxMem var definition is moved in the script file to after the launch of the application then this method will (silently) fail to configure the
	 * value needed to launch the JVM.
	 */
	public static String updateMaxMem(long numBytes)
	{
		List<String> inputList;
		File scriptFile;
		String evalStr, memStr, tmpStr;
		int currLineNum, injectLineNum, targLineNum;

		// Bail if we fail to locate the scriptFile.
		scriptFile = getScriptFile();
		if (scriptFile == null)
			return "The script file could not be located.";
		// Bail if the script file is not a regular file.
		if (scriptFile.isFile() == false)
			return "The script file does not appear to be a regular file: " + scriptFile;
		// Bail if the script file is not writeable.
		if (scriptFile.setWritable(true) == false)
			return "The script file is not writeable: " + scriptFile;

		// Process our input
		inputList = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(scriptFile)));)
		{
			// Read the lines
			currLineNum = 0;
			targLineNum = -1;
			injectLineNum = -1;
			while (true)
			{
				evalStr = br.readLine();
				if (evalStr == null)
					break;

				// Locate where we should place our maxMem configuration var
				tmpStr = evalStr.trim();
				if (tmpStr.equals("# Define the maximum memory to allow the application to utilize") == true)
					injectLineNum = currLineNum + 1;
				else if (tmpStr.startsWith("maxMem=") == true)
					targLineNum = currLineNum;

				inputList.add(evalStr);
				currLineNum++;
			}
		}
		catch(Exception aExp)
		{
			aExp.printStackTrace();
			return "Failed while processing the script file: " + scriptFile;
		}

		// Determine the memStr to use
		if (numBytes % MemUtils.GB_SIZE == 0)
			memStr = "" + (numBytes / MemUtils.GB_SIZE) + "g";
		else
			memStr = "" + (numBytes / MemUtils.MB_SIZE) + "m";

		// Insert our changes into the script
		if (targLineNum != -1)
			inputList.set(targLineNum, "maxMem=" + memStr);
		else if (injectLineNum != -1 && injectLineNum < currLineNum)
			inputList.add(injectLineNum + 1, "maxMem=" + memStr);
		else
		{
			inputList.add(0, "# Define the maximum memory to allow the application to utilize");
			inputList.add(1, "maxMem=" + memStr + "\n");
		}

		// Update the script
		System.out.println("Updating contents of file: " + scriptFile);
		if (MiscUtils.writeDoc(scriptFile, inputList) == false)
			return "Failed to write the script file: " + scriptFile;

		// On success return null
		return null;
	}

	/**
	 * Returns the executable script used to launch the JVM. If one can not be determined then this method will return null.
	 * <P>
	 * If there are multiple launch scripts then this method may grab the wrong file and fail.
	 * <P>
	 * TODO: In the future the launch script should pass itself as an argument to the JVM and DistMaker should keep track of that. If the script is significantly
	 * manipulated from the original the launch file may be improperly detected.
	 */
	private static File getScriptFile()
	{
		File[] fileArr;
		File installPath;
		File retFile;

		installPath = DistUtils.getAppPath().getParentFile();
		fileArr = installPath.listFiles();

		// Attempt to locate the path that matches run* file
		retFile = null;
		for (File aFile : fileArr)
		{
			if (aFile.getName().startsWith("run") == true)
				retFile = aFile;
		}

		if (retFile == null)
			return null;

		if (retFile.isFile() == false && Files.isExecutable(retFile.toPath()) == false)
			return null;

		System.out.println("Linux launch file: " + retFile);
		return retFile;
	}

}
