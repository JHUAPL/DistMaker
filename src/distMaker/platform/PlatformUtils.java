package distMaker.platform;

import java.io.File;

import distMaker.ErrorDM;
import distMaker.jre.*;
import distMaker.node.AppRelease;
import distMaker.utils.Version;

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
	 * Returns the file name that should be used for a specific AppLauncher version.
	 * <P>
	 * Namely legacy AppLauncher versions (versions equal to 0.0.x) will be expanded to:<BR>
	 * <B>{@code appLauncher.jar}</B><BR>
	 * while non legacy versions will be expanded to something like:<BR>
	 * <B>{@code appLauncher-<version>.jar}</B>
	 */
	public static String getAppLauncherFileName(Version aVersion)
	{
		if (aVersion.getMajorVersion() == 0 && aVersion.getMinorVersion() == 0)
			return "appLauncher.jar";

		return "appLauncher-" + aVersion + ".jar";
	}

	/**
	 * Utility method that returns the relative path where the specified AppLauncher is installed.
	 * <P>
	 * The returned path will be relative to the top of the application's DistMaker root rather than the applications
	 * Java run path.
	 */
	public static String getAppLauncherLocation(Version aVersion)
	{
		String platform, tmpPathName, retPath;

		retPath = null;
		tmpPathName = getAppLauncherFileName(aVersion);

		platform = PlatformUtils.getPlatform().toUpperCase();
		if (platform.equals("APPLE") == true)
			retPath = "Java/" + tmpPathName;
		else if (platform.equals("LINUX") == true)
			retPath = "launcher/" + tmpPathName;
		else if (platform.equals("WINDOWS") == true)
			retPath = "launcher/" + tmpPathName;
		else
			throw new ErrorDM("Unsupported platform: " + platform);

		return retPath;
	}

	/**
	 * Utility method that returns the relative path where the specified JRE should be unpacked to.
	 * <P>
	 * The returned path will be relative to the top of the application's DistMaker root rather than the applications
	 * Java run path.
	 */
	public static String getJreLocation(JreVersion aJreVersion)
	{
		String platform, tmpPathName, retPath;

		retPath = null;
		tmpPathName = JreUtils.getExpandJrePath(aJreVersion);

		platform = PlatformUtils.getPlatform().toUpperCase();
		if (platform.equals("APPLE") == true)
			retPath = "PlugIns/" + tmpPathName;
		else if (platform.equals("LINUX") == true)
			retPath = tmpPathName;
		else if (platform.equals("WINDOWS") == true)
			retPath = tmpPathName;
		else
			throw new ErrorDM("Unsupported platform: " + platform);

		return retPath;
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
	 * Utility method to configure the AppLauncher used by the (active) DistMaker distribution.
	 * <P>
	 * Note this will only take effect after the application has been restarted.
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
	 * 
	 * @param aRelease
	 *        The AppLauncher release that will be utilized.
	 */
	public static void setAppLauncher(AppLauncherRelease aRelease)
	{
		String platform;
		File cfgFile;

		// Retrieve the appropriate configuration file
		cfgFile = PlatformUtils.getConfigurationFile();

		// Delegate to the proper platform code
		platform = PlatformUtils.getPlatform().toUpperCase();
		if (platform.equals("APPLE") == true)
			AppleUtils.updateAppLauncher(aRelease, cfgFile);
		else if (platform.equals("LINUX") == true)
			LinuxUtils.updateAppLauncher(aRelease, cfgFile);
		else if (platform.equals("WINDOWS") == true)
			WindowsUtils.updateAppLauncher(aRelease, cfgFile);
		else
			throw new ErrorDM("Unrecognized platform: " + platform);
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
		File cfgFile;

		// Retrieve the appropriate configuration file
		cfgFile = PlatformUtils.getConfigurationFile();

		// Delegate to the proper platform code
		platform = PlatformUtils.getPlatform().toUpperCase();
		if (platform.equals("APPLE") == true)
			AppleUtils.updateJreVersion(aJreVersion, cfgFile);
		else if (platform.equals("LINUX") == true)
			LinuxUtils.updateJreVersion(aJreVersion, cfgFile);
		else if (platform.equals("WINDOWS") == true)
			WindowsUtils.updateJreVersion(aJreVersion, cfgFile);
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
		File cfgFile;

		// Retrieve the appropriate configuration file
		cfgFile = PlatformUtils.getConfigurationFile();

		// Delegate to the proper platform code
		platform = PlatformUtils.getPlatform().toUpperCase();
		if (platform.equals("APPLE") == true)
			AppleUtils.updateMaxMem(maxMemSize, cfgFile);
		else if (platform.equals("LINUX") == true)
			LinuxUtils.updateMaxMem(maxMemSize, cfgFile);
		else if (platform.equals("WINDOWS") == true)
			WindowsUtils.updateMaxMem(maxMemSize, cfgFile);
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
		File cfgFile;

		// Retrieve the appropriate configuration file
		cfgFile = PlatformUtils.getConfigurationFile();

		// Delegate to the proper platform code
		platform = getPlatform().toUpperCase();
		if (platform.equals("APPLE") == true)
			AppleUtils.updateAppVersion(aRelease.getVersion(), cfgFile);
	}

}
