// Copyright (C) 2024 The Johns Hopkins University Applied Physics Laboratory LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package distMaker;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.*;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.utils.IOUtils;

import com.google.common.base.Strings;
import com.google.common.io.CountingInputStream;

import glum.digest.Digest;
import glum.digest.DigestUtils;
import glum.net.*;
import glum.task.Task;
import glum.util.ThreadUtil;

/**
 * Collection of generic utility methods that should be migrated to another library / class.
 *
 * @author lopeznr1
 */
public class MiscUtils
{
	/**
	 * Utility method to convert a Unix base-10 mode into the equivalent string.
	 * <p>
	 * Example: 493 -> 'rwxr-xr-x'
	 * <p>
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
	 * Utility method to convert a Unix base-10 mode into the Set<PosixFilePermission>.
	 * <p>
	 * Example: 493 -> 'rwxr-xr-x'
	 * <p>
	 * The returned string will always be of length 9
	 */
	public static Set<PosixFilePermission> convertUnixModeToPFP(int aMode)
	{
		return PosixFilePermissions.fromString(convertUnixModeToStr(aMode));
	}


	public static boolean download(Task aTask, URL aSrcUrl, File aDstFile, Credential aCredential, long aFileLen,
			Digest aTargDigest)
	{
		// Form the message digest of interest
		MessageDigest tmpMessageDigest = null;
		if (aTargDigest != null)
			tmpMessageDigest = DigestUtils.getDigest(aTargDigest.getType());

		try
		{
			NetUtil.download(aTask, aSrcUrl, aDstFile, aCredential, aFileLen, tmpMessageDigest, null);
			if (aTask.isAborted() == true)
			{
				aTask.logRegln("File download has been aborted...");
				aTask.logRegln("\tSource: " + aSrcUrl);
				aTask.logRegln("\tFile: " + aDstFile + "\n");
//				aTask.logRegln("\tFile: " + dstFile + " Bytes transferred: " + cntByteCurr);
				return false;
			}
		}
		catch (FetchError aExp)
		{
			aTask.logRegln("File download has failed...");
			aTask.logRegln("\tReason: " + aExp.getResult());
			aTask.logRegln("\tSource: " + aSrcUrl);
			aTask.logRegln("\tFile: " + aDstFile + "\n");
			return false;
		}

		// Success if there is no targDigest to validate against
		if (aTargDigest == null)
			return true;

		// Validate that the file was downloaded successfully
		Digest testDigest = new Digest(aTargDigest.getType(), tmpMessageDigest.digest());
		if (aTargDigest.equals(testDigest) == false)
		{
			aTask.logRegln("File download is corrupted...");
			aTask.logRegln("\tFile: " + aDstFile);
			aTask.logRegln("\t\tExpected " + aTargDigest.getDescr());
			aTask.logRegln("\t\tReceived " + testDigest.getDescr() + "\n");
			return false;
		}

		return true;
	}

	/**
	 * Returns the relative path component of aAbsolutePath relative to aBasePath.
	 * <p>
	 * Returns null if aAbsolutePath does not start with aBasePath.
	 */
	public static String getRelativePath(File aBasePath, File aAbsolutePath)
	{
		Path relPath;

		// Bail if aAbsolutePath does not start with aBasePath
		if (aAbsolutePath.toPath().startsWith(aBasePath.toPath()) == false)
			return null;

		relPath = aBasePath.toPath().relativize(aAbsolutePath.toPath());
		return relPath.toString();
	}

	/**
	 * Utility method that returns a BufferedReader corresponding to the specified file.
	 * <p>
	 * This helps reduce boiler plate code.
	 */
	public static BufferedReader openFileAsBufferedReader(File aFile) throws IOException
	{
		return new BufferedReader(new InputStreamReader(new FileInputStream(aFile)));
	}

	/**
	 * Helper method that prints the exception of ErrorDM in an intelligent fashion to the specified task.
	 * <p>
	 * All ErrorDM exceptions (and their causes) will be printed. If the cause is not of type ErrorDM then the stack
	 * trace will be printed as well.
	 */
	public static void printErrorDM(Task aTask, ErrorDM aErrorDM, int numTabs)
	{
		Throwable cause;
		String tabStr;

		tabStr = Strings.repeat("\t", numTabs);

		aTask.logRegln(tabStr + "Reason: " + aErrorDM.getMessage());
		cause = aErrorDM.getCause();
		while (cause != null)
		{
			if (cause instanceof ErrorDM)
			{
				aTask.logRegln(tabStr + "Reason: " + cause.getMessage());
			}
			else
			{
				aTask.logRegln(tabStr + "StackTrace: ");
				aTask.logRegln(ThreadUtil.getStackTrace(cause));
				break;
			}

			cause = aErrorDM.getCause();
		}

		aTask.logRegln("");
	}

