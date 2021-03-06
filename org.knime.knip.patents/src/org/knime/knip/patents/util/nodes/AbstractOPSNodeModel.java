package org.knime.knip.patents.util.nodes;

import java.io.IOException;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.knime.core.data.StringValue;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.defaultnodesettings.SettingsModel;
import org.knime.knip.base.node.ValueToCellsNodeModel;
import org.knime.knip.patents.KNIMEOPSPlugin;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Abstract {@link NodeModel} for all subclasses which use one of the REST
 * Services from ops.epo.org
 * 
 * @author Daniel Seebacher, University of Konstanz
 * 
 */
public abstract class AbstractOPSNodeModel extends
		ValueToCellsNodeModel<StringValue> {

	private static final NodeLogger LOGGER = NodeLogger
			.getLogger(AbstractOPSNodeModel.class);
	
	private final XPath xpath;

	public AbstractOPSNodeModel() {
		XPathFactory xPathfactory = XPathFactory.newInstance();
		this.xpath = xPathfactory.newXPath();
	}
	
	/**
	 * Override this method if you need additional SettingsModel in subclasses
	 */
	@Override
	protected void addSettingsModels(List<SettingsModel> settingsModels) {
		// 
	}

	/**
	 * Takes an {@link HttpURLConnection} and parses its error stream and
	 * returns a pretty printed xml.
	 * 
	 * @param connection
	 *            A {@link HttpURLConnection} which returned an error code.
	 * 
	 * @return pretty printed error message.
	 * 
	 * 
	 * @throws SAXException
	 * @throws IOException
	 * @throws ParserConfigurationException
	 * @throws TransformerException
	 */
	protected String parseErrorMessage(HttpURLConnection connection)
			throws SAXException, IOException, ParserConfigurationException,
			TransformerException {

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		Document doc = db.parse(connection.getErrorStream());

		Transformer transformer = TransformerFactory.newInstance()
				.newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		// initialize StreamResult with File object to save to file
		StreamResult result = new StreamResult(new StringWriter());
		DOMSource source = new DOMSource(doc);
		transformer.transform(source, result);
		String xmlString = result.getWriter().toString();

		return xmlString;
	}

	/**
	 * The ops.epo.org REST services have a minutely limit. Check this limit and
	 * Thread.sleep() for the required amount of seconds. If throttling is
	 * disabled, do nothing.
	 * 
	 * @param connection
	 *            an {@link HttpURLConnection} to one of the REST services.
	 * @param field
	 *            the field which is currently used (image, search, retrieval,
	 *            etc.)
	 * 
	 * @throws InterruptedException
	 */
	protected void throttle(HttpURLConnection connection, String field)
			throws InterruptedException {

		if (!KNIMEOPSPlugin.isThrottlingEnabled()) {
			return;
		}

		String serverStatus = "unknown";
		int serviceLimit = 0;
		
		try {
			String throttleControl = connection
					.getHeaderField("X-Throttling-Control");			
			serverStatus = throttleControl.split(" ")[0];

			serviceLimit = Integer
					.parseInt(throttleControl.split(field + "=")[1].split(":")[1]
							.split("[^\\d]")[0]);
		} catch (Exception e) {
			serviceLimit = 20;
			LOGGER.warn(
					"Couldn't retrieve server status, sleep for a few seconds. "
							+ e.getMessage(), e);
		}

		if (serviceLimit == 0) {
			++serviceLimit;
		}

		int s = 60000 / serviceLimit;
		LOGGER.debug("Server Status: " + serverStatus
				+ "\t Throttle Control Set to " + serviceLimit
				+ " queries per minute! \t Sleeping for " + (s / 1000)
				+ " seconds!");

		Thread.sleep(s);
	}

	/**
	 * Checks the response code of the {@link HttpURLConnection}. Throws an
	 * exception if it isn't 200.
	 * 
	 * @param connection
	 *            an {@link HttpURLConnection} to one of the REST services.
	 * 
	 * @throws IOException
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 * @throws TransformerException
	 */
	protected void checkResponse(HttpURLConnection connection)
			throws IOException, SAXException, ParserConfigurationException,
			TransformerException {

		int responseCode = connection.getResponseCode();
		String responseMessage = connection.getResponseMessage();

		if (responseCode >= 400) {

			throw new RuntimeException("Server returned error [" + responseCode
					+ ", " + responseMessage + "]" + "\n Respone: \n"
					+ parseErrorMessage(connection));
		}
	}

	public abstract URL getURL(String... input) throws MalformedURLException;
	
	protected XPath getXPath(){
		return xpath;
	}
}
