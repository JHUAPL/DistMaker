package distMaker.node;

import glum.net.Credential;
import glum.task.Task;

import java.io.File;
import java.net.URL;

public class PathNode implements Node
{
	protected URL rootUrl;
	protected String fileName;

	public PathNode(URL aRootUrl, String aFileName)
	{
		rootUrl = aRootUrl;
		fileName = aFileName;
	}

	@Override
	public boolean areContentsEqual(Node aNode)
	{
		PathNode pNode;

		if (aNode instanceof PathNode == false)
			return false;

		pNode = (PathNode)aNode;
		if (pNode.fileName.equals(fileName) == false)
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
		File dstDir;

		// Determine the dest folder to create
		dstDir = new File(dstPath, fileName);

		// Form the directory
		return dstDir.mkdirs();
	}

}
