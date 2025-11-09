package com.trdp.pd;

import com.trdp.network.UdpTransport;
import com.trdp.protocol.TrdpConstants;
import com.trdp.protocol.TrdpHeader;
import com.trdp.protocol.TrdpMessageType;
import com.trdp.protocol.TrdpPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;

public class PdPublisher implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(PdPublisher.class);
    
    private final UdpTransport transport;
    private final int comId;
    private final InetAddress destinationAddress;
    private final int destinationPort;
    private final AtomicInteger sequenceCounter;
    
    public PdPublisher(int comId, String destinationAddress, int destinationPort) throws IOException {
        this.comId = comId;
        this.destinationAddress = InetAddress.getByName(destinationAddress);
        this.destinationPort = destinationPort;
        this.transport = new UdpTransport();
        this.sequenceCounter = new AtomicInteger(0);
        
        logger.info("PD Publisher created for ComID {} to {}:{}", comId, destinationAddress, destinationPort);
    }
    
    public void publish(byte[] data) throws IOException {
        if (data.length > TrdpConstants.TRDP_MAX_PD_DATA_SIZE) {
            throw new IllegalArgumentException("Data size exceeds maximum PD data size");
        }
        
        TrdpHeader header = new TrdpHeader();
        header.setSequenceCounter(sequenceCounter.getAndIncrement());
        header.setMessageType(TrdpMessageType.PD);
        header.setComId(comId);
        header.setEtbTopoCnt(0);
        header.setOpTrnTopoCnt(0);
        
        TrdpPacket packet = new TrdpPacket(header, data);
        byte[] encodedPacket = packet.encode();
        
        transport.send(encodedPacket, destinationAddress, destinationPort);
        logger.debug("Published PD message: ComID={}, SeqNo={}, Size={}", comId, 
                    header.getSequenceCounter(), data.length);
    }
    
    @Override
    public void close() {
        transport.close();
        logger.info("PD Publisher closed for ComID {}", comId);
    }
}
