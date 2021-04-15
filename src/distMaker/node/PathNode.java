package distMaker.node;

import java.io.File;
import java.net.URL;

import glum.net.Credential;
import glum.task.Task;

/**
 * Immutable {@link Node} describing a folder / directory.
 *
 * @author lopeznr1
 */
public class PathNode implements Node
{
//	private final URL rootUrl;
	private final String fileName;

	/** Standard Constructor */
	public PathNode(URL aRootUrl, String aFileName)
	{
//		rootUrl = aRootUrl;
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
