package distMaker.utils;

/**
 * Utility class that allows for comparing Versions.
 * <P>
 * Eventually when Java allows operator overloading then this class can go away since the standard mathematical
 * comparison symbols would be much clearer.
 */
public class VersionUtils
{
	/**
	 * Utility method that returns true if aVerA occurs after aVerB
	 */
	public static boolean isAfter(Version aVerA, Version aVerB)
	{
		int majorA = aVerA.getMajorVersion();
		int minorA = aVerA.getMinorVersion();
		int patchA = aVerA.getPatchVersion();
		int majorB = aVerB.getMajorVersion();
		int minorB = aVerB.getMinorVersion();
		int patchB = aVerB.getPatchVersion();

		if (majorA > majorB)
			return true;
		if (majorA == majorB && minorA > minorB)
			return true;
		if (majorA == majorB && minorA == minorB && patchA > patchB)
			return true;

		return false;
	}

	/**
	 * Utility method that returns true if aVerA occurs after aVerB
	 */
	public static boolean isAfterOrEquar(Version aVerA, Version aVerB)
	{
		// Delegate to isAfter
		return isAfter(aVerB, aVerA) == false;
	}

	/**
	 * Utility method that returns true if the following statement is true:
	 * <P>
	 * aVerEval >= aVerMin && aVerEval <= aVerMax
	 * <P>
	 * A LogicError will be thrown if the aVerMin and aVerMax are inverted (aVerMin > aVerMax)
	 */
	public static boolean isInRange(Version aVerEval, Version aVerMin, Version aVerMax)
	{
		// Ensure the endpoints are not inverted
		if (isAfter(aVerMin, aVerMax) == true)
			throw new RuntimeException("Min/Max versions appear to be swapped. min: " + aVerMin + " max: " + aVerMax);

		// Decompose and delegate
		if (isAfter(aVerMin, aVerEval) == true)
			return false;
		if (isAfter(aVerEval, aVerMax) == true)
			return false;

		return true;
	}

	/**
	 * Utility method to allow the comparison of two versions.
	 * 
	 * @param aVerA
	 * @param aVerB
	 * @return
	 */
	public static int compare(Version aVerA, Version aVerB)
	{

		int majorA = aVerA.getMajorVersion();
		int minorA = aVerA.getMinorVersion();
		int patchA = aVerA.getPatchVersion();
		int majorB = aVerB.getMajorVersion();
		int minorB = aVerB.getMinorVersion();
		int patchB = aVerB.getPatchVersion();

		int cmpVal;
		cmpVal = majorA - majorB;
		if (cmpVal != 0)
			return cmpVal;
		cmpVal = minorA - minorB;
		if (cmpVal != 0)
			return cmpVal;
		cmpVal = patchA - patchB;
		if (cmpVal != 0)
			return cmpVal;

		return 0;
	}
}
