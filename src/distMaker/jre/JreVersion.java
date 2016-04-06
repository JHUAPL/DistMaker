package distMaker.jre;

import glum.gui.GuiUtil;

public class JreVersion implements Comparable<JreVersion>
{
	private String version;

	public JreVersion(String aVersion)
	{
		version = aVersion;
	}

	/**
	 * Returns the version of the JRE as a string.
	 */
	public String getLabel()
	{
		return version;
	}

	/**
	 * Utility method that returns the better version.
	 * <P>
	 * The better version is defined as the later version (and the more specific version).
	 * <P>
	 * Returns null if the better version can not be determined
	 */
	public static JreVersion getBetterVersion(JreVersion verA, JreVersion verB)
	{
		JreVersion defaultVer;
		String[] tokenA, tokenB;
		int valA, valB, idxCnt;

		tokenA = verA.getLabel().split("[._]");
		tokenB = verB.getLabel().split("[._]");

		// Default return JreVersion is verA or the version that is more specific
		defaultVer = verA;
		if (tokenA.length < tokenB.length)
			defaultVer = verB;

		// Set the idxCnt to the less specific JreVersion
		idxCnt = tokenA.length;
		if (tokenB.length < tokenA.length)
			idxCnt = tokenB.length;

		// Compare each component of the version string. Each component should be separated by '.'
		// Assume each component is an integer where larger values correspond to later versions
		for (int c1 = 0; c1 < idxCnt; c1++)
		{
			valA = GuiUtil.readInt(tokenA[c1], -1);
			valB = GuiUtil.readInt(tokenB[c1], -1);
			if (valA == -1 && valB == -1)
				return null;

			if (valB == -1 || valA > valB)
				return verA;
			if (valA == -1 || valB > valA)
				return verB;
		}

		// Defaults to verA
		return defaultVer;
	}

	@Override
	public int compareTo(JreVersion aItem)
	{
		JreVersion tmpVer;

		// Note the natural ordering is from oldest version to most recent version
		tmpVer = JreVersion.getBetterVersion(this, aItem);
		if (tmpVer == aItem)
			return -1;
		if (tmpVer == this)
			return +1;

		return 0;
	}

}
