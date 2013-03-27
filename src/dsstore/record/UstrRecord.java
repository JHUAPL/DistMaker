package dsstore.record;

import java.nio.ByteBuffer;

import dsstore.BufUtils;

public class UstrRecord extends Record
{
	String payload;

	public UstrRecord(String aName, String aId, String aType)
	{
		super(aName, aId, aType);

		payload = null;
	}

	@Override
	public int getSize()
	{
		return 2 + (payload.length() * 2);
	}

	@Override
	public void readPayload(ByteBuffer aBuf)
	{
		payload = BufUtils.readStringUtf16(aBuf);
	}

	@Override
	public void writePayload(ByteBuffer aBuf)
	{
		BufUtils.writeStringUtf16(aBuf, payload);
	}

}
