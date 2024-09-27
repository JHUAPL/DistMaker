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
package dsstore;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import dsstore.ext.*;

public class AliasRecord
{
	int appCreaterId;
	int recSize, recVer, recType;

	// Volume vars
	String volName, volSig;
	int volType;
	long volTime;
	int unknown1;

	// File vars
	int fileNum;
	String fileName, fileType, fileOrig;
	long fileTime;

	// Misc vars
	int nlvlSrc, nlvlDst;
	int volAttr, volSysId, fileSysId;

	// Extended info
	Map<Integer, ExtInfo> extMap;

	// The format of the serialized AliasRecord is not fully understood!
	// Source:
	// http://en.wikipedia.org/wiki/Alias_(Mac_OS)
	// http://xhelmboyx.tripod.com/formats/alias-layout.txt
	// Format:
	// 4 bytes user type name/app creator code = long ASCII text string (none = 0)
	// 2 bytes record size = short unsigned total length
	// 2 bytes record version = short integer version (current version = 2)
	// 2 bytes alias kind = short integer value (file = 0; directory = 1)
	// 1 byte volume name string length = byte unsigned length
	// 27 bytes volume name string (if volume name string < 27 chars then pad with zeros)
	// 4 bytes volume created mac date = long unsigned value in seconds since beginning 1904 to 2040
	// 2 bytes volume signature = short unsigned HFS value
	// 2 bytes volume type = short integer mac os value (types are Fixed HD = 0; Network Disk = 1; 400kB FD = 2;800kB FD
	// = 3; 1.4MB FD = 4; Other Ejectable Media = 5 )
	// 4 bytes parent directory id = short unsigned HFS value
	// 1 bytes file name string length = byte unsigned length
	// 63 bytes file name string (if file name string < 63 chars then pad with zeros)
	// 4 bytes file number = long unsigned HFS value
	// 4 bytes file created mac date = long unsigned value in seconds since beginning 1904 to 2040
	// 4 bytes file type name = long ASCII text string
	// 4 bytes file creator name = long ASCII text string
	// 2 bytes nlvl From (directories from alias thru to root) = short integer range
	// 2 bytes nlvl To (directories from root thru to source) = short integer range (if alias on different volume then
	// set above to -1)
	// 4 bytes volume attributes = long hex flags
	// 2 bytes volume file system id = short integer HFS value
	// 10 bytes reserved = 80-bit value set to zero
	// 4+ bytes optional extra data strings = short integer type + short unsigned string length (types are Extended Info
	// End = -1; Directory Name = 0; Directory IDs = 1; Absolute Path = 2; AppleShare Zone Name = 3; AppleShare Server
	// Name = 4; AppleShare User Name = 5; Driver Name = 6; Revised AppleShare info = 9; AppleRemoteAccess dialup info =
	// 10)
	// string data = hex dump
	// odd lengths have a 1 byte odd string length pad = byte value set to zero

	public AliasRecord()
	{
		extMap = new LinkedHashMap<>();
	}

	/**
	 * Reads in the contents of the object from the buffer
	 */
	public void readData(ByteBuffer aBuf)
	{
		int volLen, fileLen;

		appCreaterId = aBuf.getInt();
		recSize = aBuf.getShort() & 0x0FFFF;
		recVer = aBuf.getShort() & 0x0FFFF;
		recType = aBuf.getShort() & 0x0FFFF;

		// Volume section
		volLen = aBuf.get() & 0xFF;
		volName = BufUtils.readRawAsciiStr(aBuf, volLen);
		BufUtils.seek(aBuf, 27 - volLen);
		volTime = aBuf.getInt();
		volSig = BufUtils.readRawAsciiStr(aBuf, 2);
		volType = aBuf.getShort() & 0x0FFFF;
		unknown1 = aBuf.getInt();

		// File section
		fileLen = aBuf.get() & 0xFF;
		fileName = BufUtils.readRawAsciiStr(aBuf, fileLen);
		BufUtils.seek(aBuf, 63 - fileLen);
		fileNum = aBuf.getInt();
		fileTime = aBuf.getInt();
		fileType = BufUtils.readRawAsciiStr(aBuf, 4);
		fileOrig = BufUtils.readRawAsciiStr(aBuf, 4);

		// Misc section
		nlvlSrc = aBuf.getShort();
		nlvlDst = aBuf.getShort();
		volAttr = aBuf.getInt();
		volSysId = aBuf.getShort();
		BufUtils.seek(aBuf, 10);

//		System.out.println("Alias Info: ");
//		System.out.println("  RecInfo: size:" + recSize + ", ver:" + recVer + ", type:" + recType);
//		System.out.println("  VolInfo: name:" + volName + ", sig:" + volSig + ", type:" + volType);
//		System.out.println("  FileInfo: name:" + fileName + ", fileNum:" + fileNum + ", type:" + fileType + ", fileOrig:" + fileOrig);

		// Extra info
		int exSize, exType;
		ExtInfo extInfo;

		while (true)
		{
			exType = aBuf.getShort();
			exSize = aBuf.getShort();

			if (exType == 1 || exType == 16 || exType == 17 || exType == 20)
				extInfo = new RawExtInfo(exType, exSize);
			else
				extInfo = new StrExtInfo(exType, exSize);

			extInfo.readPayload(aBuf);
//			System.out.println("\t" + extInfo);

			// Bail once we get the terminator exType
			if (exType == -1)
				break;

			// Record the ExtInfo
			extMap.put(exType, extInfo);
		}

//		System.out.println("Finished reading exInfo");
	}

