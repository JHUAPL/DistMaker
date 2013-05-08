package distMaker.platform;

import glum.gui.panel.generic.MessagePanel;
import glum.unit.ByteUnit;

import java.io.File;
import java.lang.management.ManagementFactory;

import distMaker.DistUtils;

public class MemUtils
{
	// Constants
	public static final long KB_SIZE = 1024;
	public static final long MB_SIZE = 1024 * 1024;
	public static final long GB_SIZE = 1024 * 1024 * 1024;

	/**
	 * Utility method that attempts to compute the installed system memory (ram). If the installed system ram can not be
	 * computed, then the system is assumed to have 4 GB.
	 */
	@SuppressWarnings("restriction")
	public static long getInstalledSystemMemory()
	{
		ByteUnit byteUnit;
		long systemMem;

		// Attempt to interogate the system memory using the Sun/Oracle JVM specific method
		try
		{
			systemMem = ((com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean()).getTotalPhysicalMemorySize();

			byteUnit = new ByteUnit(2);
			System.out.println("Max memory on the system: " + byteUnit.getString(systemMem));
			return systemMem;
		}
		catch (Throwable aThrowable)
		{
			System.out.println("Failed to query the installed system memory! Assume system memory is 4 GB.");
			System.out.println("Exception: " + aThrowable.getLocalizedMessage());
			// aThrowable.printStackTrace();

			systemMem = 4 * GB_SIZE;
		}

		return systemMem;
	}

	/**
	 * Utility method to configure the (active) DistMaker distribution to use the specified maxMem.
	 * <P>
	 * Method will return false on failure.
	 * 
	 * @param warnPanel
	 *           GUI message panel to route error messages.
	 * @param maxMemSize
	 *           Maximum heap memory in bytes.
	 */
	public static boolean setMaxHeapMem(MessagePanel warnPanel, long maxMemSize)
	{
		File installPath, pFile, scriptFile;
		String errMsg;
		boolean isValidPlatform;

		// Get the top level install path
		installPath = DistUtils.getAppPath().getParentFile();
		isValidPlatform = false;

		// Apple specific platform files
		pFile = new File(installPath, "Info.plist");
		if (pFile.isFile() == false)
			pFile = new File(installPath.getParentFile(), "Info.plist");

		if (pFile.isFile() == true)
		{
			isValidPlatform = true;

			errMsg = null;
			if (pFile.setWritable(true) == false)
				errMsg = "Failure. No writable permmisions for file: " + pFile;
			else if (AppleFileUtil.updateMaxMem(pFile, maxMemSize) == false)
				errMsg = "Failure. Failed to update file: " + pFile;

			if (errMsg != null)
			{
				warnPanel.setTitle("Failed setting Apple properties.");
				warnPanel.setInfo(errMsg);
				warnPanel.setVisible(true);
				return false;
			}
		}

		// Linux specific platform files
		scriptFile = new File(installPath, "runEcho");
		if (scriptFile.isFile() == true)
		{
			isValidPlatform = true;

			errMsg = null;
			if (scriptFile.setWritable(true) == false)
				errMsg = "Failure. No writable permmisions for file: " + scriptFile;
			else if (LinuxFileUtil.updateMaxMem(scriptFile, maxMemSize) == false)
				errMsg = "Failure. Failed to update file: " + scriptFile;

			if (errMsg != null)
			{
				warnPanel.setTitle("Failed setting Linux configuration.");
				warnPanel.setInfo(errMsg);
				warnPanel.setVisible(true);
				return false;
			}
		}

		// Bail if no valid platform found
		if (isValidPlatform == false)
		{
			errMsg = "This does not appear to be a valid DistMaker build. Memory changes will not take effect.";

			warnPanel.setTitle("No valid DistMaker platform located.");
			warnPanel.setInfo(errMsg);
			warnPanel.setVisible(true);
			return false;
		}

		return true;
	}

	/**
	 * Utility method that takes an inputStr, locates the fragment -Xmx*, and replaces the fragment with the appropriate
	 * -Xmx with respect to numBytes.
	 * <P>
	 * This method is a bit brittle in that it assumes the -Xmx string is surrounded with 1 white space character.
	 * <P>
	 * This method will return null if the string, -Xmx, is not located within the inputStr.
	 */
	public static String transformMaxMemHeapString(String inputStr, long numBytes)
	{
		String[] evalArr;
		String memStr, oldStr, returnStr;

		// Bail if we do not locate the -Xmx string
		if (inputStr == null || inputStr.contains("-Xmx") == false)
			return null;

		// Determine the memStr to use
		if (numBytes % GB_SIZE == 0)
			memStr = "-Xmx" + (numBytes / GB_SIZE) + "G";
		else
			memStr = "-Xmx" + (numBytes / MB_SIZE) + "M";

		// Tokenize the input on space characters
		evalArr = inputStr.split(" ");
		for (int c2 = 0; c2 < evalArr.length; c2++)
		{
			oldStr = evalArr[c2];
			if (oldStr.startsWith("-Xmx") == true)
				evalArr[c2] = memStr;
		}

		// Reconstitute the new evalStr
		returnStr = "";
		for (String aStr : evalArr)
			returnStr += " " + aStr;
		if (returnStr.length() > 0)
			returnStr = returnStr.substring(1);

		System.out.println("  Old Version: " + inputStr);
		System.out.println("  New Version: " + returnStr);
		return returnStr;
	}

}
