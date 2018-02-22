package distMaker.platform;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.*;

import distMaker.*;
import distMaker.jre.JreUtils;
import distMaker.jre.JreVersion;

/**
 * Utility class which contains a set of methods to interact with an Apple Info.plist file.
 */
public class AppleUtils
{
	/**
	 * Returns the plist file used to configure apple applications.
	 * <P>
	 * Two locations will be searched.... TODO: Add more details of those locations.
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static File getPlistFile()
	{
		File installPath;
		File pFile;

		// Get the top level install path
		installPath = DistUtils.getAppPath().getParentFile();

		// Attempt to locate the pList file (from one of the possible locations)
		pFile = new File(installPath, "Info.plist");
		if (pFile.isFile() == false)
			pFile = new File(installPath.getParentFile(), "Info.plist");

		// Bail if we failed to locate the plist file
		if (pFile.exists() == false)
			throw new ErrorDM("The plist file could not be located.");

		// Bail if the plist file is not a regular file.
		if (pFile.isFile() == false)
			throw new ErrorDM("The plist file does not appear to be a regular file: " + pFile);

		return pFile;
	}

	/**
	 * Utility method to update the specified version in the plist file (pFile) to the new version.
	 * <P>
	 * Note this method is very brittle, and assumes that the version will occur in the sibling node which immediately
	 * follows the node with a value of CFBundleVersion. TODO: Consider reducing brittleness.
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateAppVersion(String aNewVersion)
	{
		// Utilize the system pList file and delegate.
		updateAppVersion(aNewVersion, getPlistFile());
	}

	/**
	 * Utility method to update the specified version in the plist file (pFile) to the new version.
	 * <P>
	 * Note this method is very brittle, and assumes that the version will occur in the sibling node which immediately
	 * follows the node with a value of CFBundleVersion. TODO: Consider reducing brittleness.
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateAppVersion(String aNewVersin, File pFile)
	{
		Document doc;
		Element docElement;
		NodeList nodeList;
		Node keyNode, strNode;

		// Bail if the pFile is not writable.
		if (pFile.setWritable(true) == false)
			throw new ErrorDM("The plist file is not writeable: " + pFile);

		// Load the XML document via the javax.xml.parsers.* package
		try
		{
			doc = loadDoc(pFile);
			docElement = doc.getDocumentElement();
		}
		catch(Exception aExp)
		{
			throw new ErrorDM(aExp, "Failed to parse XML document. File: " + pFile);
		}

		nodeList = docElement.getElementsByTagName("*");
		for (int c1 = 0; c1 < nodeList.getLength(); c1++)
		{
			keyNode = nodeList.item(c1).getFirstChild();
			if (keyNode != null && keyNode.getNodeValue().equals("CFBundleVersion") == true)
			{
				System.out.println("Updating contents of file: " + pFile);

				strNode = nodeList.item(c1 + 1).getFirstChild();
				System.out.println("  Old Version: " + strNode.getNodeValue());

				strNode.setNodeValue(aNewVersin);
				System.out.println("  New Version: " + strNode.getNodeValue());
			}
		}

		// Update the file with the changed document
		saveDoc(pFile, doc);
	}

	/**
	 * Utility method to update the JRE to point to the specified path in the system plist file.
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateJreVersion(JreVersion aJreVersion)
	{
		// Utilize the system pList file and delegate.
		updateJreVersion(aJreVersion, getPlistFile());
	}

	/**
	 * Utility method to update the JRE to point to the specified path in the plist file (pFile).
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateJreVersion(JreVersion aJreVersion, File pFile)
	{
		List<String> inputList;
		String evalStr, tmpStr;
		int currLineNum, targLineNum;

		// Bail if the pFile is not writable
		if (pFile.setWritable(true) == false)
			throw new ErrorDM("The pFile is not writeable: " + pFile);

		// Process our input
		inputList = new ArrayList<>();
		try (BufferedReader br = MiscUtils.openFileAsBufferedReader(pFile))
		{
			// Read the lines
			currLineNum = 0;
			targLineNum = -1;
			while (true)
			{
				evalStr = br.readLine();
				if (evalStr == null)
					break;

				// Record the start of the JVMRunitme section. Note the JRE should be specified on the next line
				tmpStr = evalStr.trim();
				if (tmpStr.equals("<key>JVMRuntime</key>") == true)
					targLineNum = currLineNum + 1;

				inputList.add(evalStr);
				currLineNum++;
			}
		}
		catch(IOException aExp)
		{
			throw new ErrorDM(aExp, "Failed while processing the pFile: " + pFile);
		}

		// Update the pFile
		String regex = "<string>(.*?)</string>";
		String repStr = "<string>" + JreUtils.getExpandJrePath(aJreVersion) + "</string>";
		if (targLineNum == -1)
			throw new ErrorDM("[" + pFile + "] The pFile does not specify a 'JVMRuntime' section.");
		else if (targLineNum >= inputList.size())
			throw new ErrorDM("[" + pFile + "] The pFile appears to be incomplete!");
		else
			inputList.set(targLineNum, inputList.get(targLineNum).replaceFirst(regex, repStr));

		// Write the pFile
		MiscUtils.writeDoc(pFile, inputList);
	}

	/**
	 * Utility method to update the specified max memory (-Xmx) value in the system plist file to the specified
	 * maxMemVal.
	 * <P>
	 * In order for this method to succeed there must be a valid JVMOptions section followed by an array of string
	 * elements of JVM arguments. The array element may be empty but must be specified.
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateMaxMem(long numBytes)
	{
		// Utilize the system pList file and delegate.
		updateMaxMem(numBytes, getPlistFile());
	}

	/**
	 * Utility method to update the specified max memory (-Xmx) value in the plist file (pFile) to the specified
	 * maxMemVal.
	 * <P>
	 * In order for this method to succeed there must be a valid JVMOptions section followed by an array of string
	 * elements of JVM arguments. The array element may be empty but must be specified.
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	public static void updateMaxMem(long numBytes, File pFile)
	{
		Document doc;
		Element docElement;
		String evalStr, updateStr;
		NodeList dictList, childList;
		Node childNode, targNode;
		Element evalE, arrE, memE;
		String tagStr, valStr, currKeyVal;

		// Bail if the pFile is not writable.
		if (pFile.setWritable(true) == false)
			throw new ErrorDM("The plist file is not writeable: " + pFile);

		// Load the XML document via the javax.xml.parsers.* package
		try
		{
			doc = loadDoc(pFile);
			docElement = doc.getDocumentElement();
		}
		catch(Exception aExp)
		{
			throw new ErrorDM(aExp, "Failed to parse XML document. File: " + pFile);
		}

		// Locate the <dict> element
		dictList = docElement.getElementsByTagName("dict");
		if (dictList.getLength() == 0)
			throw new ErrorDM("No <dict> element found! File: " + pFile);

		arrE = null;
		currKeyVal = null;
		childList = dictList.item(0).getChildNodes();
		for (int c1 = 0; c1 < childList.getLength(); c1++)
		{
			childNode = childList.item(c1);
			if (childNode.getNodeType() != Node.ELEMENT_NODE)
				continue;

			evalE = (Element)childNode;
			tagStr = evalE.getTagName();
			valStr = evalE.getNodeValue();

			if (childNode.hasChildNodes() == true)
			{
				childNode = childNode.getFirstChild();
				valStr = childNode.getNodeValue();
			}
//			System.out.println("" + c1 + " Tag:" + tagStr + ": Value:" + valStr);

			if (tagStr.equals("key") == true)
				currKeyVal = childNode.getNodeValue();

			// Bail once we locate the <array> element following the <key>JVMOptions<key>
			if (tagStr.equals("array") == true && currKeyVal != null && currKeyVal.equals("JVMOptions") == true)
			{
				arrE = evalE;
				break;
			}
		}

		// Bail if we failed to locate the array element
		if (arrE == null)
			throw new ErrorDM("Failed to locate the element <array> following the element: <key>JVMOptions</key>\nFile: " + pFile);

		memE = null;
		childList = arrE.getChildNodes();
		for (int c1 = 0; c1 < childList.getLength(); c1++)
		{
			childNode = childList.item(c1);
			if (childNode.getNodeType() != Node.ELEMENT_NODE)
				continue;

			if (childNode.hasChildNodes() == false)
				continue;

			evalE = (Element)childNode;
			tagStr = evalE.getTagName();
			valStr = evalE.getNodeValue();
			if (childNode.hasChildNodes() == true)
			{
				childNode = childNode.getFirstChild();
				valStr = childNode.getNodeValue();
			}

//			System.out.println("" + c1 + " Tag:" + tagStr + ": Value:" + valStr);
			if (valStr.startsWith("-Xmx") == false)
				continue;

			memE = evalE;
		}

		// Synthesize the memElement if it does not exist (with default: -Xmx512m)
		if (memE == null)
		{
			memE = doc.createElement("string");
			arrE.appendChild(memE);
		}
		// Ensure the memElement has a valid child (text value)
		if (memE.getChildNodes().getLength() == 0)
			memE.appendChild(doc.createTextNode("-Xmx512m"));

		// Update the -Xmx value
		targNode = memE.getChildNodes().item(0);
		evalStr = targNode.getNodeValue();
		updateStr = MemUtils.transformMaxMemHeapString(evalStr, numBytes);
		if (updateStr == null)
			throw new ErrorDM("Failed to transform the memory spec value. Original value: " + evalStr + "\nFile: " + pFile);
		targNode.setNodeValue(updateStr);

		// Update the file with the changed document
		saveDoc(pFile, doc);
	}

	/**
	 * Helper method to load a Document from the specified file.
	 */
	private static Document loadDoc(File aFile) throws Exception
	{
		DocumentBuilderFactory dbf;
		DocumentBuilder db;
		Document doc;

		// Parse the XML file via the javax.xml.parsers.* package
		dbf = DocumentBuilderFactory.newInstance();
		db = dbf.newDocumentBuilder();
		doc = db.parse(aFile);

		return doc;
	}

	/**
	 * Helper method to output aDoc to the specified file.
	 * <P>
	 * On failure this method will throw an exception of type ErrorDM.
	 */
	private static void saveDoc(File aFile, Document aDoc)
	{
		try (FileOutputStream oStream = new FileOutputStream(aFile);)
		{
			Transformer tr = TransformerFactory.newInstance().newTransformer();
			tr.setOutputProperty(OutputKeys.INDENT, "yes");
			tr.setOutputProperty(OutputKeys.METHOD, "xml");
			tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "3");
//			tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
//			tr.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "roles.dtd");

			// Serialize the Document
			tr.transform(new DOMSource(aDoc), new StreamResult(oStream));
		}
		catch(Exception aExp)
		{
			throw new ErrorDM(aExp, "Failed to write the file: " + aFile);
		}
	}

}
