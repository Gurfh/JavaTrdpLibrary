package com.trdp.pd;

import com.trdp.network.UdpTransport;
import com.trdp.protocol.TrdpConstants;
import com.trdp.protocol.TrdpHeader;
import com.trdp.protocol.TrdpMessageType;
import com.trdp.protocol.TrdpPacket;
import com.trdp.protocol.TrdpPdHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implements the Requester role for the TRDP Process Data (PD) Pull Pattern.
 * <p>
 * The Requester sends PD-PDU request telegrams to a Publisher to solicit a PD-PDU reply.
 * The reply is typically received by a PdSubscriber.
 */
public class PdRequester implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(PdRequester.class);

    private final UdpTransport transport;
    private final ConcurrentHashMap<Integer, AtomicInteger> sequenceCounters = new ConcurrentHashMap<>();

    private int etbTopoCnt = 0;
    private int opTrnTopoCnt = 0;

    /**
     * Creates a PD Requester.
     * @param localPort The local port to bind to. Use 0 for an ephemeral port.
     */
    public PdRequester(int localPort) throws IOException {
        this.transport = new UdpTransport(localPort);
        logger.info("PD Requester created on port {}", transport.getLocalPort());
    }

    /**
     * Sets the topology counters to be sent with the request.
     * @param etbTopoCnt The ETB topology counter
     * @param opTrnTopoCnt The Operational Train topology counter
     */
    public void setTopologyCounters(int etbTopoCnt, int opTrnTopoCnt) {
        this.etbTopoCnt = etbTopoCnt;
        this.opTrnTopoCnt = opTrnTopoCnt;
    }

    /**
     * Sends a PD Request to a Publisher with no payload.
     *
     * @param comId The ComID of the process data being requested.
     * @param destinationIp The IP address of the Publisher.
     * @param destinationPort The port of the Publisher.
     * @param replyComId The ComID to be used in the reply (0 to use the requested ComID).
     * @param replyIpAddress The IP address where the reply should be sent (e.g. multicast group or unicast IP).
     *                       If null or 0.0.0.0, the source IP of the request will be used by the Publisher.
     * @throws IOException If sending fails.
     */
    public void request(int comId, String destinationIp, int destinationPort, int replyComId, String replyIpAddress) throws IOException {
        request(comId, destinationIp, destinationPort, replyComId, replyIpAddress, null);
    }

    /**
     * Sends a PD Request to a Publisher with an optional payload.
     *
     * @param comId The ComID of the process data being requested.
     * @param destinationIp The IP address of the Publisher.
     * @param destinationPort The port of the Publisher.
     * @param replyComId The ComID to be used in the reply (0 to use the requested ComID).
     * @param replyIpAddress The IP address where the reply should be sent (e.g. multicast group or unicast IP).
     *                       If null or 0.0.0.0, the source IP of the request will be used by the Publisher.
     * @param payload Optional payload data to include in the request, or null for empty payload.
     * @throws IOException If sending fails.
     */
    public void request(int comId, String destinationIp, int destinationPort, int replyComId, String replyIpAddress, byte[] payload) throws IOException {
        if (payload != null && payload.length > TrdpConstants.TRDP_MAX_PD_DATA_SIZE) {
            throw new IllegalArgumentException("Payload size exceeds maximum PD data size");
        }

        int seq = sequenceCounters.computeIfAbsent(comId, k -> new AtomicInteger(0)).getAndIncrement();

        TrdpPdHeader header = new TrdpPdHeader();
        header.setSequenceCounter(seq);
        header.setMessageType(TrdpMessageType.PD_REQUEST);
        header.setComId(comId);
        header.setEtbTopoCnt(etbTopoCnt);
        header.setOpTrnTopoCnt(opTrnTopoCnt);

        // Request parameters
        header.setReplyComId(replyComId);
        header.setReplyIpAddress(ipToInt(replyIpAddress));

        byte[] payloadData = (payload != null) ? Arrays.copyOf(payload, payload.length) : new byte[0];
        header.setDatasetLength(payloadData.length);

        TrdpPacket packet = new TrdpPacket(header, payloadData);
        byte[] encodedPacket = packet.encode();

        InetAddress destAddr = InetAddress.getByName(destinationIp);
        transport.send(encodedPacket, destAddr, destinationPort);

        logger.debug("Sent PD Request: ComID={}, ReplyComID={}, Dest={}:{}, PayloadSize={}",
                     comId, replyComId, destinationIp, destinationPort, payloadData.length);
    }
    
    /**
     * Helper to convert IP String to int (Big Endian).
     */
    private int ipToInt(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return 0;
        }
        try {
            InetAddress inet = InetAddress.getByName(ipAddress);
            return ByteBuffer.wrap(inet.getAddress()).getInt();
        } catch (Exception e) {
            logger.warn("Invalid IP address format for reply IP: {}", ipAddress);
            return 0;
        }
    }

    @Override
    public void close() {
        transport.close();
        logger.info("PD Requester closed");
    }
}
