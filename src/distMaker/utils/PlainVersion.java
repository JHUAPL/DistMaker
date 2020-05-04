package distMaker.utils;

/**
 * Provides the standard implementation of the Version interface.
 * 
 * @author lopeznr1
 */
public class PlainVersion implements Version
{
	// Constants
	public static PlainVersion AbsMin = new PlainVersion(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
	public static PlainVersion AbsMax = new PlainVersion(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
	public static PlainVersion Zero = new PlainVersion(0, 0, 0);

	// Attributes
	private final int major;
	private final int minor;
	private final int patch;

	public PlainVersion(int aMajor, int aMinor, int aPatch)
	{
		major = aMajor;
		minor = aMinor;
		patch = aPatch;
	}

	/**
	 * Forms a PlainVersion from the specified string. The version should have have at most 3 integer components
	 * separated by the char: '.'. Any extra components after the first 3 will be ignored. A NumberFormatException will
	 * be thrown if the type of any of the first 3 are not integers.
	 */
	public static PlainVersion parse(String aStr)
	{
		String[] tokenArr = aStr.split("\\.");

		int major = 0, minor = 0, patch = 0;
		major = Integer.parseInt(tokenArr[0]);
		if (tokenArr.length >= 2)
			minor = Integer.parseInt(tokenArr[1]);
		if (tokenArr.length >= 3)
			patch = Integer.parseInt(tokenArr[2]);

		return new PlainVersion(major, minor, patch);
	}

	@Override
	public int getMajorVersion()
	{
		return major;
	}

	@Override
	public int getMinorVersion()
	{
		return minor;
	}

	@Override
	public int getPatchVersion()
	{
		return patch;
	}

	@Override
	public String toString()
	{
		String retStr = "" + major + "." + minor;
		if (patch != 0)
			retStr += "." + patch;

		return retStr;
	}

}
