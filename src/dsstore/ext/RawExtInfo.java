package dsstore.ext;

import java.nio.ByteBuffer;

public class RawExtInfo extends ExtInfo
{
	private byte[] data;
	private int readPos;

	public RawExtInfo(int aType, int aSize)
	{
		super(aType, aSize);
		
		data = new byte[size];
		readPos = 0;
	}
	
	@Override
	public void readPayload(ByteBuffer aBuf)
	{
		readPos = aBuf.position();
		aBuf.get(data);
		
		// Odd length have a 1 byte padding
		if (size % 2 != 0)
			aBuf.get();
	}
	
	@Override
	public void writePayload(ByteBuffer aBuf)
	{
		aBuf.put(data);
		
		// Odd length have a 1 byte padding
		if (size % 2 != 0)
			aBuf.put((byte)0);
	}
	
	@Override
	public String toString()
	{
		String aStr;
		
		aStr = "RawExtInfo type:" + type + " size:" + size;
		aStr += " contents: binary data readPos:" + readPos;
		return aStr;
	}

}
