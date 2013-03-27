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
