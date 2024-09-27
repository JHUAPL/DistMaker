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

import glum.digest.Digest;
import glum.io.IoUtil;
import glum.net.Credential;
import glum.net.NetUtil;
import glum.task.Task;

/**
 * Immutable {@link Node} describing a File.
 *
 * @author lopeznr1
 */
public class FileNode implements Node
{
	private final URL rootUrl;
	private final Digest digest;
	private final String fileName;
	private final long fileLen;

	/** Standard Constructor */
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
