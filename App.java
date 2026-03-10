package com.example;

import com.trdp.config.*;
import com.trdp.config.TrdpSessionFactory.ConfiguredPdSession;
import com.trdp.pd.PdEvent;
import com.trdp.pd.PdEventListener;

import java.nio.file.Path;
import java.util.Map;

public class App {
    public static void main(String[] args) throws Exception {
        DeviceConfig config = TrdpConfig.load(Path.of("trdp-config.xml"));
        BusInterface bi = config.getBusInterfaces().get(0);

        int comId = 1001;

        PdEventListener listener = new PdEventListener() {
            @Override public void onData(PdEvent event) {}
            @Override public void onTimeout(PdEvent event) {}
            @Override public void onValidityRestored(PdEvent event) {}
        };

        try (ConfiguredPdSession session = TrdpSessionFactory.configurePd(
                config, bi, listener)) {

            // Stage data — ComID 1001 uses dataset 1005
            session.putData(comId, Map.of(
                    "u64[0]", 0xCAFEBABEL,
                    "u64[1]", Long.MAX_VALUE,
                    "u64[2]", 42L));

            System.out.println("Starting session — publishing ComID " + comId);
            session.start();

            // Let it run for 1 second (should receive ~10 packets)
            Thread.sleep(1000);
            System.out.println("Shutting down.");
        }
    }
}
