package distMaker.jre;

import java.util.ArrayList;

import com.google.common.collect.ImmutableList;

import glum.io.ParseUtil;
import glum.version.Version;

/**
 * Immutable class which defines a Java version.
 *
 * @author lopeznr1
 */
public class JreVersion implements Comparable<JreVersion>, Version
{
	/** String used to construct this JreVersion */
	private final String label;

	/** List of integers corresponding to the various components of the JRE version. */
	private final ImmutableList<Integer> compL;

	/** Flag for legacy JRE versions. JRE versions prior to 9.0 are considered legacy. */
	private final boolean isLegacy;

	/**
	 * Standard Constructor
	 */
	public JreVersion(String aLabel)
	{
		label = aLabel;

		var tokenArr = label.split("[._]");
		var workL = new ArrayList<Integer>();
		for (String aStr : tokenArr)
		{
			int tmpVal = ParseUtil.readInt(aStr, Integer.MIN_VALUE);
			if (tmpVal == Integer.MIN_VALUE)
				break;

			workL.add(tmpVal);
		}
		compL = ImmutableList.copyOf(workL);

		if (compL.size() >= 2 && compL.get(0) == 1)
			isLegacy = true;
		else
			isLegacy = false;
	}

	/**
	 * Returns the version of the JRE as a string.
	 */
	public String getLabel()
	{
		return label;
	}

	/**
	 * Utility method that returns the better version.
	 * <p>
	 * The better version is defined as the later version (and the more specific version).
	 * <p>
	 * Returns null if the better version can not be determined or if the versions are equal.
	 */
	public static JreVersion getBetterVersion(JreVersion verA, JreVersion verB)
	{
		JreVersion defaultVer;

		// Default JreVersion is the version that is more specific
		defaultVer = null;
		if (verA.compL.size() < verB.compL.size())
			defaultVer = verB;
		else if (verB.compL.size() < verA.compL.size())
			defaultVer = verA;

		// Set the idxCnt to the less specific JreVersion
		var idxCnt = Math.min(verA.compL.size(), verB.compL.size());

		// Compare each integral component (which originated from the label)
		// Assume higher values correspond to later versions
		for (int c1 = 0; c1 < idxCnt; c1++)
		{
			var valA = verA.compL.get(c1);
			var valB = verB.compL.get(c1);
			if (valA > valB)
				return verA;
			if (valB > valA)
				return verB;
		}

		// Defaults to the defaultVer
		return defaultVer;
	}

	@Override
	public int major()
	{
		if (isLegacy == true)
			return compL.get(1);

		if (compL.size() >= 1)
			return compL.get(0);

		return 0;
	}

	@Override
	public int minor()
	{
		if (isLegacy == true)
		{
			if (compL.size() >= 3)
				return compL.get(2);
			return 0;
		}

		if (compL.size() >= 2)
			return compL.get(1);

		return 0;
	}

	@Override
	public int patch()
	{
		if (isLegacy == true)
		{
			if (compL.size() >= 4)
				return compL.get(3);
			return 0;
		}

		if (compL.size() >= 3)
			return compL.get(2);

		return 0;
	}

	@Override
	public int compareTo(JreVersion aItem)
	{
		// Note the natural ordering is from oldest version to most recent version
		var tmpVer = JreVersion.getBetterVersion(this, aItem);
		if (tmpVer == aItem)
			return -1;
		if (tmpVer == this)
			return +1;

		return 0;
	}

}
