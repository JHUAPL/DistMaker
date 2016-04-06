package distMaker.platform;

import glum.gui.panel.generic.MessagePanel;

import java.io.File;

import distMaker.DistUtils;
import distMaker.jre.JreRelease;

public class PlatformUtils
{
	/**
	 * Utility method that returns the path where the specified JRE should be unpacked to.
	 */
	public static File getJreLocation(JreRelease aJreRelease)
	{
		String platform, versionStr;
		File jrePath, installPath;

		jrePath = null;
		installPath = DistUtils.getAppPath();
		versionStr = aJreRelease.getVersion().getLabel();

		platform = DistUtils.getPlatform().toUpperCase();
		if (platform.equals("APPLE") == true)
			jrePath = new File(installPath.getParentFile(), "PlugIns/jre" + versionStr);
		else if (platform.equals("LINUX") == true)
			jrePath = new File(installPath.getParentFile(), "jre" + versionStr);
		else if (platform.equals("Windows") == true)
			jrePath = new File(installPath.getParentFile(), "jre" + versionStr);

		return jrePath;
	}

	/**
	 * Utility method to configure the JRE location used by the active DistMaker distribution.
	 * <P>
	 * Note this will only take effect after the application has been relaunched.
	 */
	public static boolean setJreLocation(File aPath)
	{
		String platform;
		boolean isPass;

		isPass = false;
		platform = DistUtils.getPlatform().toUpperCase();
		if (platform.equals("APPLE") == true)
			isPass = AppleUtils.updateJrePath(aPath);
		else if (platform.equals("LINUX") == true)
			isPass = LinuxUtils.updateJrePath(aPath);
		else if (platform.equals("Windows") == true)
			isPass = WindowsUtils.updateJrePath(aPath);

		return isPass;
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
		String platform;
		String errMsg;

		// Delegate to the proper platform code
		errMsg = null;
		platform = DistUtils.getPlatform().toUpperCase();
		if (platform.equals("APPLE") == true)
			errMsg = AppleUtils.updateMaxMem(maxMemSize);
		else if (platform.equals("LINUX") == true)
			errMsg = LinuxUtils.updateMaxMem(maxMemSize);
		else if (platform.equals("Windows") == true)
			errMsg = WindowsUtils.updateMaxMem(maxMemSize);
		else
			errMsg = "Unrecognized platform: " + platform;

		// Display the warnPanel with the the error condition
		if (errMsg != null)
		{
			if (platform.equals("APPLE") == true)
				warnPanel.setTitle("Failed setting Apple properties.");
			else if (platform.equals("LINUX") == true)
				warnPanel.setTitle("Failed setting Linux configuration.");
			else if (platform.equals("Windows") == true)
				warnPanel.setTitle("Failed setting Windows configuration.");
			else
				warnPanel.setTitle("Platform is not supported.");

			warnPanel.setInfo(errMsg);
			warnPanel.setVisible(true);
			return false;
		}

		return true;
	}

}
