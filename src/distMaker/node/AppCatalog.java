package distMaker.node;

import java.util.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import distMaker.jre.JreVersion;

/**
 * Object that describes the structure (files, folders, and JRE version) of a Java application.
 */
public class AppCatalog
{
	/** The minimum JRE version required. */
	private JreVersion jreVersion;

	/** A mapping of filename to to corresponding Node */
	private ImmutableMap<String, Node> nodeMap;

	public AppCatalog(JreVersion aJreVersion, List<Node> aNodeList)
	{
		jreVersion = aJreVersion;
		nodeMap = ImmutableMap.copyOf(formNameMap(aNodeList));
	}

	/**
	 * Returns the minimum JreVersion required.
	 */
	public JreVersion getJreVersion()
	{
		return jreVersion;
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
	public Map<String, Node> formNameMap(List<Node> aNodeList)
	{
		Map<String, Node> retMap;

		retMap = new LinkedHashMap<>();
		for (Node aNode : aNodeList)
			retMap.put(aNode.getFileName(), aNode);

		return retMap;
	}

}
