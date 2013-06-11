package distMaker.platform;

import glum.io.IoUtil;

import java.io.*;
import java.util.List;

import com.google.common.collect.Lists;

import distMaker.DistUtils;

public class WindowsUtils
{
	/**
	 * Utility method to update the specified max memory (-Xmx) value in the text file (aFile) to the specified
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
			
			// Create a new max heap input config line if one is not specified
			if (isProcessed == false)
			{
				strLine = MemUtils.transformMaxMemHeapString("-Xmx256m", numBytes);
				inputList.add(strLine);
				inputList.add("\n");
				isProcessed = true;
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

		// Update the script
		System.out.println("Updating contents of file: " + aFile);
		return LinuxUtils.writeDoc(aFile, inputList);
	}

	/**
	 * Returns the l4j runtime configuration file. If one can not be determined then this method will return null.
	 * <P>
	 * If the configuration file is determined but does not exist, then an empty configuration file will be created.
	 * <P>
	 * Note this method looks for a file that ends in .l4j.cfg, or an exe file and creates the corresponding config file. 
	 * <P> If there are multiple .exe or .l4j.cfg files, then this method may grab the wrong file and fail. 
	 */
	public static File getConfigFile()
	{
		File[] fileArr;
		File installPath;
		File retFile;

		
		installPath = DistUtils.getAppPath().getParentFile();
		fileArr = installPath.listFiles();
		
		// Attempt to locate the <appExe>.l4j.ini file
		retFile = null;
		for (File aFile : fileArr)
		{
			if (aFile.getName().endsWith(".l4j.ini") == true)
				retFile = aFile;
		}
		
		if (retFile == null)
		{
			for (File aFile : fileArr)
			{
				if (aFile.getName().endsWith(".exe") == true)
					retFile = new File(aFile.getParentFile(), aFile.getName().substring(0, aFile.getName().length() - 4) + ".l4j.ini");
			}
		}
		
		if (retFile == null)
			return null;
		
		if (retFile.isFile() == false)
		{
			try
			{
				retFile.createNewFile();
			}
			catch(IOException aExp)
			{
				aExp.printStackTrace();
				return null;
			}
		}

		System.out.println("Windows config file: " + retFile);
		return retFile;
	}

}