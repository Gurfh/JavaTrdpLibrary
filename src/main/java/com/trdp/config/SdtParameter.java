package com.trdp.config;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * SDTv2 safety parameters ({@code <sdt-parameter>} element).
 * <p>
 * Defines Safety Message Identifiers (SMI), User Data Version (UDV),
 * reception/transmission periods, and safety thresholds for SDTv2
 * protected communication.
 */
public class SdtParameter {

    private final long smi1;
    private final long smi2;
    private final int udv;
    private final int rxPeriod;
    private final int txPeriod;
    private final int nRxsafe;
    private final int nGuard;
    private final long cmThr;
    private final int lmiMax;

    public SdtParameter(
            @JacksonXmlProperty(localName = "smi1", isAttribute = true) long smi1,
            @JacksonXmlProperty(localName = "smi2", isAttribute = true) Long smi2,
            @JacksonXmlProperty(localName = "udv", isAttribute = true) int udv,
            @JacksonXmlProperty(localName = "rx-period", isAttribute = true) int rxPeriod,
            @JacksonXmlProperty(localName = "tx-period", isAttribute = true) int txPeriod,
            @JacksonXmlProperty(localName = "n-rxsafe", isAttribute = true) Integer nRxsafe,
            @JacksonXmlProperty(localName = "n-guard", isAttribute = true) Integer nGuard,
            @JacksonXmlProperty(localName = "cm-thr", isAttribute = true) Long cmThr,
            @JacksonXmlProperty(localName = "lmi-max", isAttribute = true) Integer lmiMax) {
        this.smi1 = smi1;
        this.smi2 = smi2 != null ? smi2 : 0;
        this.udv = udv;
        this.rxPeriod = rxPeriod;
        this.txPeriod = txPeriod;
        this.nRxsafe = nRxsafe != null ? nRxsafe : 3;
        this.nGuard = nGuard != null ? nGuard : 100;
        this.cmThr = cmThr != null ? cmThr : 43;
        this.lmiMax = lmiMax != null ? lmiMax : 40;
    }

    public long getSmi1() {
        return smi1;
    }

    public long getSmi2() {
        return smi2;
    }

    public int getUdv() {
        return udv;
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

    public long getCmThr() {
        return cmThr;
    }

    public int getLmiMax() {
        return lmiMax;
    }

    @Override
    public String toString() {
        return String.format("SdtParameter{smi1=%d, smi2=%d, udv=%d}", smi1, smi2, udv);
    }
}
