package distMaker.digest;

public enum DigestType
{
	// Weak digest - but very fast
	MD5("MD5"),

	// Fairly strong digest type (with good performance on 32 bit machines)
	SHA256("SHA-256"),

	// Very strong digest type
	SHA512("SHA-512");

	// State vars
	private String algName;

	private DigestType(String aAlgName)
	{
		algName = aAlgName;
	}

	/**
	 * Returns the official digest algorithm name.
	 * 
	 * @see http://docs.oracle.com/javase/1.5.0/docs/guide/security/CryptoSpec.html#AppA
	 */
	public String getAlgName()
	{
		return algName;
	}

	/**
	 * Returns the corresponding DigestType.
	 */
	public static DigestType parse(String aStr)
	{
		if (aStr.equalsIgnoreCase("MD5") == true)
			return MD5;
		if (aStr.equalsIgnoreCase("SHA256") == true)
			return SHA256;
		if (aStr.equalsIgnoreCase("SHA512") == true)
			return SHA512;

		return null;
	}
}