package com.trdp.config;

import com.trdp.md.MdReplier;
import com.trdp.md.MdRequestHandler;
import com.trdp.md.MdRequester;
import com.trdp.md.MdReply;
import com.trdp.md.MdResponse;
import com.trdp.md.MdUdpDispatcher;
import com.trdp.md.TransportProtocol;
import com.trdp.network.UdpTransport;
import com.trdp.pd.PdEventListener;
import com.trdp.pd.PdPublisherHandle;
import com.trdp.pd.PdSubscriberHandle;
import com.trdp.pd.TrdpPdSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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

    private static final Logger logger = LoggerFactory.getLogger(TrdpSessionFactory.class);
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
        PdComParameter pdCom = busInterface.getPdComParameter();
        int port = (int) pdCom.getPort();

        InetAddress bindAddress = busInterface.getHostIp() != null
                ? InetAddress.getByName(busInterface.getHostIp()) : null;
        int ttl = (int) pdCom.getTtl();
        int qos = (int) pdCom.getQos();

        TrdpPdSession session = new TrdpPdSession(port, bindAddress, ttl, qos);
        session.setTrafficShapingEnabled(busInterface.getTrdpProcess().isTrafficShaping());

        Map<Integer, PdPublisherHandle> publishers = new LinkedHashMap<>();
        Map<Integer, PdSubscriberHandle> subscribers = new LinkedHashMap<>();

        for (TelegramConfig telegram : busInterface.getTelegrams()) {
            PdParameter pd = telegram.getPdParameter();
            if (pd == null) continue;

            int comId = (int) telegram.getComId();
            String type = telegram.getType();

            // Phase 4: warn if per-telegram ComParameter differs from interface-level
            Long comParamId = telegram.getComParameterId();
            if (comParamId != null && comParamId > 0) {
                config.getComParameterById(comParamId).ifPresent(cp -> {
                    if (cp.getQos() != qos || cp.getTtl() != ttl) {
                        logger.warn("PD telegram ComID {} has com-parameter-id {} with "
                                        + "QoS={}/TTL={} differing from interface-level QoS={}/TTL={}. "
                                        + "Shared socket uses interface-level values.",
                                comId, comParamId, cp.getQos(), cp.getTtl(), qos, ttl);
                    }
                });
            }

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
                        : pdCom.getTimeoutValue();
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

    // ==================== MD Factory ====================

    /**
     * Creates a configured MD session from a bus interface definition.
     * <p>
     * Creates an {@link MdRequester} and {@link MdReplier} wired with interface-level
     * socket options (host-ip, TTL, QoS) and per-telegram overrides from
     * {@link MdParameter} and {@link ComParameter}.
     *
     * <p>
     * When {@code udp-port} equals {@code tcp-port} (the IEC 61375-2-3 standard
     * single MD port, default 17225), the requester and replier share one UDP
     * socket and an {@link MdUdpDispatcher} routes incoming packets by message
     * type. With distinct ports, each endpoint binds its own UDP socket.
     *
     * @param config       the parsed device configuration (for dataset resolution)
     * @param busInterface the bus interface to configure
     * @param handler      the request handler for the replier
     * @return a configured MD session with requester, replier, and marshaller
     * @throws IOException if socket creation fails
     */
    public static ConfiguredMdSession configureMd(
            DeviceConfig config, BusInterface busInterface,
            MdRequestHandler handler) throws IOException {

        DatasetMarshaller marshaller = DatasetMarshaller.from(config);
        MdComParameter mdCom = busInterface.getMdComParameter();

        InetAddress bindAddress = busInterface.getHostIp() != null
                ? InetAddress.getByName(busInterface.getHostIp()) : null;
        int ttl = (int) mdCom.getTtl();
        int qos = (int) mdCom.getQos();

        int udpPort = (int) mdCom.getUdpPort();
        int tcpPort = (int) mdCom.getTcpPort();
        long replyTimeoutUs = mdCom.getReplyTimeout();
        long connectTimeoutUs = mdCom.getConnectTimeout();
        long confirmTimeoutUs = mdCom.getConfirmTimeout();

        TransportProtocol defaultProtocol = "TCP".equalsIgnoreCase(mdCom.getProtocol())
                ? TransportProtocol.TCP : TransportProtocol.UDP;
        int defaultRetries = (int) mdCom.getRetries();

        MdRequester requester;
        MdReplier replier;
        MdUdpDispatcher dispatcher = null;

        if (udpPort == tcpPort) {
            // IEC-standard single MD port: share one UDP socket, dispatch by message type
            UdpTransport shared = new UdpTransport(udpPort, bindAddress, ttl,
                    UdpTransport.qosToTrafficClass(qos));
            try {
                requester = MdRequester.forSharedTransport(shared, replyTimeoutUs,
                        connectTimeoutUs, bindAddress, qos);
            } catch (IOException | RuntimeException e) {
                shared.close();
                throw e;
            }
            try {
                replier = MdReplier.forSharedTransport(shared, tcpPort, handler,
                        confirmTimeoutUs, bindAddress);
            } catch (IOException | RuntimeException e) {
                requester.close();
                shared.close();
                throw e;
            }
            dispatcher = new MdUdpDispatcher(shared, requester, replier);
            dispatcher.start();
        } else {
            requester = new MdRequester(udpPort, replyTimeoutUs, connectTimeoutUs,
                    bindAddress, ttl, qos);
            try {
                replier = new MdReplier(tcpPort, handler, confirmTimeoutUs,
                        bindAddress, ttl, qos);
            } catch (IOException | RuntimeException e) {
                requester.close();
                throw e;
            }
        }

        Map<Integer, MdTelegramConfig> telegramConfigs = new LinkedHashMap<>();

        for (TelegramConfig telegram : busInterface.getTelegrams()) {
            MdParameter md = telegram.getMdParameter();
            if (md == null) continue;

            int comId = (int) telegram.getComId();

            // Start with interface-level defaults
            long perReplyTimeout = replyTimeoutUs;
            long perConfirmTimeout = confirmTimeoutUs;
            TransportProtocol perProtocol = defaultProtocol;
            int perRetries = defaultRetries;

            // Apply per-telegram MdParameter overrides
            if (md.getReplyTimeout() > 0) {
                perReplyTimeout = md.getReplyTimeout();
            }
            if (md.getConfirmTimeout() > 0) {
                perConfirmTimeout = md.getConfirmTimeout();
            }
            if (md.getProtocol() != null && !md.getProtocol().isEmpty()) {
                perProtocol = "TCP".equalsIgnoreCase(md.getProtocol())
                        ? TransportProtocol.TCP : TransportProtocol.UDP;
            }

            // Phase 4: apply per-telegram ComParameter overrides
            Long comParamId = telegram.getComParameterId();
            if (comParamId != null && comParamId > 0) {
                config.getComParameterById(comParamId).ifPresent(cp -> {
                    if (cp.getQos() != qos || cp.getTtl() != ttl) {
                        logger.warn("MD telegram ComID {} has com-parameter-id {} with "
                                        + "QoS={}/TTL={} differing from interface-level QoS={}/TTL={}. "
                                        + "Shared UDP socket uses interface-level values; "
                                        + "TCP connections will use per-ComParameter values for new connections.",
                                comId, comParamId, cp.getQos(), cp.getTtl(), qos, ttl);
                    }
                });
                // Override retries from ComParameter
                int cpRetries = config.getComParameterById(comParamId)
                        .map(cp -> (int) cp.getRetries()).orElse(perRetries);
                perRetries = Math.min(cpRetries, 2); // clamp to 0..2
            }

            telegramConfigs.put(comId, new MdTelegramConfig(
                    perReplyTimeout, perConfirmTimeout, perProtocol, perRetries));
        }

        return new ConfiguredMdSession(requester, replier, dispatcher, marshaller, telegramConfigs);
    }

    /**
     * Per-telegram MD configuration resolved from XML.
     */
    public record MdTelegramConfig(
            long replyTimeoutUs,
            long confirmTimeoutUs,
            TransportProtocol protocol,
            int maxRetries
    ) {}

    /**
     * An MD session configured from XML with an {@link MdRequester}, {@link MdReplier},
     * a {@link DatasetMarshaller}, and per-telegram configuration.
     */
    public static class ConfiguredMdSession implements AutoCloseable {

        private final MdRequester requester;
        private final MdReplier replier;
        private final MdUdpDispatcher dispatcher; // non-null only in shared-socket mode
        private final DatasetMarshaller marshaller;
        private final Map<Integer, MdTelegramConfig> telegramConfigs;

        ConfiguredMdSession(MdRequester requester, MdReplier replier,
                            MdUdpDispatcher dispatcher,
                            DatasetMarshaller marshaller,
                            Map<Integer, MdTelegramConfig> telegramConfigs) {
            this.requester = requester;
            this.replier = replier;
            this.dispatcher = dispatcher;
            this.marshaller = marshaller;
            this.telegramConfigs = Collections.unmodifiableMap(telegramConfigs);
        }

        /**
         * Starts the replier (begins accepting requests).
         */
        public void start() {
            replier.start();
        }

        /**
         * Returns the underlying requester for advanced operations.
         */
        public MdRequester getRequester() {
            return requester;
        }

        /**
         * Returns the underlying replier for advanced operations.
         */
        public MdReplier getReplier() {
            return replier;
        }

        /**
         * Returns the dataset marshaller.
         */
        public DatasetMarshaller getMarshaller() {
            return marshaller;
        }

        /**
         * Returns per-telegram configurations, keyed by ComID.
         */
        public Map<Integer, MdTelegramConfig> getTelegramConfigs() {
            return telegramConfigs;
        }

        /**
         * Sends an MD request using per-telegram configuration.
         * <p>
         * If a dataset schema exists for the given ComID, values are auto-marshalled.
         * The per-telegram protocol, timeout, and retries from XML config are applied.
         *
         * @param comId              the communication ID
         * @param values             field name to value mapping (marshalled if schema exists)
         * @param destinationAddress the destination IP address
         * @param destinationPort    the destination port
         * @return a future that completes with the reply
         */
        public CompletableFuture<MdReply> sendRequest(int comId, Map<String, Object> values,
                                                       String destinationAddress, int destinationPort) {
            byte[] data = marshaller.hasSchema(comId)
                    ? marshaller.marshall(comId, values)
                    : new byte[0];

            MdTelegramConfig cfg = telegramConfigs.get(comId);
            if (cfg != null) {
                return requester.sendRequest(comId, data, destinationAddress, destinationPort,
                        cfg.protocol(), null, null, cfg.replyTimeoutUs(), cfg.maxRetries());
            }
            return requester.sendRequest(comId, data, destinationAddress, destinationPort);
        }

        /**
         * Sends an MD request with raw byte payload using per-telegram configuration.
         *
         * @param comId              the communication ID
         * @param data               the raw payload bytes
         * @param destinationAddress the destination IP address
         * @param destinationPort    the destination port
         * @return a future that completes with the reply
         */
        public CompletableFuture<MdReply> sendRequest(int comId, byte[] data,
                                                       String destinationAddress, int destinationPort) {
            MdTelegramConfig cfg = telegramConfigs.get(comId);
            if (cfg != null) {
                return requester.sendRequest(comId, data, destinationAddress, destinationPort,
                        cfg.protocol(), null, null, cfg.replyTimeoutUs(), cfg.maxRetries());
            }
            return requester.sendRequest(comId, data, destinationAddress, destinationPort);
        }

        /**
         * Returns the shared-socket dispatcher, or {@code null} when the
         * requester and replier use separate UDP sockets (distinct ports).
         */
        public MdUdpDispatcher getDispatcher() {
            return dispatcher;
        }

        @Override
        public void close() {
            if (dispatcher != null) {
                dispatcher.close();
            }
            requester.close();
            replier.close();
        }
    }
}
