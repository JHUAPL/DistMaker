package distMaker.digest;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.xml.bind.annotation.adapters.HexBinaryAdapter;

/**
 * Collection of utility methods to ease working with the MessageDigest and associated classes.
 */
public class DigestUtils
{
	/**
	 * Utility method that will throw a RuntimeExcepption if the specified digest function is not found.
	 * <P>
	 * Algorithm should be MD5, SHA-256, SHA-512, ...
	 * <P>
	 * See: http://docs.oracle.com/javase/1.8.0/docs/guide/security/CryptoSpec.html#AppA
	 */
	public static MessageDigest getDigest(String aAlgorithm)
	{
		MessageDigest retDigest;

		try
		{
			retDigest = MessageDigest.getInstance(aAlgorithm);
		}
		catch(NoSuchAlgorithmException aExp)
		{
			throw new RuntimeException("Digest not found. Digest algorith not found: " + aAlgorithm);
		}

		return retDigest;
	}

	/**
	 * Utility method that will throw a RuntimeExcepption if the specified digest function is not found.
	 * <P>
	 * See: http://docs.oracle.com/javase/1.8.0/docs/guide/security/CryptoSpec.html#AppA
	 */
	public static MessageDigest getDigest(DigestType aDigestType)
	{
		return getDigest(aDigestType.getAlgName());
	}

	/**
	 * Utility method that returns the hex string corresponding to the byte array.
	 */
	public static String byteArr2HexStr(byte[] aByteArr)
	{
		String retStr;

		retStr = (new HexBinaryAdapter()).marshal(aByteArr).toLowerCase();
		return retStr;
	}

	/**
	 * Utility method that returns a byte array corresponding to the hex string.
	 */
	public static byte[] hexStr2ByteArr(String aHexStr)
	{
		byte[] retArr;

		retArr = (new HexBinaryAdapter()).unmarshal(aHexStr);
		return retArr;
	}

}
