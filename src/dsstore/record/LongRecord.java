package dsstore.record;

import java.nio.ByteBuffer;

public class LongRecord extends Record
{
	int payload;

	public LongRecord(String aName, String aId, String aType)
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
		payload = aBuf.getInt();
	}

	@Override
	public void writePayload(ByteBuffer aBuf)
	{
		aBuf.putInt(payload);
	}

}
