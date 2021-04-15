package distMaker.platform;

import java.io.File;

import distMaker.ErrorDM;
import distMaker.jre.*;
import distMaker.node.AppRelease;
import glum.version.Version;

/**
 * Collection of utility methods that provide platform independent mechanism for the following:
 * <ul>
 * <li>Retrieval of the DistMaker configuration file
 * <li>Retrieval of the file name of the app launcher.
 * <li>Retrieval of the location of the app launcher.
 * <li>Retrieval / Setting of the JRE location.
 * <li>Retrieval of the system {@link Platform}.
 * <li>Setting of the heap memory.
 * <li>Transformation of a platform string into the corresponding {@link Platform}.
 * </ul>
 * Note that setting of system parameters will not take effect until the DistMaker application is restarted.
 *
 * @author lopeznr1
 */
public class PlatformUtils
{
	/**
	 * Returns the file name that should be used for a specific AppLauncher version.
	 * <p>
	 * Namely legacy AppLauncher versions (versions equal to 0.0.x) will be expanded to:<br>
	 * <b>{@code appLauncher.jar}</b><br>
	 * while non legacy versions will be expanded to something like:<br>
	 * <b>{@code appLauncher-<version>.jar}</b>
	 */
	public static String getAppLauncherFileName(Version aVersion)
	{
		if (aVersion.getMajorVersion() == 0 && aVersion.getMinorVersion() == 0)
			return "appLauncher.jar";

		return "appLauncher-" + aVersion + ".jar";
	}

	/**
	 * Utility method that returns the relative path where the specified AppLauncher is installed.
	 * <p>
	 * The returned path will be relative to the top of the application's DistMaker root rather than the applications
	 * Java run path.
	 * <p>
	 * On failure this method will throw an exception of type {@link ErrorDM}.
	 */
	public static String getAppLauncherLocation(Version aVersion)
	{
		Platform platform;
		String tmpPathName;

		tmpPathName = getAppLauncherFileName(aVersion);

		// Delegate to the proper util class
		platform = PlatformUtils.getPlatform();
		if (platform == Platform.Linux)
			return "launcher/" + tmpPathName;
		else if (platform == Platform.Macosx)
			return "Java/" + tmpPathName;
		else if (platform == Platform.Windows)
			return "launcher/" + tmpPathName;

		throw new ErrorDM("Unsupported platform: " + platform);
	}

	/**
	 * Utility method that returns the platform specific configuration file for the java application.
	 * <p>
	 * On failure this method will throw an exception of type {@link ErrorDM}.
	 */
	public static File getConfigurationFile()
	{
		Platform platform;

		// Delegate to the proper util class
		platform = PlatformUtils.getPlatform();
		if (platform == Platform.Linux)
			return LinuxUtils.getScriptFile();
		else if (platform == Platform.Macosx)
			return AppleUtils.getPlistFile();
		else if (platform == Platform.Windows)
			return WindowsUtils.getConfigFile();

		throw new ErrorDM("Unsupported platform: " + platform);
	}

	/**
	 * Utility method that returns the relative path where the specified JRE should be unpacked to.
	 * <p>
	 * The returned path will be relative to the top of the application's DistMaker root rather than the applications
	 * Java run path.
	 * <p>
	 * On failure this method will throw an exception of type {@link ErrorDM}.
	 */
	public static String getJreLocation(JreVersion aJreVersion)
	{
		Platform platform;
		String tmpPathName;

		tmpPathName = JreUtils.getExpandJrePath(aJreVersion);

		// Delegate to the proper util class
		platform = PlatformUtils.getPlatform();
		if (platform == Platform.Linux)
			return tmpPathName;
		else if (platform == Platform.Macosx)
			return "PlugIns/" + tmpPathName;
		else if (platform == Platform.Windows)
			return tmpPathName;

		throw new ErrorDM("Unsupported platform: " + platform);
	}

	/**
	 * Returns the {@link Platform} on which the current JRE is running on.
	 * <p>
	 * If the platform is not recognized the a {@link ErrorDM} will be thrown.
	 */
	public static Platform getPlatform()
	{
		String osName = System.getProperty("os.name").toUpperCase();
		if (osName.startsWith("LINUX") == true)
			return Platform.Linux;
		else if (osName.startsWith("MAC OS X") == true)
			return Platform.Macosx;
		else if (osName.startsWith("WINDOWS") == true)
			return Platform.Windows;

		throw new ErrorDM("Unrecognized os.name: " + osName);
	}

