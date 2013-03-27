package dsstore.record;

import java.nio.ByteBuffer;

import dsstore.BufUtils;

public abstract class Record implements Comparable<Record>
{
	String name;
	String id;
	String type;

	public Record(String aName, String aId, String aType)
	{
		name = aName;
		id = aId;
		type = aType;
	}

	// Accessor methods
	// @formatter:off
	public Object getId() { return id; }
	public String getName() { return name; }
	public void setName(String aName) { name = aName; }
	// @formatter:on

	public void writeHeader(ByteBuffer aBuf)
	{
		BufUtils.writeStringUtf16(aBuf, name);
		BufUtils.writeRawAsciiStr(aBuf, id, 4);
		BufUtils.writeRawAsciiStr(aBuf, type, 4);
	}

	public abstract void readPayload(ByteBuffer aBuf);

	public abstract void writePayload(ByteBuffer aBuf);

	public abstract int getSize();

	@Override
	public int compareTo(Record o)
	{
		return name.compareTo(o.name);
	}

}
