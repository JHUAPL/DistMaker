package dsstore.record;

import java.nio.ByteBuffer;

public class BoolRecord extends Record
{
	byte payload;

	public BoolRecord(String aName, String aId, String aType)
	{
		super(aName, aId, aType);
	}

	@Override
	public int getSize()
	{
		return 1;
	}

	@Override
	public void readPayload(ByteBuffer aBuf)
	{
		payload = aBuf.get();
	}

	@Override
	public void writePayload(ByteBuffer aBuf)
	{
		aBuf.put(payload);
	}

}
