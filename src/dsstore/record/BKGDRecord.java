package dsstore.record;

import java.nio.ByteBuffer;

import dsstore.AliasRecord;
import dsstore.BufUtils;

public class BKGDRecord extends BlobRecord
{
	private AliasRecord refAliasRecord;

	public BKGDRecord(String aName, String aId, String aType)
	{
		super(aName, aId, aType);

		refAliasRecord = null;
	}

	@Override
	public void writePayload(ByteBuffer aBuf)
	{
		// Write out the (unsupported) BKGD record if we do not have a refAliasRecord
		if (refAliasRecord == null)
		{
			super.writePayload(aBuf);
			return;
		}

		// Write out the BKGD.PctB record
		aBuf.putInt(12);

		BufUtils.writeRawAsciiStr(aBuf, "PctB", 4);
		aBuf.putInt(refAliasRecord.size());
		BufUtils.writeRawAsciiStr(aBuf, "\0\0b\0", 4);
	}

	public void setRefAliasRecord(AliasRecord aAliasRecord)
	{
		refAliasRecord = aAliasRecord;
	}

}
