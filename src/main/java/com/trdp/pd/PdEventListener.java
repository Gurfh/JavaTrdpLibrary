package com.trdp.pd;

public interface PdEventListener {
    void onData(PdEvent event);
    void onTimeout(PdEvent event);
    void onValidityRestored(PdEvent event);
}
