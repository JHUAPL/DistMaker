package distMaker.node;

import java.util.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import distMaker.jre.*;

/**
 * Object that describes the structure (files, folders, and JRE version) of a Java application.
 */
public class AppCatalog
{
	/** The minimum JRE version required. */
	private JreVersion minJreVer;

	/** The maximum JRE version allowed. This will be null if there is no maximum. */
	private JreVersion maxJreVer;

	/** A mapping of filename to to corresponding Node */
	private ImmutableMap<String, Node> nodeMap;

	public AppCatalog(List<Node> aNodeList, JreVersion aMinJreVer, JreVersion aMaxJreVer)
	{
		minJreVer = aMinJreVer;
		maxJreVer = aMaxJreVer;
		nodeMap = ImmutableMap.copyOf(formNameMap(aNodeList));
	}

	/**
	 * Returns the most recent JRE from the specified release that is compatible with this Appcatalog.
	 * <P>
	 * Returns null if there are no JREs that are compatible.
	 */
	public JreRelease getCompatibleJre(List<JreRelease> aJreList)
	{
		// Sort the platforms, but reverse the order so that the newest version is first
		Collections.sort(aJreList);
		Collections.reverse(aJreList);

		for (JreRelease aRelease : aJreList)
		{
			if (isJreVersionTooNew(aRelease.getVersion()) == true)
				continue;

			if (isJreVersionTooOld(aRelease.getVersion()) == true)
				continue;

			return aRelease;
		}

		return null;
	}

	/**
	 * Returns the minimum JreVersion that is compatible.
	 */
	public JreVersion getMinJreVersion()
	{
		return minJreVer;
	}

	/**
	 * Returns the maximum JreVersion that is compatible.
	 */
	public JreVersion getMaxJreVersion()
	{
		return maxJreVer;
	}

	public boolean isJreVersionCompatible(JreVersion aJreVer)
	{
		// Check to make sure aJreVer is not too old
		if (isJreVersionTooOld(aJreVer) == true)
			return false;

		// Check to make sure aJreVer is not too new
		if (isJreVersionTooNew(aJreVer) == true)
			return false;

		// The version aJreVer must be compatible
		return true;
	}

	/**
	 * Returns true if the specified version is not compatible (too new) with this AppCatalog.
	 */
	public boolean isJreVersionTooNew(JreVersion aJreVer)
	{
		// Check to make sure aJreVer is not too old
		if (maxJreVer != null && JreVersion.getBetterVersion(maxJreVer, aJreVer) == aJreVer)
			return true;

		return false;
	}

	/**
	 * Returns true if the specified version is not compatible (too old) with this AppCatalog.
	 */
	public boolean isJreVersionTooOld(JreVersion aJreVer)
	{
		// Check to make sure aJreVer is not too old
		if (minJreVer != null && JreVersion.getBetterVersion(minJreVer, aJreVer) == minJreVer)
			return true;

		return false;
	}

	/**
	 * Returns the Node corresponding to the specified name.
	 */
	public Node getNode(String aName)
	{
		return nodeMap.get(aName);
	}

	/**
	 * Returns the full list of Nodes
	 */
	public ImmutableList<Node> getAllNodesList()
	{
		return nodeMap.values().asList();
	}

	/**
	 * Helper method to form the map used to quickly locate a Node with the corresponding filename.
	 * <P>
	 * TODO: This should be renamed formNameMap to formDigestMap<BR>
	 * TODO: This should probably be a mapping of Digest to Node rather than filename to Node
	 */
	private Map<String, Node> formNameMap(List<Node> aNodeList)
	{
		Map<String, Node> retMap;

		retMap = new LinkedHashMap<>();
		for (Node aNode : aNodeList)
			retMap.put(aNode.getFileName(), aNode);

		return retMap;
	}

}
