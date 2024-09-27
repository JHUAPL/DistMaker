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

import dsstore.BufUtils;

public class StrExtInfo extends ExtInfo
{
	private String data;

	public StrExtInfo(int aType, int aSize)
	{
		super(aType, aSize);

		data = "";
	}

	public StrExtInfo(int aType, String aData)
	{
		super(aType, 0);

		data = aData;
		if (isRawAscii() == true)
		{
			size = data.length();
		}
		else
		{
			size = data.length() * 2;
			size += 2;
		}
	}

	@Override
	public void readPayload(ByteBuffer aBuf)
	{
		if (isRawAscii() == true)
		{
			data = BufUtils.readRawAsciiStr(aBuf, size);

			// Odd length have a 1 byte padding
			if (size % 2 != 0)
				aBuf.get();
		}
		else
		{
			data = BufUtils.readStringUtf16(aBuf);
		}
	}

	@Override
	public void writePayload(ByteBuffer aBuf)
	{
		int numBytes;

		// Determine the length of the string
		numBytes = data.length();
		if (isRawAscii() == true)
		{
			if (numBytes % 2 == 1)
				numBytes++;
		}

		if (isRawAscii() == true)
			BufUtils.writeRawAsciiStr(aBuf, data, numBytes);
		else
			BufUtils.writeStringUtf16(aBuf, data);
	}

	@Override
	public String toString()
	{
		String aStr;

		aStr = "StrExtInfo type:" + type + " size:" + size;
		aStr += " contents: " + data;
		return aStr;
	}

	/**
	 * Helper method to determine if the payload string contents are stored as a Raw US-ASCII string or a UTF-16
	 */
	public boolean isRawAscii()
	{
		// The type below are stored as UTF-16
		if (type == 14 || type == 15)
			return false;

		return true;
	}

}
