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