	/**
	 * Utility method to configure the AppLauncher used by the (active) DistMaker distribution.
	 * <p>
	 * Note this will only take effect after the application has been restarted.
	 * <p>
	 * On failure this method will throw an exception of type {@link ErrorDM}.
	 *
	 * @param aRelease
	 *        The AppLauncher release that will be utilized.
	 */
	public static void setAppLauncher(AppLauncherRelease aRelease)
	{
		Platform platform;
		File cfgFile;

		// Retrieve the appropriate configuration file
		cfgFile = PlatformUtils.getConfigurationFile();

		// Delegate to the proper util class
		platform = PlatformUtils.getPlatform();
		if (platform == Platform.Linux)
			LinuxUtils.updateAppLauncher(aRelease, cfgFile);
		else if (platform == Platform.Macosx)
			AppleUtils.updateAppLauncher(aRelease, cfgFile);
		else if (platform == Platform.Windows)
			WindowsUtils.updateAppLauncher(aRelease, cfgFile);
		else
			throw new ErrorDM("Unrecognized platform: " + platform);
	}

	/**
	 * Utility method to configure the JRE version used by the (active) DistMaker distribution.
	 * <p>
	 * Note this will only take effect after the application has been restarted.
	 * <p>
	 * On failure this method will throw an exception of type {@link ErrorDM}.
	 *
	 * @param aJrePath
	 *        Path to top of the JRE.
	 */
	public static void setJreVersion(JreVersion aJreVersion)
	{
		Platform platform;
		File cfgFile;

		// Retrieve the appropriate configuration file
		cfgFile = PlatformUtils.getConfigurationFile();

		// Delegate to the proper util class
		platform = PlatformUtils.getPlatform();
		if (platform == Platform.Linux)
			LinuxUtils.updateJreVersion(aJreVersion, cfgFile);
		else if (platform == Platform.Macosx)
			AppleUtils.updateJreVersion(aJreVersion, cfgFile);
		else if (platform == Platform.Windows)
			WindowsUtils.updateJreVersion(aJreVersion, cfgFile);
		else
			throw new ErrorDM("Unrecognized platform: " + platform);
	}

	/**
	 * Utility method to configure the (active) DistMaker distribution to use the specified maxMem.
	 * <p>
	 * Note this will only take effect after the application has been restarted.
	 * <p>
	 * On failure this method will throw an exception of type {@link ErrorDM}.
	 *
	 * @param maxMemSize
	 *        Maximum heap memory in bytes.
	 */
	public static void setMaxHeapMem(long maxMemSize)
	{
		Platform platform;
		File cfgFile;

		// Retrieve the appropriate configuration file
		cfgFile = PlatformUtils.getConfigurationFile();

		// Delegate to the proper util class
		platform = PlatformUtils.getPlatform();
		if (platform == Platform.Linux)
			LinuxUtils.updateMaxMem(maxMemSize, cfgFile);
		else if (platform == Platform.Macosx)
			AppleUtils.updateMaxMem(maxMemSize, cfgFile);
		else if (platform == Platform.Windows)
			WindowsUtils.updateMaxMem(maxMemSize, cfgFile);
		else
			throw new ErrorDM(null, "Unrecognized platform: " + platform, "Unsupported Platform");
	}

	/**
	 * Utility method that takes a string and will transform it to the corresponding {@link Platform}.
	 * <p>
	 * Returns null if the platform could not be determined.
	 */
	public static Platform transformToPlatform(String aInputStr)
	{
		aInputStr = aInputStr.toLowerCase();

		if (aInputStr.equals("linux") == true)
			return Platform.Linux;

		if (aInputStr.equals("macosx") == true)
			return Platform.Macosx;

		if (aInputStr.equals("apple") == true)
			return Platform.Macosx;

		if (aInputStr.equals("windows") == true)
			return Platform.Windows;

		return null;
	}

	/**
	 * Utility method to update the (active) DistMaker distribution to reflect the specified AppRelease.
	 * <p>
	 * Note this will only take effect after the application has been restarted.
	 * <p>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateAppRelease(AppRelease aRelease)
	{
		Platform platform;
		File cfgFile;

		// Retrieve the appropriate configuration file
		cfgFile = PlatformUtils.getConfigurationFile();

		// Delegate to the proper util class
		platform = getPlatform();
		if (platform == Platform.Macosx)
			AppleUtils.updateAppVersion(aRelease.getVersion(), cfgFile);
	}

}
