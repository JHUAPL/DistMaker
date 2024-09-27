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
package distMaker.jre;

import glum.digest.Digest;
import glum.version.*;

/**
 * Immutable class that describes an AppLauncher release.
 * <p>
 * The reference fileName should be a jar file.
 *
 * @author lopeznr1
 */
public class AppLauncherRelease implements Comparable<AppLauncherRelease>
{
	private final Version version;
	private final Digest digest;
	private final String fileName;
	private final long fileLen;

	public AppLauncherRelease(String aVersion, String aFileName, Digest aDigest, long aFileLen)
	{
		version = PlainVersion.parse(aVersion);
		fileName = aFileName;
		digest = aDigest;
		fileLen = aFileLen;
	}

	/**
	 * Returns the Digest associated with the AppLauncher (jar) file.
	 */
	public Digest getDigest()
	{
		return digest;
	}

	/**
	 * Returns the version of the AppLauncher corresponding to this release.
	 */
	public Version getVersion()
	{
		return version;
	}

	/**
	 * Returns the length of the associated file
	 */
	public long getFileLen()
	{
		return fileLen;
	}

	/**
	 * Returns the filename of this AppLauncher release.
	 */
	public String getFileName()
	{
		return fileName;
	}

	@Override
	public int compareTo(AppLauncherRelease aItem)
	{
		int cmpVal;

		cmpVal = VersionUtils.compare(version, aItem.version);
		if (cmpVal != 0)
			return cmpVal;

		cmpVal = fileName.compareTo(aItem.fileName);
		if (cmpVal != 0)
			return cmpVal;

		cmpVal = Long.compare(fileLen, aItem.fileLen);
		if (cmpVal != 0)
			return cmpVal;

		return 0;
	}

}
