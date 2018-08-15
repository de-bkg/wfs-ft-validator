/*
 * This file is part of the wfs-ft-validator project.
 * Copyright (c) 2017 Bundesamt für Kartographie und Geodäsie <http://bkg.bund.de>
 *
 * Licensed under MIT License.
 * See the LICENSE file in the project root for more information.
 */

package org.gdz.wfs.validator;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.net.*;
import java.io.*;
import java.util.*;

/**
 * Script to validate a WFS Service and all the supported Feature Types.
 *
 * @author Anna-Lena Hock (ahock-bkg)
 */
public class WfsValidator {

    // == Static =======================================================================================================

    private static final Logger LOGGER = LoggerFactory.getLogger(WfsValidator.class);

    private static final SchemaFactory SCHEMA_FACTORY = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);


    /** starting point
     * @param args program arguments: first argument is the URL of the Service
     */
    public static void main(String args[]) throws Exception{
        WfsValidator validator = parseCommandLine(args);

        validator.validate();

        System.exit(validator.hasErrors() ? 2 : 0);
    }

    /**
     * Creates the programm options for parsing the command line.
     */
    private static Options createProgrammOptions() {
        Options options = new Options();

        options.addOption(
                Option.builder().longOpt("count")
                        .desc("Number of features to test with each request.")
                        .hasArg()
                        .argName("NUMBER")
                        .build()
        );

        // TODO: additional arguments: resolve depth, mandatory features

        return options;
    }

    /**
     * Prints the help for the programm options.
     */
    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("wfs-ft-validator", options);
    }

    /**
     * Parses the command line and assigns all properties.
     *
     * @param args - the command line arguments
     */
    private static WfsValidator parseCommandLine(String[] args) throws Exception {
        Options options = createProgrammOptions();
        CommandLineParser parser = new DefaultParser();
        try {
            // parse the command line arguments
            CommandLine commandLine = parser.parse(options, args);

            // arguments
            if (commandLine.getArgList().size() < 1)
                throw new IllegalArgumentException("Missing WFS URL for testing.");

            String url = commandLine.getArgList().get(0);
            WfsValidator validator = new WfsValidator(url);

            // Options
            if (commandLine.hasOption("count"))
                validator.setFeatureCount(Integer.valueOf(commandLine.getOptionValue("count")));

            return validator;
        } catch (Exception exp) {
            System.out.println("Error parsing arguments: " + exp.getMessage());
            printHelp(options);
            System.exit(1);
            return null;    // never reached!
        }
    }

    // == Members ======================================================================================================


    private final String serviceUrl;

    private int featureCount = 10;

    private int errors = 0;

    public WfsValidator(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public int getFeatureCount() { return featureCount; }
    public void setFeatureCount(int featureCount) { this.featureCount = featureCount; }

    private boolean hasErrors() { return errors > 0; }

    /**
     * Performs the validation with the configured parameters.
     */
    private void validate() throws Exception {
        LOGGER.info("Validating service: {}", serviceUrl);
        errors = 0;

        String capabilitiesRequestKvp = "?service=WFS&request=GetCapabilities";

        String capabilitiesString = getStringFromRequest(serviceUrl + capabilitiesRequestKvp);

        if (capabilitiesString.trim().length() == 0){
            LOGGER.info("Capabilities Response without content");
            errors ++;
        }

        Document capabilitiesResponse = getDocumentFromString(capabilitiesString);
        ArrayList<String> featureTypes = getFeatureTypes(capabilitiesResponse);

        validateFeatureTypes(featureTypes, serviceUrl);
    }

    /**
     * Submits a request and writes the response to a string
     * @param request request, which should be submitted
     * @return the response string
     */
    private String getStringFromRequest(String request) throws IOException {
        String responseString;

        URL requestUrl = new URL(request);
        URLConnection connection = requestUrl.openConnection();

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        responseString = reader.readLine();
        StringBuilder builder = new StringBuilder();

        while (responseString != null) {
            builder.append(responseString).append("\n");
            responseString = reader.readLine();
        }
        responseString = builder.toString();
        reader.close();

        return responseString;
    }

    /** Creates a XML document from a string
     * @param xmlText xml text stored as string
     * @return the document object, created from the string
     */
    private Document getDocumentFromString(String xmlText) throws IOException {

        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            docFactory.setNamespaceAware(false);
            docFactory.setValidating(false);

            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            return docBuilder.parse(new InputSource(new StringReader(xmlText)));

        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("XML Parser...: " + e.getMessage(), e);
        } catch (SAXException e) {
            throw new IOException("Error parsing XML document: " + e.getMessage(), e);
        }
    }

    /** Get the supported feature types from the GetCapabilities response
     * @param capabilitiesResponse the response from GetCapabilities as Document
     * @return ArrayList with Strings containing the feature type names for example tn-a:AerodromeArea
     */
    private ArrayList<String> getFeatureTypes(Document capabilitiesResponse){

        NodeList featureTypeNodes = capabilitiesResponse.getElementsByTagName("FeatureType");

        ArrayList<String> featureTypes = new ArrayList<>();

        for (int i = 0; i < featureTypeNodes.getLength(); i++) {

            Element featureTypeElement = (Element) featureTypeNodes.item(i);

            if (featureTypeElement.getNodeType() != Node.ELEMENT_NODE)
                continue;

            NodeList nameList = featureTypeElement.getElementsByTagName("Name").item(0).getChildNodes();
            Node nameNode = nameList.item(0);
            featureTypes.add(nameNode.getNodeValue());
        }

        return featureTypes;
    }

    /** Validate the schema and hrefs of an array of feature types
     * @param featureTypes to be validated feature types
     * @param serviceUrl the URL of the service
     */
    private void validateFeatureTypes(ArrayList<String> featureTypes, String serviceUrl)
            throws SAXException, IOException
    {

        LOGGER.info("----------------------------Building Schema for WFS----------------------------");
        String requestSchema = serviceUrl + "?SERVICE=WFS&VERSION=2.0.0&REQUEST=DescribeFeatureType&OUTPUTFORMAT=text%2Fxml%3B+subtype%3Dgml%2F3.2.1";
        String schemaResponseString = getStringFromRequest(requestSchema);

        //Missing Includes for wfs schema have to be included manually: wfs:FeatureCollection
        schemaResponseString = schemaResponseString.replace("</schema>", "<import namespace=\"http://www.opengis.net/wfs/2.0\" schemaLocation=\"http://schemas.opengis.net/wfs/2.0/wfs.xsd\"/>\n</schema>");

        Schema schema = SCHEMA_FACTORY.newSchema(new StreamSource(new StringReader(schemaResponseString)));

        String getFeatureRequestKvp = "?SERVICE=WFS&VERSION=2.0.0&REQUEST=GetFeature&TYPENAMES=%s&COUNT=" + featureCount;
        for (String type : featureTypes) {
            LOGGER.info("----------------------------Validating Feature Type {}----------------------------", type);

            String featuresRequest = serviceUrl + String.format(getFeatureRequestKvp, type);

            try {
                String featureResponseString = getStringFromRequest(featuresRequest);
                if (Objects.equals(featureResponseString, "")) {
                    continue;
                }

                Document featureResponseDoc = getDocumentFromString(featureResponseString);
                validateHrefs(featureResponseDoc, serviceUrl);

                validateSchema(featureResponseString, schema);
            } catch (IOException ex) {
                LOGGER.warn("Error requesting Feature Type. URL: {}", featuresRequest);
                LOGGER.warn("Error requesting Feature Type!  {}", ex.getLocalizedMessage());
                errors++;
            } catch (SAXException ex) {
                LOGGER.warn("Error validating Feature Type!  {}", ex.getLocalizedMessage());
                errors++;
            }
        }
    }

    /** Validates the Hrefs (which contain the serviceUrl) within the GetFeature response
     * @param getFeatureResponse the Response from the GetFeature
     * @param serviceUrl the URL of the service
     */
    private void validateHrefs(Document getFeatureResponse, String serviceUrl) {
        try {

            XPath xPath = XPathFactory.newInstance().newXPath();

            NodeList hrefNodes = (NodeList) xPath.evaluate("//@href",
                    getFeatureResponse.getDocumentElement(), XPathConstants.NODESET);

            int countURLs = 0;

            for (int i = 0; i < hrefNodes.getLength(); ++i) {
                Node e = hrefNodes.item(i);
                try {

                    if (e.getNodeValue().contains(serviceUrl)) {
                        getStringFromRequest(e.getNodeValue());
                        countURLs++;
                    }

                } catch (IOException ex) {
                    LOGGER.info("Wrong URL: {}", e.getNodeValue());
                    errors++;
                }
            }
            LOGGER.info("Number of URLs found: {}", countURLs);

        } catch (XPathExpressionException e) {
            throw new IllegalStateException("Error evaluating XPath expression!", e);
        }
    }

    /** Requests the feature type schema (DescribeFeatureType) and validates the GetFeature response with it
     * @param getFeatureResponse The list of features returned from GetFeature request
     * @param schema the complete schema of the WFS
     */
    private void validateSchema(String getFeatureResponse, Schema schema) throws IOException, SAXException {
        Validator validator = schema.newValidator();

        final List<SAXParseException> exceptions = new LinkedList<>();
        validator.setErrorHandler(new ErrorHandler()
        {
            @Override
            public void warning(SAXParseException exception) throws SAXException
            {
                exceptions.add(exception);
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException
            {
                exceptions.add(exception);
            }

            @Override
            public void error(SAXParseException exception) throws SAXException
            {
                exceptions.add(exception);
            }
        });

        validator.validate(new StreamSource(new StringReader(getFeatureResponse)));

        for(SAXParseException exception:exceptions)
        {
            LOGGER.info("Error validating Schema: {}", exception.getLocalizedMessage());
        }

        if (exceptions.size() == 0)
            LOGGER.info("FeatureType is valid");
        else
            errors ++;
    }
}
