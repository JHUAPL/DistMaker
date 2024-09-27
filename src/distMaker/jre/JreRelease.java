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

import distMaker.platform.Architecture;
import distMaker.platform.Platform;
import glum.digest.Digest;
import glum.version.Version;

/**
 * Immutable class that describes a JRE Release.
 *
 * @author lopeznr1
 */
public class JreRelease implements Comparable<JreRelease>
{
	private final JreVersion version;
	private final Version alMinVer;
	private final Version alMaxVer;

	private final Architecture architecture;
	private final Platform platform;

	private final String fileName;
	private final Digest digest;
	private final long fileLen;

	/**
	 * Standard Constructor
	 */
	public JreRelease(Architecture aArchitecture, Platform aPlatform, JreVersion aVersion, String aFileName,
			Digest aDigest, long aFileLen, Version aAlMinVer, Version aAlMaxVer)
	{
		version = aVersion;
		alMinVer = aAlMinVer;
		alMaxVer = aAlMaxVer;

		architecture = aArchitecture;
		platform = aPlatform;

		fileName = aFileName;
		digest = aDigest;
		fileLen = aFileLen;
	}

	/**
	 * Returns the minimum AppLauncher version compatible with this JRE release.
	 */
	public Version getAppLauncherMinVersion()
	{
		return alMinVer;
	}

	/**
	 * Returns the maximum AppLauncher version compatible with this JRE release.
	 */
	public Version getAppLauncherMaxVersion()
	{
		return alMaxVer;
	}

	/**
	 * Returns the Digest associated with the JRE (tar.gz) file.
	 */
	public Digest getDigest()
	{
		return digest;
	}

	/**
	 * Returns the version of the JRE corresponding to this release.
	 */
	public JreVersion getVersion()
	{
		return version;
	}

	/**
	 * Returns true if the specified system matches the JRE's system.
	 *
	 * @param aArchitecture
	 *        {@link Architecture} of the relevant system.
	 * @param aPlatform
	 *        {@link Platform} of the relevant system.s
	 */
	public boolean isSystemMatch(Architecture aArchitecture, Platform aPlatform)
	{
		// Ensure the architecture matches
		if (architecture != aArchitecture)
			return false;

		// Ensure the platform matches
		if (platform != aPlatform)
			return false;

		return true;
	}

	/**
	 * Returns the length of the associated file
	 */
	public long getFileLen()
	{
		return fileLen;
	}

	/**
	 * Returns the filename of this (tar.gz) JRE release.
	 */
	public String getFileName()
	{
		return fileName;
	}

	@Override
	public int compareTo(JreRelease aItem)
	{
		int cmpVal;

		cmpVal = architecture.compareTo(aItem.architecture);
		if (cmpVal != 0)
			return cmpVal;

		cmpVal = platform.compareTo(aItem.platform);
		if (cmpVal != 0)
			return cmpVal;

		cmpVal = version.compareTo(aItem.version);
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
