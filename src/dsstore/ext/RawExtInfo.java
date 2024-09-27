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
package dsstore.ext;

import java.nio.ByteBuffer;

public class RawExtInfo extends ExtInfo
{
	private byte[] data;
	private int readPos;

	public RawExtInfo(int aType, int aSize)
	{
		super(aType, aSize);
		
		data = new byte[size];
		readPos = 0;
	}
	
	@Override
	public void readPayload(ByteBuffer aBuf)
	{
		readPos = aBuf.position();
		aBuf.get(data);
		
		// Odd length have a 1 byte padding
		if (size % 2 != 0)
			aBuf.get();
	}
	
	@Override
	public void writePayload(ByteBuffer aBuf)
	{
		aBuf.put(data);
		
		// Odd length have a 1 byte padding
		if (size % 2 != 0)
			aBuf.put((byte)0);
	}
	
	@Override
	public String toString()
	{
		String aStr;
		
		aStr = "RawExtInfo type:" + type + " size:" + size;
		aStr += " contents: binary data readPos:" + readPos;
		return aStr;
	}

}
