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
package dsstore;

import java.nio.ByteBuffer;

import com.google.common.base.Charsets;

public class BlockDir
{
	private String name;
	private int index;

	public BlockDir()
	{
		name = null;
		index = -1;
	}

	public BlockDir(ByteBuffer srcBuf)
	{
		this();
		readData(srcBuf);
	}

	public int getBlockIndex()
	{
		return index;
	}

	public void readData(ByteBuffer srcBuf)
	{
		int numBytes;
		byte[] byteArr;

		numBytes = srcBuf.get() & 0xFF;

		// Directories name
		byteArr = new byte[numBytes];
		srcBuf.get(byteArr);
		name = new String(byteArr, Charsets.US_ASCII);

		// Block's address
		index = srcBuf.getInt();

		// Debug output
//		System.out.println("[BlockDir] Name: " + name + " index: " + index);
	}

}
