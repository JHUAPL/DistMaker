package dsstore;

import java.nio.ByteBuffer;

import com.google.common.base.Charsets;

public class BlockDir
{
	private String name;
	private int index;

	
	public BlockDir()
	{
		name = null;
		index = -1;
	}

	public BlockDir(ByteBuffer srcBuf)
	{
		this();
		readData(srcBuf);
	}

	public int getBlockIndex()
	{
		return index;
	}
	
	public void readData(ByteBuffer srcBuf)
	{
		int numBytes;
		byte[] byteArr;
		
		numBytes = srcBuf.get() & 0xFF;
		
		// Directories name
		byteArr = new byte[numBytes];
		srcBuf.get(byteArr);
		name = new String(byteArr, Charsets.US_ASCII);
		
		// Block's address
		index = srcBuf.getInt();
		
		// Debug output
//		System.out.println("[BlockDir] Name: " + name + " index: " + index);
	}
	
	
	
}
