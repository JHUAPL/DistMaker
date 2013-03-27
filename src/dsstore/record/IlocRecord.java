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
