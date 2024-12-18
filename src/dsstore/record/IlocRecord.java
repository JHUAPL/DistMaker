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

import dsstore.BufUtils;

public class IlocRecord extends Record
{
	int posX, posY;

	public IlocRecord(String aName, String aId, String aType)
	{
		super(aName, aId, aType);
	}

	@Override
	public int getSize()
	{
		return 20;
	}

	@Override
	public void readPayload(ByteBuffer aBuf)
	{
		int size;
		
		size = 0xFFFF & aBuf.getInt();
		if (size != 16)
			throw new RuntimeException("Iloc blob unreconized. Size: " + size);

		posX = aBuf.getInt();
		posY = aBuf.getInt();
		BufUtils.seek(aBuf, 8);
	}

	@Override
	public void writePayload(ByteBuffer aBuf)
	{
		aBuf.putInt(16);
		
		aBuf.putInt(posX);
		aBuf.putInt(posY);

		// Unknown values
		aBuf.putInt(0xFFFFFFFF);
		aBuf.putInt(0xFFFF0000);
	}

}
