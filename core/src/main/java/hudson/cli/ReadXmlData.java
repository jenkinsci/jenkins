
package hudson.cli;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.kohsuke.args4j.Argument;

/**
 * 
 * @author aju.balachandran
 *
 */
public class ReadXmlData {

	public Map<String, String> getXmlDirectTagData(File xmlPath,String[] attNames)throws Exception
	{
		String retValue = null;
		Map<String, String> retMap = new HashMap<String, String>();
		try {
			  File file = xmlPath;
			  DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			  DocumentBuilder db = dbf.newDocumentBuilder();
			  Document doc = db.parse(file);
			  doc.getDocumentElement().normalize();
			 
			  Node n = doc.getDocumentElement().getFirstChild();

			  do{
				  n = n.getNextSibling();
				  for(int i = 0; i < attNames.length; i++)
				  {
					  if(n.getNodeName().equals(attNames[i]))
					  {
						  retValue = n.getTextContent();
						  retMap.put(attNames[i], retValue);
						  break;
					  }
				  }
			  }while(n != null );
			  } catch (Exception e) {
			    e.printStackTrace();
			  }
			  
	return retMap;
	}
}
