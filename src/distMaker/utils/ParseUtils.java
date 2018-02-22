package distMaker.utils;

import distMaker.DistUtils;

public class ParseUtils
{

	/**
	 * Utility method that processes the 'exit' instruction.
	 * <P>
	 * Returns true if the processing of the configuration file should exit.
	 * <P>
	 * Processing of the configuration file should exit if the specified needed version is not met or the version string
	 * could not be parsed into major minor components.
	 * 
	 * @param aTargName
	 *        The target component whose version will be evaluated. Current supported values are one of the following:
	 *        [AppLauncher, DistMaker]
	 * @param aNeededVer
	 *        A string describing the minimum version that is required in order for this exit instruction to be ignored.
	 * @return Returns true if the needed version requirements are not met.
	 */
	public static boolean shouldExitLogic(String aTargName, String aNeededVer)
	{
		// We handle logic for the following targets: [AppLauncher, DistMaker]
		// If not one of the specified targets then further parsing should stop
		Version evalVer;
		if (aTargName.equals("DistMaker") == true)
			evalVer = DistUtils.getDistMakerVersion();
		else if (aTargName.equals("AppLauncher") == true)
			evalVer = DistUtils.getAppLauncherVersion();
		else
			return true;

		// Determine the needed version
		int needMajorVer = Integer.MAX_VALUE;
		int needMinorVer = Integer.MAX_VALUE;
		try
		{
			String[] versionArr;

			versionArr = aNeededVer.split("\\.");
			if (versionArr.length >= 1)
				needMajorVer = Integer.parseInt(versionArr[0]);
			if (versionArr.length >= 2)
				needMinorVer = Integer.parseInt(versionArr[1]);
		}
		catch(Throwable aExp)
		{
			// Ignore just assume version components are whatever we managed to parse
		}
		Version needVer;
		needVer = new PlainVersion(needMajorVer, needMinorVer, 0);

		// Exit the logic if the needVer > evalVer
		if (needVer.getMajorVersion() > evalVer.getMajorVersion())
			return true;
		if (needVer.getMajorVersion() == needVer.getMajorVersion() && needVer.getMinorVersion() > evalVer.getMinorVersion())
			return true;

		return false;
	}

}
