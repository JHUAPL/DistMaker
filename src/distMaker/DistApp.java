package distMaker;

import distMaker.utils.PlainVersion;
import distMaker.utils.Version;

/**
 * Provides main entry point.
 * <P>
 * Currently this will just print the library name and the version. This is used during the build process for making
 * Distmaker releases.
 */
public class DistApp
{
	/** The DistMaker version is defined here. */
	public static final Version version = new PlainVersion(0, 49, 0);

	/**
	 * Main entry point that will print out the version of DistMaker to stdout.
	 */
	public static void main(String[] aArgArr)
	{
		System.out.println("DistMaker " + DistApp.version);
	}

}
