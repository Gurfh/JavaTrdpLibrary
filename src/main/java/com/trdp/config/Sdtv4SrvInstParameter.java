package com.trdp.config;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * SDTv4 service instance parameters ({@code <sdtv4-srv-inst-parameter>} element).
 * <p>
 * Extends SDTv4 parameters with a per-instance identifier. Used within
 * telegrams to associate SDTv4 safety parameters with specific service instances.
 * Instance IDs must be unique within a telegram.
 */
public class Sdtv4SrvInstParameter {

    private final long instanceId;
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

    public Sdtv4SrvInstParameter(
            @JacksonXmlProperty(localName = "instance-id", isAttribute = true) long instanceId,
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
        this.instanceId = instanceId;
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

    public long getInstanceId() {
        return instanceId;
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
        return String.format("Sdtv4SrvInstParameter{instanceId=%d, smi1=%d}", instanceId, smi1);
    }
}
