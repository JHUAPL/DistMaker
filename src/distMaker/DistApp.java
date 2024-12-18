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
package distMaker;

import glum.version.PlainVersion;
import glum.version.Version;

/**
 * Provides the main entry point.
 * <p>
 * Application prints the library name and version.
 * <p>
 * This is used during the build process for making DistMaker releases.
 *
 * @author lopeznr1
 */
public class DistApp
{
	/** The DistMaker version is defined here. */
	public static final Version version = new PlainVersion(0, 71, 0);

	/**
	 * Main entry point that will print out the version of DistMaker to stdout.
	 */
	public static void main(String[] aArgArr)
	{
		System.out.println("DistMaker " + DistApp.version);
	}

}
