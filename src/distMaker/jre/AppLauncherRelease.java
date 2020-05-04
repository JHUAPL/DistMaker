package distMaker.jre;

import distMaker.utils.*;
import glum.digest.Digest;

/**
 * Immutable class that describes an AppLauncher release.
 * <P>
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
