package distMaker.node;

import glum.io.IoUtil;
import glum.net.Credential;
import glum.task.Task;

import java.io.File;
import java.net.URL;

import distMaker.DistUtils;

public class FileNode implements Node
{
	protected URL rootUrl;
	protected String md5sum;
	protected String fileName;
	protected long fileLen;

	public FileNode(URL aRootUrl, String aFileName, String aMd5sum, long aFileLen)
	{
		rootUrl = aRootUrl;
		fileName = aFileName;
		md5sum = aMd5sum;
		fileLen = aFileLen;
	}

	@Override
	public boolean areContentsEqual(Node aNode)
	{
		FileNode fNode;

		if (aNode instanceof FileNode == false)
			return false;

		fNode = (FileNode)aNode;
		if (fNode.md5sum.equals(md5sum) == false)
			return false;
		if (fNode.fileName.equals(fileName) == false)
			return false;
		if (fNode.fileLen != fileLen)
			return false;

		return true;
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

		// Determine the source URL to copy the contents from
		srcUrl = IoUtil.createURL(rootUrl.toString() + "/" + fileName);
		
		// Determine the file to transfer the contents to
		dstFile = new File(dstPath, fileName);

		return DistUtils.downloadFile(aTask, srcUrl, dstFile, aCredential);
	}
}
