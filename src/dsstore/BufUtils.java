package dsstore;

import java.nio.ByteBuffer;

import com.google.common.base.Charsets;

public class BufUtils
{

	/**
	 * Method to advance the buffers current position by aLen. A negative value will rewind the buffer.
	 */
	public static void seek(ByteBuffer aBuf, int aLen)
	{
		aBuf.position(aBuf.position() + aLen);
	}

	/**
	 * Method to read a US-ASCII string from the buffer.
	 */
	public static String readRawAsciiStr(ByteBuffer aBuf, int strLen)
	{
		byte[] byteArr;

		byteArr = new byte[strLen];
		aBuf.get(byteArr);

		return new String(byteArr, Charsets.US_ASCII);
	}

	/**
	 * Method to write out a US-ASCII string to the buffer. if strLen is greater than the lenth of aStr, then the buffer
	 * will be padded with the difference in zero bytes
	 */
	public static void writeRawAsciiStr(ByteBuffer aBuf, String aStr, int strLen)
	{
		byte[] byteArr;
		int padBytes;

		byteArr = aStr.getBytes(Charsets.US_ASCII);
		if (byteArr.length > strLen)
			throw new RuntimeException("Input string is too big. Input: " + aStr + " Max:" + strLen);

		// Output the string contents
		aBuf.put(byteArr);

		// Pad with empty bytes
		padBytes = strLen - byteArr.length;
		for (int c1 = 0; c1 < padBytes; c1++)
			aBuf.put((byte)0);
	}

	public static String readStringUtf16(ByteBuffer aBuf)
	{
		byte[] byteArr;
		int strLen;

		strLen = aBuf.getShort() & 0xFFFF;

		byteArr = new byte[strLen * 2];
		aBuf.get(byteArr);
		return new String(byteArr, Charsets.UTF_16BE);
	}

	public static void writeStringUtf16(ByteBuffer aBuf, String aStr)
	{
		byte[] byteArr;
		int strLen;

		strLen = aStr.length();
		aBuf.putShort((short)(0xFFFF & strLen));

		byteArr = aStr.getBytes(Charsets.UTF_16BE);
		aBuf.put(byteArr);
	}

}
