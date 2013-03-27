package dsstore.ext;

import java.nio.ByteBuffer;

public abstract class ExtInfo
{
	int type;
	int size;
	
	public ExtInfo(int aType, int aSize)
	{
		type = aType;
		size = aSize;
	}
	
	public int getSize()
	{
		if (size%2 == 0)
			return size;
		
		return size + 1;
	}
	
	public void writeHeader(ByteBuffer aBuf)
	{
		aBuf.putShort((short)(type & 0xFFFF));
		aBuf.putShort((short)(size & 0xFFFF));
	}
	
	public abstract void readPayload(ByteBuffer aBuf);
	
	public abstract void writePayload(ByteBuffer aBuf);
	
}
