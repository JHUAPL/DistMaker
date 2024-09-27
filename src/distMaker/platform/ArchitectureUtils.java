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
package distMaker.platform;

/**
 * Collection of utility methods that provide a mechanism for the following:
 * <ul>
 * <li>Retrieval of the system {@link Architecture}.
 * <li>Transformation of a architecture string into the corresponding {@link Architecture}.
 * </ul>
 * Note that setting of system parameters will not take effect until the DistMaker application is restarted.
 *
 * @author lopeznr1
 */
public class ArchitectureUtils
{
	/**
	 * Returns the architecture the current JRE is running on.
	 * <p>
	 * This always returns x64.
	 * <p>
	 * TODO: In the future update the code to return the architecture rather than assume x64!
	 */
	public static Architecture getArchitecture()
	{
		return Architecture.x64;
	}

	/**
	 * Utility method that takes a string and will transform it to the corresponding {@link Architecture}.
	 * <p>
	 * Returns null if the architecture could not be determined.
	 */
	public static Architecture transformToArchitecture(String aInputStr)
	{
		aInputStr = aInputStr.toLowerCase();

		if (aInputStr.equals("x64") == true)
			return Architecture.x64;

		return null;
	}

}
