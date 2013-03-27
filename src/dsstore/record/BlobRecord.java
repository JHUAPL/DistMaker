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
