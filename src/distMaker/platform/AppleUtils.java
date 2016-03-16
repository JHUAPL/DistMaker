package distMaker.platform;

import glum.io.IoUtil;

import java.io.File;
import java.io.FileOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.*;

/**
 * Utility class which contains a set of methods to interact with an Apple Info.plist file.
 */
public class AppleUtils
{
	/**
	 * Utility method to update the specified max memory (-Xmx) value in the plist file (aFile) to the specified maxMemVal.
	 * <P>
	 * In order for this method to succeed there must be a valid JVMOptions section followed by an array of string elements of JVM arguments. The array element
	 * may be empty but must be specified.
	 * 
	 * @return Returns null on success or an error message describing the issue.
	 */
	public static String updateMaxMem(File aFile, long numBytes)
	{
		Document doc;
		Element docElement;
		String evalStr, updateStr;
		NodeList dictList, childList;
		Node childNode, targNode;
		Element evalE, arrE, memE;
		String tagStr, valStr, currKeyVal;
		int zios_Clean;

		// Load the XML document via the javax.xml.parsers.* package
		try
		{
			doc = loadDoc(aFile);
			docElement = doc.getDocumentElement();
		}
		catch(Exception aExp)
		{
			aExp.printStackTrace();
			return "Failed to parse XML document. File: " + aFile;
		}

		// Locate the <dict> element
		dictList = docElement.getElementsByTagName("dict");
		if (dictList.getLength() == 0)
			return "No <dict> element found!";

		arrE = null;
		currKeyVal = null;
		childList = dictList.item(0).getChildNodes();
		for (int c1 = 0; c1 < childList.getLength(); c1++)
		{
			childNode = childList.item(c1);
			if (childNode.getNodeType() != Node.ELEMENT_NODE)
			{
//				System.out.println("" + c1 + ": nodeType: " + childNode.getNodeType());
				continue;
			}

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
			return "Failed to locate the element <array> following the element: <key>JVMOptions</key>";

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
			return "Failed to transform the memory spec value. Original value: " + evalStr;
		targNode.setNodeValue(updateStr);

		// Update the file with the changed document
		System.out.println("Updating contents of file: " + aFile);
		return saveDoc(aFile, doc);
	}

	/**
	 * Utility method to update the specified version in the plist file (aFile) to the new version.
	 * <P>
	 * Note this method is very brittle, and assumes that the version will occur in the sibling node which immediately follows the node with a value of
	 * CFBundleVersion.
	 */
	public static String updateVersion(File aFile, String aNewVersin)
	{
		Document doc;
		Element docElement;
		NodeList nodeList;
		Node keyNode, strNode;

		// Load the XML document via the javax.xml.parsers.* package
		try
		{
			doc = loadDoc(aFile);
			docElement = doc.getDocumentElement();
		}
		catch(Exception aExp)
		{
			aExp.printStackTrace();
			return "Failed to parse XML document. File: " + aFile;
		}

		nodeList = docElement.getElementsByTagName("*");
		for (int c1 = 0; c1 < nodeList.getLength(); c1++)
		{
			keyNode = nodeList.item(c1).getFirstChild();
			if (keyNode != null && keyNode.getNodeValue().equals("CFBundleVersion") == true)
			{
				System.out.println("Updating contents of file: " + aFile);

				strNode = nodeList.item(c1 + 1).getFirstChild();
				System.out.println("  Old Version: " + strNode.getNodeValue());

				strNode.setNodeValue(aNewVersin);
				System.out.println("  New Version: " + strNode.getNodeValue());
			}
		}

		// Update the file with the changed document
		return saveDoc(aFile, doc);
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
	 */
	private static String saveDoc(File aFile, Document aDoc)
	{
		FileOutputStream oStream;

		oStream = null;
		try
		{
			Transformer tr = TransformerFactory.newInstance().newTransformer();
			tr.setOutputProperty(OutputKeys.INDENT, "yes");
			tr.setOutputProperty(OutputKeys.METHOD, "xml");
			tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "3");
//			tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
//			tr.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "roles.dtd");

			// Serialize the Document
			oStream = new FileOutputStream(aFile);
			tr.transform(new DOMSource(aDoc), new StreamResult(oStream));
		}
		catch(Exception aExp)
		{
			aExp.printStackTrace();
			return "Failed to write the file: " + aFile;
		}
		finally
		{
			IoUtil.forceClose(oStream);
		}

		return null;
	}

}
