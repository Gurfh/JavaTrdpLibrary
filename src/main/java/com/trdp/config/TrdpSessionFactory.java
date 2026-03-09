package com.trdp.config;

import com.trdp.pd.PdEventListener;
import com.trdp.pd.PdPublisherHandle;
import com.trdp.pd.PdSubscriberHandle;
import com.trdp.pd.TrdpPdSession;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Factory that creates fully configured TRDP sessions from a parsed
 * {@link DeviceConfig}. Wires telegram definitions into publishers and
 * subscribers with automatic dataset marshalling.
 * <p>
 * Usage:
 * <pre>{@code
 * DeviceConfig config = TrdpConfig.load(Path.of("trdp-config.xml"));
 * BusInterface bi = config.getBusInterfaces().get(0);
 *
 * try (ConfiguredPdSession session = TrdpSessionFactory.configurePd(config, bi, listener)) {
 *     session.start();
 *     session.putData(1000, Map.of("speed", 42L, "doorOpen", true));
 * }
 * }</pre>
 *
 * @see TrdpConfig
 * @see DatasetMarshaller
 */
public class TrdpSessionFactory {

    private static final String DEFAULT_MULTICAST = "239.255.0.1";

    private TrdpSessionFactory() {
    }

    /**
     * Creates a configured PD session from a bus interface definition.
     * <p>
     * Iterates all PD telegrams in the bus interface and registers publishers
     * (type "source"), subscribers (type "sink"), or both (type "source-sink").
     * The returned session is ready to {@link ConfiguredPdSession#start()}.
     *
     * @param config       the parsed device configuration (for dataset resolution)
     * @param busInterface the bus interface to configure
     * @param listener     the event listener for all subscribers
     * @return a configured session with publishers, subscribers, and a dataset marshaller
     * @throws IOException if socket or multicast group operations fail
     */
    public static ConfiguredPdSession configurePd(
            DeviceConfig config, BusInterface busInterface,
            PdEventListener listener) throws IOException {

        DatasetMarshaller marshaller = DatasetMarshaller.from(config);
        int port = (int) busInterface.getPdComParameter().getPort();
        TrdpPdSession session = new TrdpPdSession(port);

        Map<Integer, PdPublisherHandle> publishers = new LinkedHashMap<>();
        Map<Integer, PdSubscriberHandle> subscribers = new LinkedHashMap<>();

        for (TelegramConfig telegram : busInterface.getTelegrams()) {
            PdParameter pd = telegram.getPdParameter();
            if (pd == null) continue;

            int comId = (int) telegram.getComId();
            String type = telegram.getType();

            boolean isSource = "source".equalsIgnoreCase(type)
                    || "source-sink".equalsIgnoreCase(type);
            boolean isSink = "sink".equalsIgnoreCase(type)
                    || "source-sink".equalsIgnoreCase(type);

            if (isSource) {
                String destIp = telegram.getDestinations().isEmpty()
                        ? DEFAULT_MULTICAST
                        : telegram.getDestinations().get(0).getUri();
                if (destIp == null || destIp.isEmpty()) {
                    destIp = DEFAULT_MULTICAST;
                }
                PdPublisherHandle pub = session.addPublisher(comId, destIp, port, pd.getCycle());
                publishers.put(comId, pub);
            }

            if (isSink) {
                String srcIp = telegram.getSources().isEmpty()
                        ? DEFAULT_MULTICAST
                        : telegram.getSources().get(0).getUri1();
                if (srcIp == null || srcIp.isEmpty()) {
                    srcIp = DEFAULT_MULTICAST;
                }
                long timeout = pd.getTimeout() > 0
                        ? pd.getTimeout()
                        : busInterface.getPdComParameter().getTimeoutValue();
                PdSubscriberHandle sub = session.addSubscriber(comId, srcIp, timeout, listener);
                subscribers.put(comId, sub);
            }
        }

        return new ConfiguredPdSession(session, marshaller, publishers, subscribers);
    }

    /**
     * A PD session configured from XML with registered publishers, subscribers,
     * and a {@link DatasetMarshaller} for automatic payload encoding/decoding.
     */
    public static class ConfiguredPdSession implements AutoCloseable {

        private final TrdpPdSession session;
        private final DatasetMarshaller marshaller;
        private final Map<Integer, PdPublisherHandle> publishers;
        private final Map<Integer, PdSubscriberHandle> subscribers;

        ConfiguredPdSession(TrdpPdSession session, DatasetMarshaller marshaller,
                            Map<Integer, PdPublisherHandle> publishers,
                            Map<Integer, PdSubscriberHandle> subscribers) {
            this.session = session;
            this.marshaller = marshaller;
            this.publishers = Collections.unmodifiableMap(publishers);
            this.subscribers = Collections.unmodifiableMap(subscribers);
        }

        /**
         * Starts the session (receive thread and cyclic send scheduler).
         */
        public void start() {
            session.start();
        }

        /**
         * Stages raw byte data for the given ComID publisher.
         *
         * @param comId the communication ID of the publisher
         * @param data  the raw byte payload
         * @throws IllegalArgumentException if no publisher is registered for the ComID
         */
        public void putData(int comId, byte[] data) {
            PdPublisherHandle pub = publishers.get(comId);
            if (pub == null) {
                throw new IllegalArgumentException("No publisher for ComID " + comId);
            }
            pub.putData(data);
        }

        /**
         * Marshalls field values into a binary payload and stages it for the
         * given ComID publisher. Fields are encoded in dataset schema order;
         * missing fields default to zero.
         *
         * @param comId  the communication ID of the publisher
         * @param values field name to value mapping
         * @throws IllegalArgumentException if no publisher or dataset is registered for the ComID
         */
        public void putData(int comId, Map<String, Object> values) {
            PdPublisherHandle pub = publishers.get(comId);
            if (pub == null) {
                throw new IllegalArgumentException("No publisher for ComID " + comId);
            }
            pub.putData(marshaller.marshall(comId, values));
        }

        /**
         * Returns the dataset marshaller for manual encode/decode operations.
         */
        public DatasetMarshaller getMarshaller() {
            return marshaller;
        }

        /**
         * Returns the underlying session for advanced operations.
         */
        public TrdpPdSession getSession() {
            return session;
        }

        /**
         * Returns all registered publishers, keyed by ComID.
         */
        public Map<Integer, PdPublisherHandle> getPublishers() {
            return publishers;
        }

        /**
         * Returns all registered subscribers, keyed by ComID.
         */
        public Map<Integer, PdSubscriberHandle> getSubscribers() {
            return subscribers;
        }

        @Override
        public void close() throws Exception {
            session.close();
        }
    }
}
