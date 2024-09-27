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
package distMaker.node;

import java.util.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import distMaker.jre.JreRelease;
import distMaker.jre.JreVersion;

/**
 * Object that describes the structure (files, folders, and JRE version) of a Java application.
 *
 * @author lopeznr1
 */
public class AppCatalog
{
	/** The minimum JRE version required. */
	private JreVersion minJreVer;

	/** The maximum JRE version allowed. This will be null if there is no maximum. */
	private JreVersion maxJreVer;

	/** A mapping of filename to to corresponding Node */
	private ImmutableMap<String, Node> nodeM;

	public AppCatalog(List<Node> aNodeL, JreVersion aMinJreVer, JreVersion aMaxJreVer)
	{
		minJreVer = aMinJreVer;
		maxJreVer = aMaxJreVer;
		nodeM = ImmutableMap.copyOf(formNameMap(aNodeL));
	}

	/**
	 * Returns the most recent {@link JreRelease} from the specified list that is compatible with this
	 * {@link AppCatalog}.
	 * <p>
	 * Returns null if there are no {@link JreRelease} that is compatible.
	 */
	public JreRelease getCompatibleJre(List<JreRelease> aJreL)
	{
		// Sort the platforms, but reverse the order so that the newest version is first
		Collections.sort(aJreL);
		Collections.reverse(aJreL);

		for (JreRelease aRelease : aJreL)
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
		// Check to make sure aJreVer is not too new
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
		return nodeM.get(aName);
	}

	/**
	 * Returns the full list of Nodes
	 */
	public ImmutableList<Node> getAllNodesList()
	{
		return nodeM.values().asList();
	}

	/**
	 * Helper method to form the map used to quickly locate a Node with the corresponding filename.
	 * <p>
	 * TODO: This should be renamed formNameMap to formDigestMap<br>
	 * TODO: This should probably be a mapping of Digest to Node rather than filename to Node
	 */
	private Map<String, Node> formNameMap(List<Node> aNodeL)
	{
		Map<String, Node> retM;

		retM = new LinkedHashMap<>();
		for (Node aNode : aNodeL)
			retM.put(aNode.getFileName(), aNode);

		return retM;
	}

}
