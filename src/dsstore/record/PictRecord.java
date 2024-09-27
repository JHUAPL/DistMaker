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

import dsstore.AliasRecord;
import dsstore.BufUtils;

public class PictRecord extends Record
{
	AliasRecord aliasRecord;

	public PictRecord(String aName, String aId, String aType)
	{
		super(aName, aId, aType);

		aliasRecord = new AliasRecord();
	}
	
	public AliasRecord getAliasRecord()
	{
		return aliasRecord;
	}

	@Override
	public int getSize()
	{
		return 4 + aliasRecord.size();
	}

	@Override
	public void readPayload(ByteBuffer aBuf)
	{
		int begPos, endPos;
		int bytesRead, bytesSkip;
		int blobSize;

		blobSize = 0xFFFF & aBuf.getInt();

		begPos = aBuf.position();
		aliasRecord.readData(aBuf);
		endPos = aBuf.position();

		// Skip any bytes if we fail to fully read the structure.
		bytesRead = endPos - begPos;
		bytesSkip = blobSize - bytesRead;
		if (bytesSkip != 0)
			System.out.println("Failed to read PictRecord completely. Skipping bytes" + bytesSkip);
		BufUtils.seek(aBuf, bytesSkip);
	}

	@Override
	public void writePayload(ByteBuffer aBuf)
	{
		int blobSize;

		blobSize = aliasRecord.size();

		aBuf.putInt(0xFFFF & blobSize);
		aliasRecord.writeData(aBuf);
	}

}
