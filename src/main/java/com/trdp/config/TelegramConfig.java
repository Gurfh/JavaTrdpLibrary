package com.trdp.config;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Telegram definition ({@code <telegram>} element) within a bus interface.
 * <p>
 * A telegram defines a single TRDP communication exchange identified by its ComID.
 * It may carry PD parameters (for cyclic process data), MD parameters (for
 * request/reply messaging), source/destination filters, and SDTv4 safety parameters.
 * <p>
 * The {@code type} attribute ("sink", "source", or "source-sink") indicates the
 * telegram direction. When {@code create} is "on", publishers/subscribers should
 * be automatically created based on the type.
 *
 * @see BusInterface#getTelegrams()
 */
public class TelegramConfig {

    private final long comId;
    private final Long dataSetId;
    private final Long comParameterId;
    private final String name;
    private final String type;
    private final boolean create;
    private final MdParameter mdParameter;
    private final PdParameter pdParameter;
    private final List<SourceConfig> sources;
    private final List<DestinationConfig> destinations;
    private final List<Sdtv4SrvInstParameter> sdtv4SrvInstParameters;

    public TelegramConfig(
            @JacksonXmlProperty(localName = "com-id", isAttribute = true) long comId,
            @JacksonXmlProperty(localName = "data-set-id", isAttribute = true) Long dataSetId,
            @JacksonXmlProperty(localName = "com-parameter-id", isAttribute = true) Long comParameterId,
            @JacksonXmlProperty(localName = "name", isAttribute = true) String name,
            @JacksonXmlProperty(localName = "type", isAttribute = true) String type,
            @JacksonXmlProperty(localName = "create", isAttribute = true) String create,
            @JacksonXmlProperty(localName = "md-parameter") MdParameter mdParameter,
            @JacksonXmlProperty(localName = "pd-parameter") PdParameter pdParameter,
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "source") List<SourceConfig> sources,
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "destination") List<DestinationConfig> destinations,
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "sdtv4-srv-inst-parameter") List<Sdtv4SrvInstParameter> sdtv4SrvInstParameters) {
        this.comId = comId;
        this.dataSetId = dataSetId;
        this.comParameterId = comParameterId;
        this.name = name;
        this.type = type;
        this.create = "on".equalsIgnoreCase(create);
        this.mdParameter = mdParameter;
        this.pdParameter = pdParameter;
        this.sources = sources != null ? Collections.unmodifiableList(sources) : Collections.emptyList();
        this.destinations = destinations != null ? Collections.unmodifiableList(destinations) : Collections.emptyList();
        this.sdtv4SrvInstParameters = sdtv4SrvInstParameters != null
                ? Collections.unmodifiableList(sdtv4SrvInstParameters) : Collections.emptyList();
    }

    public long getComId() {
        return comId;
    }

    public Long getDataSetId() {
        return dataSetId;
    }

    public Long getComParameterId() {
        return comParameterId;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public boolean isCreate() {
        return create;
    }

    public MdParameter getMdParameter() {
        return mdParameter;
    }

    public PdParameter getPdParameter() {
        return pdParameter;
    }

    public List<SourceConfig> getSources() {
        return sources;
    }

    public List<DestinationConfig> getDestinations() {
        return destinations;
    }

    public List<Sdtv4SrvInstParameter> getSdtv4SrvInstParameters() {
        return sdtv4SrvInstParameters;
    }

    @Override
    public String toString() {
        return String.format("TelegramConfig{comId=%d, name='%s', type='%s'}", comId, name, type);
    }
}
