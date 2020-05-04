package distMaker.platform;

/**
 * Collection of utility methods that provide a mechanism for the following:
 * <UL>
 * <LI>Retrieval of the system {@link Architecture}.
 * <LI>Transformation of a architecture string into the corresponding {@link Architecture}.
 * </UL>
 * Note that setting of system parameters will not take effect until the DistMaker application is restarted.
 *
 * @author lopeznr1
 */
public class ArchitectureUtils
{
	/**
	 * Returns the architecture the current JRE is running on.
	 * <P>
	 * This always returns x64.
	 * <P>
	 * TODO: In the future update the code to return the architecture rather than assume x64!
	 */
	public static Architecture getArchitecture()
	{
		return Architecture.x64;
	}

	/**
	 * Utility method that takes a string and will transform it to the corresponding {@link Architecture}.
	 * <P>
	 * Returns null if the architecture could not be determined.
	 */
	public static Architecture transformToArchitecture(String aInputStr)
	{
		aInputStr = aInputStr.toLowerCase();

		if (aInputStr.equals("x64") == true)
			return Architecture.x64;

		return null;
	}

}
