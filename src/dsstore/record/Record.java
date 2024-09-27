// Copyright (C) 2024 The Johns Hopkins University Applied Physics Laboratory LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
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
