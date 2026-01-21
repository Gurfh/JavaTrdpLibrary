package com.trdp.md;

@FunctionalInterface
public interface MdRequestHandler {
    MdResponse handleRequest(MdRequest request);
}