package com.trdp.config;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Telegram reference within a service ({@code <telegramRef>} element).
 * <p>
 * Links a service to a telegram by ComID with source and destination identifiers.
 */
public class TelegramRef {

    private final int id;
    private final long comId;
    private final long srcId;
    private final long dstId;

    public TelegramRef(
            @JacksonXmlProperty(localName = "id", isAttribute = true) int id,
            @JacksonXmlProperty(localName = "com-id", isAttribute = true) long comId,
            @JacksonXmlProperty(localName = "src-id", isAttribute = true) long srcId,
            @JacksonXmlProperty(localName = "dst-id", isAttribute = true) long dstId) {
        this.id = id;
        this.comId = comId;
        this.srcId = srcId;
        this.dstId = dstId;
    }

    public int getId() {
        return id;
    }

    public long getComId() {
        return comId;
    }

    public long getSrcId() {
        return srcId;
    }

    public long getDstId() {
        return dstId;
    }

    @Override
    public String toString() {
        return String.format("TelegramRef{id=%d, comId=%d, srcId=%d, dstId=%d}", id, comId, srcId, dstId);
    }
}
