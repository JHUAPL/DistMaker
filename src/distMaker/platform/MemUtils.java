package distMaker.platform;

import glum.reflect.ReflectUtil;
import glum.unit.ByteUnit;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;

public class MemUtils
{
	// Constants
	public static final long KB_SIZE = 1024;
	public static final long MB_SIZE = 1024 * 1024;
	public static final long GB_SIZE = 1024 * 1024 * 1024;

	/**
	 * Utility method that attempts to compute the installed system memory (ram). If the installed system ram can not be
	 * computed, then the system is assumed to have 4 GB.
	 */
	public static long getInstalledSystemMemory()
	{
		ByteUnit byteUnit;
		long systemMem;

		// Attempt to interrogate the system memory using the Sun/Oracle JVM specific method
		try
		{
			OperatingSystemMXBean osBean;
			Class<?> tmpClass;
			Method tmpMethod;

			// Note we gather the available memory via reflection to avoid Eclipse/IDE compilation errors due
			// to restricted com.sun.management package. The commented out single line of code is equivalent to
			// the next block (4 lines) of code.
			// Retrieve the OS available memory via reflection equivalent to the commented method below:
//			systemMem = ((com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean()).getTotalPhysicalMemorySize();
			osBean = ManagementFactory.getOperatingSystemMXBean();
			tmpClass = Class.forName("com.sun.management.OperatingSystemMXBean");
			tmpMethod = ReflectUtil.locateMatchingMethod(tmpClass, "getTotalPhysicalMemorySize");
			systemMem = (Long)tmpMethod.invoke(osBean);

			byteUnit = new ByteUnit(2);
			System.out.println("Max memory on the system: " + byteUnit.getString(systemMem));
			return systemMem;
		}
		catch(Throwable aThrowable)
		{
			System.out.println("Failed to query the installed system memory! Assume system memory is 4 GB.");
			System.out.println("Exception: " + aThrowable.getLocalizedMessage());
			// aThrowable.printStackTrace();

			systemMem = 4 * GB_SIZE;
		}

		return systemMem;
	}

	/**
	 * Utility method that takes an inputStr, locates the fragment -Xmx*, and replaces the fragment with the appropriate
	 * -Xmx with respect to numBytes.
	 * <P>
	 * This method is a bit brittle in that it assumes the -Xmx string is surrounded with 1 white space character.
	 * <P>
	 * This method will return null if the string, -Xmx, is not located within the inputStr.
	 */
	public static String transformMaxMemHeapString(String inputStr, long numBytes)
	{
		String[] evalArr;
		String memStr, oldStr, returnStr;

		// Bail if we do not locate the -Xmx string
		if (inputStr == null || inputStr.contains("-Xmx") == false)
			return null;

		// Determine the memStr to use
		if (numBytes % GB_SIZE == 0)
			memStr = "-Xmx" + (numBytes / GB_SIZE) + "g";
		else
			memStr = "-Xmx" + (numBytes / MB_SIZE) + "m";

		// Tokenize the input on space characters
		evalArr = inputStr.split(" ");
		for (int c2 = 0; c2 < evalArr.length; c2++)
		{
			oldStr = evalArr[c2];
			if (oldStr.startsWith("-Xmx") == true)
				evalArr[c2] = memStr;
		}

		// Reconstitute the new evalStr
		returnStr = "";
		for (String aStr : evalArr)
			returnStr += " " + aStr;
		if (returnStr.length() > 0)
			returnStr = returnStr.substring(1);

		System.out.println("  Old Version: " + inputStr);
		System.out.println("  New Version: " + returnStr);
		return returnStr;
	}

}
