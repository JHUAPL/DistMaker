package distMaker.digest;

import java.util.Arrays;

public class Digest
{
	private final DigestType digestType;
	private final byte[] digestValueArr;

	public Digest(DigestType aDigestType, byte[] aDigestValueArr)
	{
		digestType = aDigestType;
		digestValueArr = Arrays.copyOf(aDigestValueArr, aDigestValueArr.length);
	}

	public Digest(DigestType aDigestType, String aHexStr)
	{
		digestType = aDigestType;
		digestValueArr = DigestUtils.hexStr2ByteArr(aHexStr);
	}

	/**
	 * Returns a user friendly description (string) of this digest result.
	 * <P>
	 * The result will be DigestType:hexDigestValue
	 */
	public String getDescr()
	{
		return "" + digestType + ":" + getValueAsString();
	}

	/**
	 * Returns the DigestType associated with this Digest.
	 */
	public DigestType getType()
	{
		return digestType;
	}

	/**
	 * Returns the actual digest (as a string) associated with this Digest.
	 */
	public byte[] getValue()
	{
		return Arrays.copyOf(digestValueArr, digestValueArr.length);
	}

	/**
	 * Returns the actual digest (as a string) associated with this Digest.
	 */
	public String getValueAsString()
	{
		return DigestUtils.byteArr2HexStr(digestValueArr);
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((digestType == null) ? 0 : digestType.hashCode());
		result = prime * result + Arrays.hashCode(digestValueArr);
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Digest other = (Digest)obj;
		if (digestType != other.digestType)
			return false;
		if (!Arrays.equals(digestValueArr, other.digestValueArr))
			return false;
		return true;
	}

}
