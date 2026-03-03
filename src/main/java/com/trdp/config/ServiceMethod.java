package com.trdp.config;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Service method definition ({@code <method>} element within a service).
 * <p>
 * Associates a method identifier with request and reply ComIDs.
 * The {@code confirm} flag indicates whether MD confirmation is required.
 */
public class ServiceMethod {

    private final int id;
    private final long comId;
    private final long replyComId;
    private final boolean confirm;
    private final String name;

    public ServiceMethod(
            @JacksonXmlProperty(localName = "id", isAttribute = true) int id,
            @JacksonXmlProperty(localName = "com-id", isAttribute = true) long comId,
            @JacksonXmlProperty(localName = "reply-com-id", isAttribute = true) long replyComId,
            @JacksonXmlProperty(localName = "confirm", isAttribute = true) String confirm,
            @JacksonXmlProperty(localName = "name", isAttribute = true) String name) {
        this.id = id;
        this.comId = comId;
        this.replyComId = replyComId;
        this.confirm = "on".equalsIgnoreCase(confirm);
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public long getComId() {
        return comId;
    }

    public long getReplyComId() {
        return replyComId;
    }

    public boolean isConfirm() {
        return confirm;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return String.format("ServiceMethod{id=%d, comId=%d, replyComId=%d}", id, comId, replyComId);
    }
}
