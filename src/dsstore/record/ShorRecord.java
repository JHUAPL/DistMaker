package dsstore.record;

import java.nio.ByteBuffer;

public class ShorRecord extends Record
{
	int payload;

	public ShorRecord(String aName, String aId, String aType)
	{
		super(aName, aId, aType);
	}

	@Override
	public int getSize()
	{
		return 4;
	}

	@Override
	public void readPayload(ByteBuffer aBuf)
	{
		payload = 0x0000FFFF & aBuf.getInt();
	}

	@Override
	public void writePayload(ByteBuffer aBuf)
	{
		aBuf.putInt(0x0000FFFF & payload);
	}

}
