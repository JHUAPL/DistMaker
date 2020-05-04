package distMaker.platform;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import distMaker.*;
import distMaker.jre.*;

/**
 * Collection of utility methods specific to the Linux platform.
 *
 * @author lopeznr1
 */
public class LinuxUtils
{
	/**
	 * Returns the executable script used to launch the JVM.
	 * <P>
	 * If there are multiple launch scripts then this method may grab the wrong file and fail.
	 * <P>
	 * TODO: In the future the launch script should pass itself as an argument to the JVM and DistMaker should keep track
	 * of that. If the script is significantly manipulated from the original the launch file may be improperly detected.
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

		// Ensure the file is executable. If this is the script file used to launch us then it should be executable!
		if (Files.isExecutable(retFile.toPath()) == false)
			throw new ErrorDM("The script file is NOT executable.");

		return retFile;
	}

	/**
	 * Utility method to update the configuration to reflect the specified JRE version.
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateAppLauncher(AppLauncherRelease aRelease, File aScriptFile)
	{
		List<String> inputL;
		String evalStr, tmpStr;
		boolean isFound;

		// Bail if the scritpFile is not writable
		if (aScriptFile.setWritable(true) == false)
			throw new ErrorDM("The script file is not writeable: " + aScriptFile);

		// Define the regex we will be searching for
		Pattern tmpPattern = Pattern.compile("\\-cp[\\s]+.*.jar");

		// Process our input
		isFound = false;
		inputL = new ArrayList<>();
		try (BufferedReader br = MiscUtils.openFileAsBufferedReader(aScriptFile))
		{
			// Read the lines
			while (true)
			{
				evalStr = br.readLine();
				if (evalStr == null)
					break;

				// Locate where the AppLauncher is specified
				tmpStr = evalStr.trim();
				if (tmpPattern.matcher(tmpStr).find() == true)
				{
					String repStr;

					// Perform an inline replacement
					repStr = "-cp ../launcher/" + PlatformUtils.getAppLauncherFileName(aRelease.getVersion());
					evalStr = tmpPattern.matcher(tmpStr).replaceFirst(repStr);

					isFound = true;
				}

				inputL.add(evalStr);
			}
		}
		catch(IOException aExp)
		{
			throw new ErrorDM(aExp, "Failed while processing the script file: " + aScriptFile);
		}

		// Fail if there was no update performed
		if (isFound == false)
			throw new ErrorDM("[" + aScriptFile + "] The script does not specify a valid class path.");

		// Write the scriptFile
		MiscUtils.writeDoc(aScriptFile, inputL);
	}

	/**
	 * Utility method to update the configuration to reflect the specified JRE version.
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateJreVersion(JreVersion aJreVersion, File aScriptFile)
	{
		List<String> inputL;
		String evalStr, tmpStr;
		boolean isFound;

		// Bail if the scritpFile is not writable
		if (aScriptFile.setWritable(true) == false)
			throw new ErrorDM("The script file is not writeable: " + aScriptFile);

		// Process our input
		isFound = false;
		inputL = new ArrayList<>();
		try (BufferedReader br = MiscUtils.openFileAsBufferedReader(aScriptFile))
		{
			// Read the lines
			while (true)
			{
				evalStr = br.readLine();
				if (evalStr == null)
					break;

				// Locate where the java executable is specified
				tmpStr = evalStr.trim();
				if (tmpStr.startsWith("javaExe=") == true)
				{
					isFound = true;
					evalStr = "javaExe=../" + JreUtils.getExpandJrePath(aJreVersion) + "/bin/java";
				}

				inputL.add(evalStr);
			}
		}
		catch(IOException aExp)
		{
			throw new ErrorDM(aExp, "Failed while processing the script file: " + aScriptFile);
		}

		// Fail if there was no update performed
		if (isFound == false)
			throw new ErrorDM("[" + aScriptFile + "] The script does not specify a valid JRE path.");

		// Write the scriptFile
		MiscUtils.writeDoc(aScriptFile, inputL);
	}

	/**
	 * Utility method to update the specified maxMem var in the script (aFile) to the requested number of bytes.
	 * <P>
	 * Note this method assumes the specified file is a shell script built by DistMaker where the var maxMem holds the
	 * proper (right side) specification for the JVM's -Xmx value.
	 * <P>
	 * If the maxMem var definition is moved in the script file to after the launch of the application then this method
	 * will (silently) fail to configure the value needed to launch the JVM.
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateMaxMem(long aNumBytes, File aScriptFile)
	{
		List<String> inputL;
		String evalStr, memStr, tmpStr;
		int currLineNum, injectLineNum, targLineNum;

		// Bail if the scriptFile is not writable
		if (aScriptFile.setWritable(true) == false)
			throw new ErrorDM("The script file is not writeable: " + aScriptFile);

		// Process our input
		inputL = new ArrayList<>();
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

				inputL.add(evalStr);
				currLineNum++;
			}
		}
		catch(IOException aExp)
		{
			throw new ErrorDM(aExp, "Failed while processing the script file: " + aScriptFile);
		}

		// Determine the memStr to use
		if (aNumBytes % MemUtils.GB_SIZE == 0)
			memStr = "" + (aNumBytes / MemUtils.GB_SIZE) + "g";
		else
			memStr = "" + (aNumBytes / MemUtils.MB_SIZE) + "m";

		// Insert our changes into the script
		if (targLineNum != -1)
			inputL.set(targLineNum, "maxMem=" + memStr);
		else if (injectLineNum != -1 && injectLineNum < currLineNum)
			inputL.add(injectLineNum + 1, "maxMem=" + memStr);
		else
		{
			inputL.add(0, "# Define the maximum memory to allow the application to utilize");
			inputL.add(1, "maxMem=" + memStr + "\n");
		}

		// Write the scriptFile
		MiscUtils.writeDoc(aScriptFile, inputL);
	}

}
