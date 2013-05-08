package distMaker.platform;

import glum.io.IoUtil;

import java.io.*;
import java.util.List;

import com.google.common.collect.Lists;

public class LinuxUtils
{

	/**
	 * Utility method to update the specified max memory (-Xmx) value in the plist file (aFile) to the specified
	 * maxMemVal.
	 * <P>
	 * Note this method is very brittle, and assumes that there is a single value where the string, -Xmx, is specified in
	 * the script. It assumes this string will be surrounded by a single space character on each side.
	 */
	public static boolean updateMaxMem(File aFile, long numBytes)
	{
		BufferedReader br;
		List<String> inputList;
		String strLine, updateStr;
		boolean isProcessed;

		inputList = Lists.newArrayList();
		isProcessed = false;

		// Process our input
		br = null;
		try
		{
			br = new BufferedReader(new InputStreamReader(new FileInputStream(aFile)));

			// Read the lines
			while (true)
			{
				strLine = br.readLine();
				if (strLine == null)
					break;

				updateStr = MemUtils.transformMaxMemHeapString(strLine, numBytes);
				if (updateStr != null)
				{
					isProcessed = true;
					strLine = updateStr;
				}

				inputList.add(strLine);
			}
		}
		catch (Exception aExp)
		{
			aExp.printStackTrace();
			return false;
		}
		finally
		{
			IoUtil.forceClose(br);
		}

		// Bail if we did not find a line to change
		if (isProcessed == false)
		{
			Exception aExp;
			aExp = new Exception("Failed to locate -Xmx string!");
			aExp.printStackTrace();
			return false;
		}

		// Update the script
		System.out.println("Updating contents of file: " + aFile);
		return writeDoc(aFile, inputList);
	}

	/**
	 * Helper method to output the specified strings to aFile
	 */
	private static boolean writeDoc(File aFile, List<String> strList)
	{
		BufferedWriter bw;

		// Output the strList
		bw = null;
		try
		{
			bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(aFile)));

			// Write the lines
			for (String aStr : strList)
				bw.write(aStr + '\n');
		}
		catch (Exception aExp)
		{
			aExp.printStackTrace();
			return false;
		}
		finally
		{
			IoUtil.forceClose(bw);
		}

		return true;
	}

}
