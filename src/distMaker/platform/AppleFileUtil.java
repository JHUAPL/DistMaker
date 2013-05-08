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
public class AppleFileUtil
{
	/**
	 * Utility method to update the specified max memory (-Xmx) value in the plist file (aFile) to the specified
	 * maxMemVal.
	 * <P>
	 * Note this method is very brittle, and assumes that the value occurs within <string> tags. The assumption is that
	 * the occurrence of the string, -Xmx, will be once and refer to the max heap memory.
	 */
	public static boolean updateMaxMem(File aFile, long numBytes)
	{
		DocumentBuilderFactory dbf;
		Document dom;
		Element doc;
		String evalStr, updateStr;
		boolean isProcessed;

		dom = null;
		isProcessed = false;

		// Make an instance of the DocumentBuilderFactory
		dbf = DocumentBuilderFactory.newInstance();
		try
		{
			// use the factory to take an instance of the document builder
			DocumentBuilder db = dbf.newDocumentBuilder();

			// Parse using the builder to get the DOM mapping of the XML file
			dom = db.parse(aFile);
			doc = dom.getDocumentElement();

			NodeList nodeList;
			Node keyNode;

			nodeList = doc.getElementsByTagName("string");
			for (int c1 = 0; c1 < nodeList.getLength(); c1++)
			{
				keyNode = nodeList.item(c1).getFirstChild();
				if (keyNode != null)
				{
					evalStr = keyNode.getNodeValue();
					updateStr = MemUtils.transformMaxMemHeapString(evalStr, numBytes);
					if (updateStr != null)
					{
						isProcessed = true;
						keyNode.setNodeValue(updateStr);
						break;
					}
				}
			}
		}
		catch (Exception aExp)
		{
			aExp.printStackTrace();
			return false;
		}

		// Bail if we did not find a line to change
		if (isProcessed == false)
		{
			Exception aExp;
			aExp = new Exception("Failed to locate -Xmx string!");
			aExp.printStackTrace();
			return false;
		}

		// Update the file with the changed document
		System.out.println("Updating contents of file: " + aFile);
		return writeDoc(aFile, dom);
	}

	/**
	 * Utility method to update the specified version in the plist file (aFile) to the new version.
	 * <P>
	 * Note this method is very brittle, and assumes that the version will occur in the sibling node which immediately
	 * follows the node with a value of CFBundleVersion.
	 */
	public static boolean updateVersion(File aFile, String aNewVersin)
	{
		DocumentBuilderFactory dbf;
		Document dom;
		Element doc;

		// Make an instance of the DocumentBuilderFactory
		dbf = DocumentBuilderFactory.newInstance();
		try
		{
			// use the factory to take an instance of the document builder
			DocumentBuilder db = dbf.newDocumentBuilder();

			// Parse using the builder to get the DOM mapping of the XML file
			dom = db.parse(aFile);
			doc = dom.getDocumentElement();

			NodeList nodeList;
			Node keyNode, strNode;

			nodeList = doc.getElementsByTagName("*");
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
		}
		catch (Exception aExp)
		{
			aExp.printStackTrace();
			return false;
		}

		// Update the file with the changed document
		return writeDoc(aFile, dom);
	}

	/**
	 * Helper method to output the specified document to aFile
	 */
	private static boolean writeDoc(File aFile, Document aDoc)
	{
		FileOutputStream oStream;

		oStream = null;
		try
		{
			Transformer tr = TransformerFactory.newInstance().newTransformer();
			tr.setOutputProperty(OutputKeys.INDENT, "yes");
			tr.setOutputProperty(OutputKeys.METHOD, "xml");
			tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
//			tr.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "roles.dtd");
//			tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

			// Serialize the Document
			oStream = new FileOutputStream(aFile);
			tr.transform(new DOMSource(aDoc), new StreamResult(oStream));

			return true;
		}
		catch (Exception aExp)
		{
			aExp.printStackTrace();
		}
		finally
		{
			IoUtil.forceClose(oStream);
		}

		return false;
	}

}
