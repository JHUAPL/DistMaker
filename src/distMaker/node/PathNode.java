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
