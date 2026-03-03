package com.trdp.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlFactory;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

/**
 * Loads TRDP device configuration from XML files conforming to the IEC 61375-2-3
 * {@code trdp-config.xsd} schema.
 * <p>
 * The XML is first validated against the XSD (bundled on the classpath), then
 * deserialized into an immutable {@link DeviceConfig} object graph.
 * <p>
 * Usage:
 * <pre>{@code
 * DeviceConfig config = TrdpConfig.load(Path.of("trdp-config.xml"));
 * for (BusInterface bi : config.getBusInterfaces()) {
 *     System.out.println(bi.getName() + " -> " + bi.getTelegrams().size() + " telegrams");
 * }
 * }</pre>
 *
 * @see DeviceConfig
 * @see TrdpConfigException
 */
public final class TrdpConfig {

    private static final String XSD_RESOURCE = "trdp-config.xsd";

    private TrdpConfig() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Loads and validates a TRDP configuration from the given file path.
     *
     * @param path the path to the XML configuration file.
     * @return the parsed device configuration.
     * @throws TrdpConfigException if the file cannot be read, fails XSD validation,
     *                             or cannot be deserialized.
     */
    public static DeviceConfig load(Path path) throws TrdpConfigException {
        try {
            byte[] data = Files.readAllBytes(path);
            return parse(data);
        } catch (IOException e) {
            throw new TrdpConfigException("Failed to read configuration file: " + path, e);
        }
    }

    /**
     * Loads and validates a TRDP configuration from the given input stream.
     *
     * @param inputStream the input stream containing the XML configuration.
     * @return the parsed device configuration.
     * @throws TrdpConfigException if the stream cannot be read, fails XSD validation,
     *                             or cannot be deserialized.
     */
    public static DeviceConfig load(InputStream inputStream) throws TrdpConfigException {
        try {
            byte[] data = inputStream.readAllBytes();
            return parse(data);
        } catch (IOException e) {
            throw new TrdpConfigException("Failed to read configuration from input stream", e);
        }
    }

    private static DeviceConfig parse(byte[] data) throws TrdpConfigException {
        validateAgainstXsd(data);
        return deserialize(data);
    }

    private static void validateAgainstXsd(byte[] data) throws TrdpConfigException {
        try (InputStream xsdStream = TrdpConfig.class.getClassLoader().getResourceAsStream(XSD_RESOURCE)) {
            if (xsdStream == null) {
                throw new TrdpConfigException("XSD resource not found on classpath: " + XSD_RESOURCE);
            }

            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = schemaFactory.newSchema(new StreamSource(xsdStream));
            Validator validator = schema.newValidator();

            // Prevent XXE attacks
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            validator.validate(new StreamSource(new ByteArrayInputStream(data)));
        } catch (SAXException e) {
            throw new TrdpConfigException("XML validation failed: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new TrdpConfigException("I/O error during XML validation", e);
        }
    }

    private static DeviceConfig deserialize(byte[] data) throws TrdpConfigException {
        try {
            XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();
            // Prevent XXE attacks
            xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

            XmlFactory xmlFactory = new XmlFactory(xmlInputFactory);
            XmlMapper mapper = XmlMapper.builder(xmlFactory)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build();

            return mapper.readValue(data, DeviceConfig.class);
        } catch (IOException e) {
            throw new TrdpConfigException("Failed to deserialize XML configuration: " + e.getMessage(), e);
        }
    }
}