	/**
	 * Writes out the contents of the object to the buffer
	 */
	public void writeData(ByteBuffer aBuf)
	{
		aBuf.putInt(appCreaterId);
		aBuf.putShort((short)(recSize & 0xFFFF));
		aBuf.putShort((byte)(recVer & 0xFFFF));
		aBuf.putShort((byte)(recType & 0xFFFF));

		// Volume section
		aBuf.put((byte)(0xFF & volName.length()));
		BufUtils.writeRawAsciiStr(aBuf, volName, 27);
		aBuf.putInt((int)(0xFFFFFFFF & volTime));
		BufUtils.writeRawAsciiStr(aBuf, volSig, 2);
		aBuf.putShort((byte)(volType & 0xFFFF));
		aBuf.putInt(unknown1);

		// File section
		aBuf.put((byte)(0xFF & fileName.length()));
		BufUtils.writeRawAsciiStr(aBuf, fileName, 63);
		aBuf.putInt(fileNum);
		aBuf.putInt((int)(0xFFFFFFFF & fileTime));
		BufUtils.writeRawAsciiStr(aBuf, fileType, 4);
		BufUtils.writeRawAsciiStr(aBuf, fileOrig, 4);

		// Misc section
		aBuf.putShort((short)(nlvlSrc & 0xFFFF));
		aBuf.putShort((short)(nlvlDst & 0xFFFF));
		aBuf.putInt(volAttr);
		aBuf.putShort((short)(volSysId & 0xFFFF));
		for (int c1 = 0; c1 < 10; c1++)
			aBuf.put((byte)0);

		// Extra info
		for (ExtInfo aExtInfo : extMap.values())
		{
			aExtInfo.writeHeader(aBuf);
			aExtInfo.writePayload(aBuf);
		}

		// Extra info terminator
		aBuf.putShort((short)-1);
		aBuf.putShort((short)0);
	}

	/**
	 * Changes the volume name associated with this AliasRecord
	 */
	public void setVolumeName(String aVolName)
	{
		String tmpStr;

		volName = aVolName;

		tmpStr = aVolName + ":.background:background.png";
		extMap.put(2, new StrExtInfo(2, tmpStr));

		tmpStr = aVolName;
		extMap.put(15, new StrExtInfo(15, tmpStr));

		tmpStr = "/Volumes/" + aVolName;
		extMap.put(19, new StrExtInfo(19, tmpStr));

// TODO:		
		// Remove the (unknown) ExtInfo type == 20
//		extMap.remove(20);
//System.out.println("Will not remove unknown type: 20\n\t" + extMap.get(20));

		// Update the internal copy of the record size
		recSize = size();
	}

	/**
	 * Returns the size of this AliasRecord
	 */
	public int size()
	{
		int retSize;

		// Fixed size sections: Header, Volume, File, Misc
		retSize = 10;
		retSize += 40;
		retSize += 80;
		retSize += 20;

		// Extra Info section
		for (ExtInfo aExtInfo : extMap.values())
			retSize += 4 + aExtInfo.getSize();

		// Ext Terminator
		retSize += 4;

		return retSize;
	}

}
