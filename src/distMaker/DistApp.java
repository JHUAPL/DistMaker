package distMaker;

import glum.version.PlainVersion;
import glum.version.Version;

/**
 * Provides the main entry point.
 * <p>
 * Application prints the library name and version.
 * <p>
 * This is used during the build process for making DistMaker releases.
 *
 * @author lopeznr1
 */
public class DistApp
{
	/** The DistMaker version is defined here. */
	public static final Version version = new PlainVersion(0, 57, 0);

	/**
	 * Main entry point that will print out the version of DistMaker to stdout.
	 */
	public static void main(String[] aArgArr)
	{
		System.out.println("DistMaker " + DistApp.version);
	}

}
