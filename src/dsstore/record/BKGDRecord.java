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

public class BKGDRecord extends BlobRecord
{
	private AliasRecord refAliasRecord;

	public BKGDRecord(String aName, String aId, String aType)
	{
		super(aName, aId, aType);

		refAliasRecord = null;
	}

	@Override
	public void writePayload(ByteBuffer aBuf)
	{
		// Write out the (unsupported) BKGD record if we do not have a refAliasRecord
		if (refAliasRecord == null)
		{
			super.writePayload(aBuf);
			return;
		}

		// Write out the BKGD.PctB record
		aBuf.putInt(12);

		BufUtils.writeRawAsciiStr(aBuf, "PctB", 4);
		aBuf.putInt(refAliasRecord.size());
		BufUtils.writeRawAsciiStr(aBuf, "\0\0b\0", 4);
	}

	public void setRefAliasRecord(AliasRecord aAliasRecord)
	{
		refAliasRecord = aAliasRecord;
	}

}
