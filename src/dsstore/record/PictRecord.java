package dsstore.record;

import java.nio.ByteBuffer;

import dsstore.AliasRecord;
import dsstore.BufUtils;

public class PictRecord extends Record
{
	AliasRecord aliasRecord;

	public PictRecord(String aName, String aId, String aType)
	{
		super(aName, aId, aType);

		aliasRecord = new AliasRecord();
	}
	
	public AliasRecord getAliasRecord()
	{
		return aliasRecord;
	}

	@Override
	public int getSize()
	{
		return 4 + aliasRecord.size();
	}

	@Override
	public void readPayload(ByteBuffer aBuf)
	{
		int begPos, endPos;
		int bytesRead, bytesSkip;
		int blobSize;

		blobSize = 0xFFFF & aBuf.getInt();

		begPos = aBuf.position();
		aliasRecord.readData(aBuf);
		endPos = aBuf.position();

		// Skip any bytes if we fail to fully read the structure.
		bytesRead = endPos - begPos;
		bytesSkip = blobSize - bytesRead;
		if (bytesSkip != 0)
			System.out.println("Failed to read PictRecord completely. Skipping bytes" + bytesSkip);
		BufUtils.seek(aBuf, bytesSkip);
	}

	@Override
	public void writePayload(ByteBuffer aBuf)
	{
		int blobSize;

		blobSize = aliasRecord.size();

		aBuf.putInt(0xFFFF & blobSize);
		aliasRecord.writeData(aBuf);
	}

}
