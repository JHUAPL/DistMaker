package distMaker.jre;

import distMaker.digest.Digest;

/**
 * Immutable class that describes a JRE Release.
 * <P>
 * The reference fileName should be a compressed (tar.gz) JRE.
 */
public class JreRelease implements Comparable<JreRelease>
{
	private String platform;
	private JreVersion version;
	private Digest digest;
	private String fileName;
	private long fileLen;

	public JreRelease(String aPlatform, String aVersion, String aFileName, Digest aDigest, long aFileLen)
	{
		platform = aPlatform;
		version = new JreVersion(aVersion);
		fileName = aFileName;
		digest = aDigest;
		fileLen = aFileLen;
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
