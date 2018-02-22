package distMaker;

import glum.task.Task;
import glum.util.ThreadUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;

import com.google.common.base.Strings;
import com.google.common.io.CountingInputStream;

/**
 * Collection of generic utility methods that should be migrated to another library / class.
 */
public class MiscUtils
{
	/**
	 * Utility method to convert a Unix base-10 mode into the equivalent string.
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
	 * Utility method to convert a Unix base-10 mode into the Set<PosixFilePermission>.
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
	 * Returns the relative path component of aAbsolutePath relative to aBasePath.
	 * <P>
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
	 * <P>
	 * This helps reduce boiler plate code.
	 */
	public static BufferedReader openFileAsBufferedReader(File aFile) throws IOException
	{
		return new BufferedReader(new InputStreamReader(new FileInputStream(aFile)));
	}

	/**
	 * Helper method that prints the exception of ErrorDM in an intelligent fashion to the specified task.
	 * <P>
	 * All ErrorDM exceptions (and their causes) will be printed. If the cause is not of type ErrorDM then the stack
	 * trace will be printed as well.
	 */
	public static void printErrorDM(Task aTask, ErrorDM aErrorDM, int numTabs)
	{
		Throwable cause;
		String tabStr;

		tabStr = Strings.repeat("\t", numTabs);

		aTask.infoAppendln(tabStr + "Reason: " + aErrorDM.getMessage());
		cause = aErrorDM.getCause();
		while (cause != null)
		{
			if (cause instanceof ErrorDM)
			{
				aTask.infoAppendln(tabStr + "Reason: " + cause.getMessage());
			}
			else
			{
				aTask.infoAppendln(tabStr + "StackTrace: ");
				aTask.infoAppendln(ThreadUtil.getStackTrace(cause));
				break;
			}

			cause = aErrorDM.getCause();
		}

		aTask.infoAppendln("");
	}

	/**
	 * Untar an input file into an output file.
	 * <P>
	 * Source based off of:<BR>
	 * http://stackoverflow.com/questions/315618/how-do-i-extract-a-tar-file-in-java/7556307#7556307
	 * <P>
	 * The output file is created in the output folder, having the same name as the input file, minus the '.tar'
	 * extension.
	 * 
	 * @param inputFile
	 *        the input .tar file
	 * @param aDestPath
	 *        The destination folder where the content will be dumped.
	 * @throws IOException
	 * @throws FileNotFoundException
	 * @return The {@link List} of {@link File}s with the untared content.
	 * @throws ArchiveException
	 */
	public static List<File> unTar(Task aTask, final File inputFile, final File aDestPath) throws FileNotFoundException, IOException, ArchiveException
	{
		Map<File, Long> pathMap;
		InputStream iStream;

		final List<File> untaredFiles = new ArrayList<>();
		long fullLen = inputFile.length();

		// Open up the stream to the tar file (set up a counting stream to allow for progress updates)
		CountingInputStream cntStream = new CountingInputStream(new FileInputStream(inputFile));
		iStream = cntStream;
		if (inputFile.getName().toUpperCase().endsWith(".GZ") == true)
			iStream = new GZIPInputStream(iStream);

		pathMap = new LinkedHashMap<>();
		final TarArchiveInputStream debInputStream = (TarArchiveInputStream)new ArchiveStreamFactory().createArchiveInputStream("tar", iStream);
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
				pathMap.put(outputFile, tmpUtc);
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

			untaredFiles.add(outputFile);

			// Update the progress bar
			aTask.infoUpdate("\tUnpacked: " + entry.getName());
			long currLen = cntStream.getCount();
			aTask.setProgress(currLen / (fullLen + 0.0));
		}
		debInputStream.close();

		// Update all of the times on the folders last
		for (File aDir : pathMap.keySet())
		{
			// Bail if we have been aborted
			if (aTask.isActive() == false)
				return null;

			aDir.setLastModified(pathMap.get(aDir));
		}

		aTask.infoAppendln("\tUnpacked: " + untaredFiles.size() + " files\n");

		return untaredFiles;
	}

	/**
	 * Helper method to output the specified strings to aFile
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void writeDoc(File aFile, List<String> strList)
	{
		// Output the strList
		try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(aFile)));)
		{
			// Write the lines
			for (String aStr : strList)
				bw.write(aStr + '\n');
		}
		catch(IOException aExp)
		{
			throw new ErrorDM(aExp, "Failed to write the file: " + aFile);
		}
	}

}
