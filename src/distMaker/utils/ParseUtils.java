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
package distMaker.utils;

import distMaker.DistUtils;
import glum.version.PlainVersion;
import glum.version.Version;

public class ParseUtils
{

	/**
	 * Utility method that processes the 'exit' instruction.
	 * <p>
	 * Returns true if the processing of the configuration file should exit.
	 * <p>
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
			var versionArr = aNeededVer.split("\\.");
			if (versionArr.length >= 1)
				needMajorVer = Integer.parseInt(versionArr[0]);
			if (versionArr.length >= 2)
				needMinorVer = Integer.parseInt(versionArr[1]);
		}
		catch (Throwable aExp)
		{
			// Ignore just assume version components are whatever we managed to parse
		}
		var needVer = new PlainVersion(needMajorVer, needMinorVer, 0);

		// Exit the logic if the needVer > evalVer
		if (needVer.major() > evalVer.major())
			return true;
		if (needVer.major() == needVer.major() && needVer.minor() > evalVer.minor())
			return true;

		return false;
	}

}
