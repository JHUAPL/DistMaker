package distMaker.utils;

/**
 * Interface which provides access to version components (major, minor, patch).
 * <P>
 * Each component is modeled as an integer and it is assumed that higher values correspond to more developed software.
 * <P>
 * Reference: https://semver.org/
 * <P>
 * Implementors of this interface should be immutable.
 * 
 * @author lopeznr1
 */
public interface Version
{
	/**
	 * Returns the major version component.
	 */
	public int getMajorVersion();

	/**
	 * Returns the minor version component.
	 */
	public int getMinorVersion();

	/**
	 * Returns the patch version component.
	 */
	public int getPatchVersion();

}
