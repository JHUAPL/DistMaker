package dsstore;

import glum.task.*;
import glum.zio.stream.FileZinStream;
import glum.zio.stream.FileZoutStream;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

import dsstore.record.*;

/**
 * Most of the decoding of the .DS_store is described in the following source. 
 * Source:
 * http://search.cpan.org/~wiml/Mac-Finder-DSStore/DSStoreFormat.pod#FILE_FORMAT 
 */
public class MainApp
{
	// Constants
	public static final String DT_long = "long"; //convertStringToInt("long");  // An integer (4 bytes)
	public static final String DT_shor = "shor"; //convertStringToInt("shor");  // A short integer? Still stored as four bytes, but the first two are always zero.
	public static final String DT_bool = "bool"; //convertStringToInt("bool");  // A boolean value, stored as one byte.
	public static final String DT_blob = "blob"; //convertStringToInt("blob");  // An arbitrary block of bytes, stored as an integer followed by that many bytes of data.
	public static final String DT_type = "type"; //convertStringToInt("type");  // Four bytes, containing a FourCharCode.
	public static final String DT_ustr = "ustr"; //convertStringToInt("ustr");  // A Unicode text string, stored as an integer character count followed by 2*count bytes of data in UTF-16.

	// State vars
	private Task refTask;
	private ByteBuffer dataBuf;
	private String volumeName;

	public MainApp(String aVolumeName)
	{
		// Only output verbose info if we are not updating a store
		refTask = new ConsoleTask();
		if (aVolumeName != null)
			refTask = new SilentTask();

		dataBuf = null;
		volumeName = aVolumeName;
	}

	
	public void writeStore(File aFile)
	{
		// Ensure we have a valid dataBuf
		if (dataBuf == null)
			return;

		try (FileZoutStream aStream = new FileZoutStream(aFile);)
		{
			// Write the file's MagicKey
			int fileMagicKey = 0x0001;
			aStream.writeInt(fileMagicKey);

			// Dump the contents of aBytBuff
			aStream.writeFully(dataBuf.array());

			aStream.close();
		}
		catch(IOException aExp)
		{
			aExp.printStackTrace();
		}
	}

