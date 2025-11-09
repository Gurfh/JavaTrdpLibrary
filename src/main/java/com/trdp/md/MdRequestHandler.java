package com.trdp.md;

@FunctionalInterface
public interface MdRequestHandler {
    byte[] handleRequest(int comId, byte[] requestData);
}