	/**
	 * Unpacks an input file into an output file.
	 * <p>
	 * Source based off of:<br>
	 * http://stackoverflow.com/questions/315618/how-do-i-extract-a-tar-file-in-java/7556307#7556307
	 * <p>
	 * The output file is created in the output folder, having the same name as the input file, minus the '.tar'
	 * extension.
	 *
	 * @param inputFile
	 *        the input .tar.gz or zip file
	 * @param aDestPath
	 *        The destination folder where the content will be dumped.
	 * @throws IOException
	 * @throws FileNotFoundException
	 * @return The {@link List} of {@link File}s with the untared content.
	 * @throws ArchiveException
	 */
	public static List<File> unPack(Task aTask, final File inputFile, final File aDestPath) throws FileNotFoundException, IOException, ArchiveException
	{
		Map<File, Long> pathM;
		InputStream iStream;

		final List<File> untaredFileL = new ArrayList<>();
		long fullLen = inputFile.length();

		// Open up the stream to the tar file (set up a counting stream to allow for progress updates)
		CountingInputStream cntStream = new CountingInputStream(new FileInputStream(inputFile));
		iStream = cntStream;

		String archiverName;
		if (inputFile.getName().toUpperCase().endsWith(".ZIP") == false)
		{
			archiverName = "tar";
			iStream = new GZIPInputStream(iStream);
		}
		else
		{
			archiverName = "zip";
//			iStream = new ZipInputStream(iStream);
		}

		pathM = new LinkedHashMap<>();
		final ArchiveInputStream debInputStream = new ArchiveStreamFactory().createArchiveInputStream(archiverName, iStream);
		TarArchiveEntry entry = null;
		while ((entry = (TarArchiveEntry)debInputStream.getNextEntry()) != null)
		{
			// Bail if we have been aborted
			if (aTask.isActive() == false)
				return null;

			final File outputFile = new File(aDestPath, entry.getName());
			if (entry.isDirectory())
			{
				if (!outputFile.exists())
				{
					if (!outputFile.mkdirs())
					{
						throw new IllegalStateException(String.format("Couldn't create directory %s.", outputFile.getAbsolutePath()));
					}
				}

				long tmpUtc = entry.getModTime().getTime();
				outputFile.setLastModified(tmpUtc);
				pathM.put(outputFile, tmpUtc);
			}
			else if (entry.isSymbolicLink() == true)
			{
				// Ensure the parent folders exist
				outputFile.getParentFile().mkdirs();

				File tmpFile = new File(entry.getLinkName());
				Files.createSymbolicLink(outputFile.toPath(), tmpFile.toPath());

//				long tmpUtc = entry.getModTime().getTime();
//				outputFile.setLastModified(tmpUtc);
//				// TODO: In the future if you want the symbolic link to have the same time as that in the tar do something like the below
//				// since in Java (as of 1.8) it is impossible to set the time on the link rather than the target
//				Path outLink = Files.createSymbolicLink(outputFile.toPath(), tmpFile.toPath());
//				if (PlatformUtils.getPlatform().equals("Linux") == true)
//				{
//					DateUnit dUnit = new DateUnit("", "yyyyMMddHHmmss");
//					long tmpUtc = entry.getModTime().getTime();
//					String timeStamp = dUnit.getString(tmpUtc);
//					Runtime.getRuntime().exec("touch -h -t " + timeStamp + " " + outLink.toAbsolutePath());
//				}
			}
			else if (entry.isFile() == true)
			{
				// Ensure the parent folders exist
				outputFile.getParentFile().mkdirs();

				// Copy over the file
				OutputStream outputFileStream = new FileOutputStream(outputFile);
				IOUtils.copy(debInputStream, outputFileStream);
				outputFileStream.close();

				// Update the modified time of the file
				long tmpUtc = entry.getModTime().getTime();
				outputFile.setLastModified(tmpUtc);
			}
			else
			{
				System.err.println(String.format("Unrecognized entry: %s", entry.getName()));
			}

			// Update the mode on all (non symbolic) files / paths
			int mode = entry.getMode();
			if (entry.isSymbolicLink() == false)
			{
				String permStr;

				permStr = convertUnixModeToStr(mode);
				Files.setPosixFilePermissions(outputFile.toPath(), PosixFilePermissions.fromString(permStr));
//				System.out.println(String.format("\tMode: %d %x %s %s  name: %s", mode, mode, Integer.toOctalString(mode), permStr, entry.getName()));
			}

			untaredFileL.add(outputFile);

			// Update the progress bar
			aTask.logRegUpdate("\tUnpacked: " + entry.getName());
			long currLen = cntStream.getCount();
			aTask.setProgress(currLen / (fullLen + 0.0));
		}
		debInputStream.close();

		// Update all of the times on the folders last
		for (File aDir : pathM.keySet())
		{
			// Bail if we have been aborted
			if (aTask.isActive() == false)
				return null;

			aDir.setLastModified(pathM.get(aDir));
		}

		aTask.logRegln("\tUnpacked: " + untaredFileL.size() + " files\n");

		return untaredFileL;
	}

	/**
	 * Helper method to output the specified strings to aFile
	 * <p>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void writeDoc(File aFile, List<String> aStrL)
	{
		// Output the strList
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(aFile)));)
		{
			// Write the lines
			for (String aStr : aStrL)
				bw.write(aStr + '\n');
		}
		catch(IOException aExp)
		{
			throw new ErrorDM(aExp, "Failed to write the file: " + aFile);
		}
	}

}
