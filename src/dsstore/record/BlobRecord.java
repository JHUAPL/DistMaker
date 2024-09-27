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
package dsstore.record;

import java.nio.ByteBuffer;

public class BlobRecord extends Record
{
	protected byte[] data;

	public BlobRecord(String aName, String aId, String aType)
	{
		super(aName, aId, aType);
	}

	@Override
	public int getSize()
	{
		return 4 + data.length;
	}

	@Override
	public void readPayload(ByteBuffer aBuf)
	{
		int size;
		
		size = 0xFFFF & aBuf.getInt();
		data = new byte[size];
		
		aBuf.get(data);
	}

	@Override
	public void writePayload(ByteBuffer aBuf)
	{
		int size;
		
		size = data.length;
		aBuf.putInt(0xFFFF & size);
		aBuf.put(data);
	}

}
