package distMaker;

import java.io.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;

/**
 * Collection of generic utility methods that should be migrated to another library.
 */
public class MiscUtils
{
	public static void unTar(final File inputFile, final File aDestPath) throws Exception
	{
		throw new Exception("Incomplete...");
	}
	
	/**
	 * Helper method to convert a Unix base-10 mode into the equivalent string.
	 * <P>
	 * Example: 493 -> 'rwxr-xr-x'
	 * <P>
	 * The returned string will always be of length 9 
	 */
	public static String convertUnixModeToStr(int aMode)
	{
		char permArr[] = {'r', 'w', 'x', 'r', 'w', 'x', 'r', 'w', 'x'};
		
		for (int c1 = 8; c1 >= 0; c1--)
		{
			if (((aMode >> c1) & 0x01) != 1)
				permArr[8 - c1] = '-';
		}
		
		return new String(permArr);
	}

	/**
	 * Helper method to convert a Unix base-10 mode into the Set<PosixFilePermission>.
	 * <P>
	 * Example: 493 -> 'rwxr-xr-x'
	 * <P>
	 * The returned string will always be of length 9 
	 */
	public static Set<PosixFilePermission> convertUnixModeToPFP(int aMode)
	{
		return PosixFilePermissions.fromString(convertUnixModeToStr(aMode));
	}

	/**
	 * Helper method to output the specified strings to aFile
	 */
	public static boolean writeDoc(File aFile, List<String> strList)
	{
		// Output the strList
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(aFile)));)
		{
			// Write the lines
			for (String aStr : strList)
				bw.write(aStr + '\n');
		}
		catch(Exception aExp)
		{
			aExp.printStackTrace();
			return false;
		}

		return true;
	}

}
