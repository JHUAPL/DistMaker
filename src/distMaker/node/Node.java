package distMaker.node;

import java.io.File;

import glum.net.Credential;
import glum.task.Task;

/**
 * Interface which provides an abstraction of specific data resource.
 *
 * @author lopeznr1
 */
public interface Node
{
	/**
	 * Returns true, if the contents stored in aNode are equal to this Node.
	 */
	public boolean areContentsEqual(Node aNode);

	/**
	 * Returns the "file name" associated with this Node
	 */
	public String getFileName();

	/**
	 * Method to copy the contents of this node to destPath. The var, destPath, should be a folder.
	 */
	boolean transferContentTo(Task aTask, Credential aCredential, File destPath);

}
