package distMaker.platform;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import distMaker.*;
import distMaker.jre.JreVersion;

public class LinuxUtils
{
	/**
	 * Returns the executable script used to launch the JVM.
	 * <P>
	 * If there are multiple launch scripts then this method may grab the wrong file and fail.
	 * <P>
	 * TODO: In the future the launch script should pass itself as an argument to the JVM and DistMaker should keep track of that. If the script is significantly
	 * manipulated from the original the launch file may be improperly detected.
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static File getScriptFile()
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

		// Bail if we failed to locate a regular file
		if (retFile == null)
			throw new ErrorDM("The script file could not be located.");

		// Ensure the file is a regular fie
		if (retFile.isFile() == false)
			throw new ErrorDM("The script file is NOT a regular file.");

		// Ensure the file is executable. If this is really the script file used to launch us then it should be executable!
		if (Files.isExecutable(retFile.toPath()) == false)
			throw new ErrorDM("The script file is NOT executable.");

		return retFile;
	}

	/**
	 * Utility method to update the configuration to reflect the specified JRE version.
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateJreVersion(JreVersion aJreVersion)
	{
		// Utilize the system scriptFile and delegate.
		updateJreVersion(aJreVersion, getScriptFile());
	}

	/**
	 * Utility method to update the configuration to reflect the specified JRE version.
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateJreVersion(JreVersion aJreVersion, File aScriptFile)
	{
		List<String> inputList;
		String evalStr, tmpStr;
		int currLineNum, targLineNum;

		// Bail if the scritpFile is not writable
		if (aScriptFile.setWritable(true) == false)
			throw new ErrorDM("The script file is not writeable: " + aScriptFile);

		// Process our input
		inputList = new ArrayList<>();
		try (BufferedReader br = MiscUtils.openFileAsBufferedReader(aScriptFile))
		{
			// Read the lines
			currLineNum = 0;
			targLineNum = -1;
			while (true)
			{
				evalStr = br.readLine();
				if (evalStr == null)
					break;

				// Locate where the java executable is specified
				tmpStr = evalStr.trim();
				if (tmpStr.startsWith("javaExe=") == true)
					targLineNum = currLineNum;

				inputList.add(evalStr);
				currLineNum++;
			}
		}
		catch(IOException aExp)
		{
			throw new ErrorDM(aExp, "Failed while processing the script file: " + aScriptFile);
		}

		// Update the script
		if (targLineNum != -1)
			inputList.set(targLineNum, "javaExe=../jre" + aJreVersion.getLabel() + "/bin/java");
		else
			throw new ErrorDM("[" + aScriptFile + "] The script does not specify 'javaExe'.");

		// Write the scriptFile
		MiscUtils.writeDoc(aScriptFile, inputList);
	}

	/**
	 * Utility method to update the specified maxMem var in the script (aFile) to the requested number of bytes.
	 * <P>
	 * Note this method assumes the specified file is a shell script built by DistMaker where the var maxMem holds the proper (right side) specification for the
	 * JVM's -Xmx value.
	 * <P>
	 * If the maxMem var definition is moved in the script file to after the launch of the application then this method will (silently) fail to configure the
	 * value needed to launch the JVM.
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateMaxMem(long numBytes)
	{
		// Utilize the system scriptFile and delegate.
		updateMaxMem(numBytes, getScriptFile());
	}

	/**
	 * Utility method to update the specified maxMem var in the script (aFile) to the requested number of bytes.
	 * <P>
	 * Note this method assumes the specified file is a shell script built by DistMaker where the var maxMem holds the proper (right side) specification for the
	 * JVM's -Xmx value.
	 * <P>
	 * If the maxMem var definition is moved in the script file to after the launch of the application then this method will (silently) fail to configure the
	 * value needed to launch the JVM.
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateMaxMem(long numBytes, File aScriptFile)
	{
		List<String> inputList;
		String evalStr, memStr, tmpStr;
		int currLineNum, injectLineNum, targLineNum;

		// Bail if the scriptFile is not writable
		if (aScriptFile.setWritable(true) == false)
			throw new ErrorDM("The script file is not writeable: " + aScriptFile);

		// Process our input
		inputList = new ArrayList<>();
		try (BufferedReader br = MiscUtils.openFileAsBufferedReader(aScriptFile))
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
		catch(IOException aExp)
		{
			throw new ErrorDM(aExp, "Failed while processing the script file: " + aScriptFile);
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

		// Write the scriptFile
		MiscUtils.writeDoc(aScriptFile, inputList);
	}

}
