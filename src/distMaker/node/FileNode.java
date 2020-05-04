package distMaker.node;

import java.io.File;
import java.net.URL;

import glum.digest.Digest;
import glum.io.IoUtil;
import glum.net.Credential;
import glum.net.NetUtil;
import glum.task.Task;

/**
 * Immutable node describing a File.
 * 
 * @author lopeznr1
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

		fNode = (FileNode) aNode;
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
		// Determine the source URL to copy the contents from
		URL srcUrl = IoUtil.createURL(rootUrl.toString() + "/" + fileName);

		// Determine the file to transfer the contents to
		File dstFile = new File(dstPath, fileName);

		// Download the file
		if (NetUtil.download(aTask, srcUrl, dstFile, aCredential, fileLen, digest) == false)
			return false;

		return true;
	}
}
