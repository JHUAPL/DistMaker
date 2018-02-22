package distMaker.platform;

import java.io.File;

import distMaker.DistUtils;
import distMaker.ErrorDM;
import distMaker.jre.*;
import distMaker.node.AppRelease;

public class PlatformUtils
{
	/**
	 * Utility method that returns the platform specific configuration file for the java application.
	 */
	public static File getConfigurationFile()
	{
		File cfgFile;
		String platform;

		platform = PlatformUtils.getPlatform().toUpperCase();
		if (platform.equals("APPLE") == true)
			cfgFile = AppleUtils.getPlistFile();
		else if (platform.equals("LINUX") == true)
			cfgFile = LinuxUtils.getScriptFile();
		else if (platform.equals("WINDOWS") == true)
			cfgFile = WindowsUtils.getConfigFile();
		else
			throw new ErrorDM("Unsupported platform: " + platform);

		return cfgFile;
	}

	/**
	 * Utility method that returns the path where the specified JRE should be unpacked to.
	 */
	public static File getJreLocation(JreRelease aJreRelease)
	{
		// Delegate to actual worker method
		return getJreLocation(aJreRelease.getVersion());
	}

	/**
	 * Utility method that returns the path where the specified JRE should be unpacked to.
	 */
	public static File getJreLocation(JreVersion aJreVersion)
	{
		String platform, relJrePathStr;
		File installPath, retJrePath;

		retJrePath = null;
		installPath = DistUtils.getAppPath();
		relJrePathStr = JreUtils.getExpandJrePath(aJreVersion);

		platform = PlatformUtils.getPlatform().toUpperCase();
		if (platform.equals("APPLE") == true)
			retJrePath = new File(installPath.getParentFile(), "PlugIns/" + relJrePathStr);
		else if (platform.equals("LINUX") == true)
			retJrePath = new File(installPath.getParentFile(), relJrePathStr);
		else if (platform.equals("WINDOWS") == true)
			retJrePath = new File(installPath.getParentFile(), relJrePathStr);

		return retJrePath;
	}

	/**
	 * Returns the platform (Apple, Linux, or Windows) on which the current JRE is running on.
	 */
	public static String getPlatform()
	{
		String osName;

		osName = System.getProperty("os.name").toUpperCase();
		if (osName.startsWith("LINUX") == true)
			return "Linux";
		if (osName.startsWith("MAC OS X") == true)
			return "Apple";
		if (osName.startsWith("WINDOWS") == true)
			return "Windows";

		return System.getProperty("os.name");
	}

	/**
	 * Utility method to configure the JRE version used by the (active) DistMaker distribution.
	 * <P>
	 * Note this will only take effect after the application has been restarted.
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
	 * 
	 * @param aJrePath
	 *        Path to top of the JRE.
	 */
	public static void setJreVersion(JreVersion aJreVersion)
	{
		String platform;

		// Delegate to the proper platform code
		platform = PlatformUtils.getPlatform().toUpperCase();
		if (platform.equals("APPLE") == true)
			AppleUtils.updateJreVersion(aJreVersion);
		else if (platform.equals("LINUX") == true)
			LinuxUtils.updateJreVersion(aJreVersion);
		else if (platform.equals("WINDOWS") == true)
			WindowsUtils.updateJreVersion(aJreVersion);
		else
			throw new ErrorDM("Unrecognized platform: " + platform);
	}

	/**
	 * Utility method to configure the (active) DistMaker distribution to use the specified maxMem.
	 * <P>
	 * Note this will only take effect after the application has been restarted.
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
	 * 
	 * @param maxMemSize
	 *        Maximum heap memory in bytes.
	 */
	public static void setMaxHeapMem(long maxMemSize)
	{
		String platform;

		// Delegate to the proper platform code
		platform = PlatformUtils.getPlatform().toUpperCase();
		if (platform.equals("APPLE") == true)
			AppleUtils.updateMaxMem(maxMemSize);
		else if (platform.equals("LINUX") == true)
			LinuxUtils.updateMaxMem(maxMemSize);
		else if (platform.equals("WINDOWS") == true)
			WindowsUtils.updateMaxMem(maxMemSize);
		else
			throw new ErrorDM(null, "Unrecognized platform: " + platform, "Unsupported Platform");
	}

	/**
	 * Utility method to update the (active) DistMaker distribution to reflect the specified AppRelease.
	 * <P>
	 * Note this will only take effect after the application has been restarted.
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateAppRelease(AppRelease aRelease)
	{
		String platform;

		// Delegate to the proper platform code
		platform = getPlatform().toUpperCase();
		if (platform.equals("APPLE") == true)
			AppleUtils.updateAppVersion(aRelease.getVersion());
	}

}
