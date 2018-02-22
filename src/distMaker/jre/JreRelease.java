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
	private final Version alMinVer;
	private final Version alMaxVer;
	private final String platform;
	private final JreVersion version;
	private final Digest digest;
	private final String fileName;
	private final long fileLen;

	public JreRelease(String aPlatform, String aVersion, String aFileName, Digest aDigest, long aFileLen, Version aAlMinVer, Version aAlMaxVer)
	{
		platform = aPlatform;
		version = new JreVersion(aVersion);
		fileName = aFileName;
		digest = aDigest;
		fileLen = aFileLen;
		alMinVer = aAlMinVer;
		alMaxVer = aAlMaxVer;
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
	 * Returns true if the specified platform matches our platform.
	 */
	public boolean isPlatformMatch(String aPlatform)
	{
		String platformStr;

		// Consider this JreRelease a match if our platform is contained within aPlatform
		platformStr = platform.toUpperCase();
		if (aPlatform.toUpperCase().contains(platformStr) == true)
			return true;

		// If our platform == APPLE - then check to see if aPlatform mathes against 'MACOSX'
		if (platformStr.equals("APPLE") == true && aPlatform.toUpperCase().contains("MACOSX") == true)
			return true;

		return false;
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