	/**
	 * Method to read the actual store contents.
	 * 
	 * @return True if we successfully read the store
	 */
	public boolean readStore(File aFile)
	{
		// Bail if the file is not valid
		if (aFile.isFile() == false)
		{
			System.err.println("File does note exist: " + aFile);
			return false;
		}
		
		dataBuf = null;
		try (FileZinStream iStream = new FileZinStream(aFile))
		{
			byte[] byteArr;
List<BlockDir> blockDirList;
String dmgMagicKey;
int fileMagicKey;
int allocBlockOffset1, allocBlockOffset2, allocBlockSize;			
int blockCnt, tmpSize, seekDiff, dirCnt;			
int blockAddrArr[];
			
			int fileSize = (int)aFile.length();
			
			// DS_Store magic key: 0x0001
			fileMagicKey = iStream.readInt();
			if (fileMagicKey != 0x0001)
				throw new IOException("Bad magic key value: " + fileMagicKey + " Expected: " + 0x0001);

			// Read the rest of the contents into a bytebuffer
			byteArr = new byte[fileSize - 4];
			iStream.readFully(byteArr);
			dataBuf = ByteBuffer.wrap(byteArr);

			// Header block (not stored in the allocators list)
			dmgMagicKey = BufUtils.readRawAsciiStr(dataBuf, 4);
//			aStream.readRawStringAndValidate("Bud1");
//			dmgMagicKey = byteBuf.getInt();
			allocBlockOffset1 = dataBuf.getInt();
			allocBlockSize = dataBuf.getInt();
			allocBlockOffset2 = dataBuf.getInt();
			
			// Check the header block validity
			if (dmgMagicKey.equals("Bud1") == false)
				throw new RuntimeException("Header magic key does not equal: 'Bud1'  Found: '" + dmgMagicKey + "'");
			
			if (allocBlockOffset1 != allocBlockOffset2)
				throw new RuntimeException("Allocator block offset mismatch: " + allocBlockOffset1 + " != " + allocBlockOffset2);
			
			
			
			// Advance to the allocator section
			dataBuf.position(allocBlockOffset1);
			
			blockCnt = dataBuf.getInt();
			dataBuf.getInt(); // Unknown
			
			blockAddrArr = new int[blockCnt];
			for (int c1 = 0; c1 < blockCnt; c1++)
			{
				blockAddrArr[c1] = dataBuf.getInt();
				
int blkLen, blkPos;			
blkLen = 1 << (blockAddrArr[c1] & 0x1F);		
blkPos = (blockAddrArr[c1] >> 5) * 32;
refTask.infoAppendln("BlockAddr[" + c1 + "] -> Size: " + blkLen + "  Offset: " + blkPos);				

//The entries in the block address table are what I call block addresses. Each address is a packed offset+size. 
//The least-significant 5 bits of the number indicate the block's size, as a power of 2 (from 2^5 to 2^31). If those bits are masked off, the result 
//is the starting offset of the block (keeping in mind the 4-byte fudge factor). Since the lower 5 bits are unusable to store an offset, blocks must be 
//allocated on 32-byte boundaries, and as a side effect the minimum block size is 32 bytes (in which case the least significant 5 bits are equal to 0x05).


			}
			
			// Seek to the end of %256 items (which are unused)
			seekDiff = 0;
			if (blockCnt % 256 != 0)
				seekDiff = (256 - blockCnt%256) * 4;
			
			dataBuf.position(dataBuf.position() + seekDiff);
			
			// Read the block directories
			dirCnt = dataBuf.getInt();
refTask.infoAppendln("Block directory count: " + dirCnt);
			
			blockDirList = new ArrayList<>(dirCnt);
			for (int c1 = 0; c1 < dirCnt; c1++)
				blockDirList.add(new BlockDir(dataBuf));
			
			
			// Read the free lists
int freeCnt;			
refTask.infoAppendln("Reading freelists...");
			for (int c1 = 0; c1 < 32; c1++)
			{
				freeCnt = dataBuf.getInt();
				refTask.infoAppend("[" + c1 + "]: " + freeCnt + " Offsets: ");				
				for (int c2 = 0; c2 < freeCnt; c2++)
					refTask.infoAppend(dataBuf.getInt() + ",");
				refTask.infoAppendln("");
			}
			
			
			
			// Read the DSDB structure
// TODO: Remove hard coded value			
			dataBuf.position(64);
			readDsdbNode(dataBuf);
			
// TODO: Remove hard coded value			
			dataBuf.position(4096);
			readRecords(dataBuf);
			
//			The 32-byte header has the following fields:
//
//		    Magic number Bud1 (42 75 64 31)
//		    Offset to the allocator's bookkeeping information block
//		    Size of the allocator's bookkeeping information block
//		    A second copy of the offset; the Finder will refuse to read the file if this does not match the first copy. Perhaps this is a safeguard against corruption from an interrupted write transaction.
//		    Sixteen bytes of unknown purpose. The
		}
		catch (IOException aExp)
		{
			aExp.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	
	public void readRecords(ByteBuffer aBuf)
	{
		List<Record> recordList;
		BKGDRecord bkgdRecord;
		Record tmpRecord;
		int pCode, numRecords;
		String name, id, type;
		int begPos;

		// Read the record info
		begPos = aBuf.position();
		pCode = aBuf.getInt();
		numRecords = aBuf.getInt();
		
		bkgdRecord = null;
		recordList = new ArrayList<>(numRecords);
		for (int c1 = 0; c1 < numRecords; c1++)
		{
			BufUtils.seek(aBuf, 2); // empty
			name = BufUtils.readStringUtf16(aBuf);
			
			id = BufUtils.readRawAsciiStr(aBuf, 4);
			type = BufUtils.readRawAsciiStr(aBuf, 4);
			
			// Read the Records
			if (id.equals("pict") == true && type.equals(DT_blob) == true)
			{
				tmpRecord = new PictRecord(name, id, type);
				bkgdRecord.setRefAliasRecord(((PictRecord)tmpRecord).getAliasRecord());
			}
			else if (id.equals("BKGD") == true && type.equals(DT_blob) == true)
			{
				bkgdRecord = new BKGDRecord(name, id, type);
				tmpRecord = bkgdRecord;
			}
			else if (id.equals("Iloc") == true)
				tmpRecord = new IlocRecord(name, id, type);
			else if (type.equals(DT_bool) == true)
				tmpRecord = new BoolRecord(name, id, type);
			else if (type.equals(DT_blob) == true)
				tmpRecord = new BlobRecord(name, id, type);
			else if (type.equals(DT_ustr) == true)
				tmpRecord = new UstrRecord(name, id, type);
			else if (type.equals(DT_long) == true)
				tmpRecord = new LongRecord(name, id, type);
			else
				tmpRecord = new ShorRecord(name, id, type);
			
			tmpRecord.readPayload(aBuf);
			recordList.add(tmpRecord);

			refTask.infoAppendln("Found Name: <" + name + ">  recordId: " + id + " dataType: " + type + " payloadSize: " + tmpRecord.getSize());
		}
		
		
		// TODO: This should not be in the read method...
		// Update the records to reflect the new volumeName
		if (volumeName != null)
		{
			for (Record aRecord : recordList)
			{
				if (aRecord instanceof PictRecord)
					((PictRecord)aRecord).getAliasRecord().setVolumeName(volumeName);
				
//				if (aRecord.getName().equals(" ") == false && aRecord.getId().equals("Iloc") == true)				
				if (aRecord.getName().endsWith(".app") == true && aRecord.getId().equals("Iloc") == true)
					aRecord.setName(volumeName + ".app");
				else if (aRecord.getName().equals(" ") == true && aRecord.getId().equals("Iloc") == true)
					aRecord.setName("Applications");
			}
			
			// Sort the list alphabetically
			Collections.sort(recordList);
			
			
			// Output back to the ByteBuf
			aBuf.position(begPos);
			
			aBuf.putInt(pCode);
			aBuf.putInt(numRecords);
			
			for (Record aRecord : recordList)
			{
				aBuf.putShort((short)0);
				aRecord.writeHeader(aBuf);
				aRecord.writePayload(aBuf);
			}
		}
	}
	
	
	



	public void readDsdbNode(ByteBuffer srcBuf)
	{
		int rootBlockNum;
		int numLevels;
		int numRecords;
		int numNodes;
		int valConst;

		// Read the DSDB header
		// 0: The block number of the root node of the B-tree
		// 1: The number of levels of internal nodes (tree height minus one --- that is, for a tree containing only a single, leaf, node this will be zero)
		// 2: The number of records in the tree
		// 3: The number of nodes in the tree (tree nodes, not including this header block)
		// 4: Always 0x1000, probably the tree node page size
		rootBlockNum = srcBuf.getInt();
		numLevels = srcBuf.getInt();
		numRecords = srcBuf.getInt();
		numNodes = srcBuf.getInt();
		valConst = srcBuf.getInt();

		refTask.infoAppendln("rootBlockNum: " + rootBlockNum);
		refTask.infoAppendln("numLevels: " + numLevels);
		refTask.infoAppendln("rootRecords: " + numRecords);
		refTask.infoAppendln("rootNodes: " + numNodes);
		refTask.infoAppendln("valConst: " + valConst);
	}
	
	
	/**
	 * Application main entry point
	 */
	public static void main(String[] args) throws Exception
	{
		MainApp aMainApp;
		File aFile;
		String storeFileName, newVolName;

		storeFileName = null;
		newVolName = null;

		if (args.length == 0)
		{
			System.out.println("Usage: dsStoreUtil <fileName> <newVolumeName>");
			System.exit(-1);
			return;
		}
		if (args.length >= 1)
		{
			storeFileName = args[0];
		}
		if (args.length >= 2)
		{
			newVolName = args[1];
		}

		aFile = new File(storeFileName);
		System.out.println("Updating store: " + aFile);

		aMainApp = new MainApp(newVolName);

		// Bail if we failed to read the store
		if (aMainApp.readStore(aFile) == false)
			System.exit(-1);

		// Save the ds_store if there was a request for a new name
		if (newVolName != null)
			aMainApp.writeStore(aFile);
	}

}
