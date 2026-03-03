package com.trdp.config;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * SDTv4 safety parameters ({@code <sdtv4-parameter>} element).
 * <p>
 * Extends SDTv2 with protocol variant selection, safety function/channel
 * identifiers and versions. Used for SDTv4-protected sources and destinations.
 */
public class Sdtv4Parameter {

    private final long smi1;
    private final long smi2;
    private final int udvMain;
    private final int udvSub;
    private final int rxPeriod;
    private final int txPeriod;
    private final int nRxsafe;
    private final int nGuard;
    private final int protoVar;
    private final int safeFuncId;
    private final int safeFuncVers;
    private final int safeChannelId;
    private final int safeChannelVers;

    public Sdtv4Parameter(
            @JacksonXmlProperty(localName = "smi1", isAttribute = true) long smi1,
            @JacksonXmlProperty(localName = "smi2", isAttribute = true) Long smi2,
            @JacksonXmlProperty(localName = "udv-main", isAttribute = true) int udvMain,
            @JacksonXmlProperty(localName = "udv-sub", isAttribute = true) Integer udvSub,
            @JacksonXmlProperty(localName = "rx-period", isAttribute = true) int rxPeriod,
            @JacksonXmlProperty(localName = "tx-period", isAttribute = true) int txPeriod,
            @JacksonXmlProperty(localName = "n-rxsafe", isAttribute = true) Integer nRxsafe,
            @JacksonXmlProperty(localName = "n-guard", isAttribute = true) Integer nGuard,
            @JacksonXmlProperty(localName = "proto-var", isAttribute = true) Integer protoVar,
            @JacksonXmlProperty(localName = "safe-func-id", isAttribute = true) Integer safeFuncId,
            @JacksonXmlProperty(localName = "safe-func-vers", isAttribute = true) Integer safeFuncVers,
            @JacksonXmlProperty(localName = "safe-channel-id", isAttribute = true) Integer safeChannelId,
            @JacksonXmlProperty(localName = "safe-channel-vers", isAttribute = true) Integer safeChannelVers) {
        this.smi1 = smi1;
        this.smi2 = smi2 != null ? smi2 : 0;
        this.udvMain = udvMain;
        this.udvSub = udvSub != null ? udvSub : 0;
        this.rxPeriod = rxPeriod;
        this.txPeriod = txPeriod;
        this.nRxsafe = nRxsafe != null ? nRxsafe : 3;
        this.nGuard = nGuard != null ? nGuard : 100;
        this.protoVar = protoVar != null ? protoVar : 2;
        this.safeFuncId = safeFuncId != null ? safeFuncId : 0;
        this.safeFuncVers = safeFuncVers != null ? safeFuncVers : 0;
        this.safeChannelId = safeChannelId != null ? safeChannelId : 0;
        this.safeChannelVers = safeChannelVers != null ? safeChannelVers : 0;
    }

    public long getSmi1() {
        return smi1;
    }

    public long getSmi2() {
        return smi2;
    }

    public int getUdvMain() {
        return udvMain;
    }

    public int getUdvSub() {
        return udvSub;
    }

    public int getRxPeriod() {
        return rxPeriod;
    }

    public int getTxPeriod() {
        return txPeriod;
    }

    public int getNRxsafe() {
        return nRxsafe;
    }

    public int getNGuard() {
        return nGuard;
    }

    public int getProtoVar() {
        return protoVar;
    }

    public int getSafeFuncId() {
        return safeFuncId;
    }

    public int getSafeFuncVers() {
        return safeFuncVers;
    }

    public int getSafeChannelId() {
        return safeChannelId;
    }

    public int getSafeChannelVers() {
        return safeChannelVers;
    }

    @Override
    public String toString() {
        return String.format("Sdtv4Parameter{smi1=%d, smi2=%d, udvMain=%d}", smi1, smi2, udvMain);
    }
}
