package distMaker.jre;

import distMaker.digest.Digest;
import distMaker.utils.Version;

/**
 * Immutable class that describes a JRE Release.
 * <P>
 * The reference fileName should be a compressed (tar.gz) JRE.
 */
public class JreRelease implements Comparable<JreRelease>
{
	private final JreVersion version;
	private final Version alMinVer;
	private final Version alMaxVer;

	private final String archStr;
	private final String platStr;

	private final String fileName;
	private final Digest digest;
	private final long fileLen;

	public JreRelease(String aArchStr, String aPlatStr, JreVersion aVersion, String aFileName, Digest aDigest, long aFileLen, Version aAlMinVer, Version aAlMaxVer)
	{
		version = aVersion;
		alMinVer = aAlMinVer;
		alMaxVer = aAlMaxVer;

		archStr = aArchStr.toLowerCase();
		platStr = aPlatStr.toLowerCase();
	
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
	 * @param aArchStr
	 *        Architecture of the relevant system. (Ex: x64)
	 * @param aPlatStr
	 *        Platform of the relevant system. (Ex: linux)
	 * @return
	 */
	public boolean isSystemMatch(String aArchStr, String aPlatStr)
	{
		aArchStr = aArchStr.toLowerCase();
		aPlatStr = aPlatStr.toLowerCase();
		
		// Ensure the architecture matches
		if (archStr.equals(aArchStr) == false)
			return false;
		
		// Ensure the platform matches
		if (platStr.equals(aPlatStr) == false)
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

		cmpVal = archStr.compareTo(aItem.archStr);
		if (cmpVal != 0)
			return cmpVal;

		cmpVal = platStr.compareTo(aItem.platStr);
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
