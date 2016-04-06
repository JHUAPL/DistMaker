package distMaker.node;

import glum.io.IoUtil;
import glum.net.Credential;
import glum.task.Task;

import java.io.File;
import java.net.URL;
import java.security.MessageDigest;

import distMaker.DistUtils;
import distMaker.digest.Digest;
import distMaker.digest.DigestUtils;

/**
 * Immutable node describing a File.
 */
public class FileNode implements Node
{
	private final URL rootUrl;
	private final Digest digest;
	private final String fileName;
	private final long fileLen;

	public FileNode(URL aRootUrl, String aFileName, Digest aDigest, long aFileLen)
	{
		rootUrl = aRootUrl;
		fileName = aFileName;
		digest = aDigest;
		fileLen = aFileLen;
	}

	@Override
	public boolean areContentsEqual(Node aNode)
	{
		FileNode fNode;

		if (aNode instanceof FileNode == false)
			return false;

		fNode = (FileNode)aNode;
		if (fNode.digest.equals(digest) == false)
			return false;
		if (fNode.fileName.equals(fileName) == false)
			return false;
		if (fNode.fileLen != fileLen)
			return false;

		return true;
	}

	/**
	 * Returns the length of the associated file
	 */
	public long getFileLen()
	{
		return fileLen;
	}

	@Override
	public String getFileName()
	{
		return fileName;
	}

	@Override
	public boolean transferContentTo(Task aTask, Credential aCredential, File dstPath)
	{
		URL srcUrl;
		File dstFile;
		Digest tmpDigest;
		boolean isPass;

		// Determine the source URL to copy the contents from
		srcUrl = IoUtil.createURL(rootUrl.toString() + "/" + fileName);

		// Determine the file to transfer the contents to
		dstFile = new File(dstPath, fileName);

		// Download the file
		MessageDigest msgDigest = DigestUtils.getDigest(digest.getType());
		isPass = DistUtils.downloadFile(aTask, srcUrl, dstFile, aCredential, fileLen, msgDigest);
		if (isPass == false)
			return false;

		// Validate that the file was downloaded successfully
		tmpDigest = new Digest(digest.getType(), msgDigest.digest());
		if (digest.equals(tmpDigest) == false)
		{
			aTask.infoAppendln("\nThe download of the application appears to be corrupted.");
			aTask.infoAppendln("\tFile: " + fileName);
			aTask.infoAppendln("\t\tExpected " + digest.getDescr());
			aTask.infoAppendln("\t\tRecieved " + tmpDigest.getDescr() + "\n");
			return false;
		}

		return true;
	}
}
