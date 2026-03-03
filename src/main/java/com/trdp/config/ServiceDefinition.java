package com.trdp.config;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Service-oriented interface definition ({@code <service>} element).
 * <p>
 * Defines a service with its events, fields, methods, device bindings,
 * and telegram references. Used for AUTOSAR-style service discovery
 * and communication.
 */
public class ServiceDefinition {

    private final String name;
    private final long id;
    private final long ttl;
    private final boolean dummyService;
    private final List<ServiceEvent> events;
    private final List<ServiceField> fields;
    private final List<ServiceMethod> methods;
    private final List<ServiceDevice> serviceDevices;
    private final List<TelegramRef> telegramRefs;

    public ServiceDefinition(
            @JacksonXmlProperty(localName = "name", isAttribute = true) String name,
            @JacksonXmlProperty(localName = "id", isAttribute = true) long id,
            @JacksonXmlProperty(localName = "ttl", isAttribute = true) Long ttl,
            @JacksonXmlProperty(localName = "dummyService", isAttribute = true) String dummyService,
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "event") List<ServiceEvent> events,
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "field") List<ServiceField> fields,
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "method") List<ServiceMethod> methods,
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "service-device") List<ServiceDevice> serviceDevices,
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "telegramRef") List<TelegramRef> telegramRefs) {
        this.name = name;
        this.id = id;
        this.ttl = ttl != null ? ttl : 0;
        this.dummyService = "on".equalsIgnoreCase(dummyService);
        this.events = events != null ? Collections.unmodifiableList(events) : Collections.emptyList();
        this.fields = fields != null ? Collections.unmodifiableList(fields) : Collections.emptyList();
        this.methods = methods != null ? Collections.unmodifiableList(methods) : Collections.emptyList();
        this.serviceDevices = serviceDevices != null
                ? Collections.unmodifiableList(serviceDevices) : Collections.emptyList();
        this.telegramRefs = telegramRefs != null
                ? Collections.unmodifiableList(telegramRefs) : Collections.emptyList();
    }

    public String getName() {
        return name;
    }

    public long getId() {
        return id;
    }

    public long getTtl() {
        return ttl;
    }

    public boolean isDummyService() {
        return dummyService;
    }

    public List<ServiceEvent> getEvents() {
        return events;
    }

    public List<ServiceField> getFields() {
        return fields;
    }

    public List<ServiceMethod> getMethods() {
        return methods;
    }

    public List<ServiceDevice> getServiceDevices() {
        return serviceDevices;
    }

    public List<TelegramRef> getTelegramRefs() {
        return telegramRefs;
    }

    @Override
    public String toString() {
        return String.format("ServiceDefinition{name='%s', id=%d}", name, id);
    }
}
