package com.trdp.config;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Data set definition ({@code <data-set>} element).
 * <p>
 * Defines a structured payload layout identified by a numeric ID (>= 1000).
 * Contains one or more {@link DataSetElement} entries that describe the
 * fields, their types, and array sizes.
 *
 * @see DeviceConfig#getDataSetById(int)
 */
public class DataSetDefinition {

    private final int id;
    private final String name;
    private final List<DataSetElement> elements;

    public DataSetDefinition(
            @JacksonXmlProperty(localName = "id", isAttribute = true) int id,
            @JacksonXmlProperty(localName = "name", isAttribute = true) String name,
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "element") List<DataSetElement> elements) {
        this.id = id;
        this.name = name;
        this.elements = elements != null ? Collections.unmodifiableList(elements) : Collections.emptyList();
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<DataSetElement> getElements() {
        return elements;
    }

    @Override
    public String toString() {
        return String.format("DataSetDefinition{id=%d, name='%s', elements=%d}", id, name, elements.size());
    }
}
