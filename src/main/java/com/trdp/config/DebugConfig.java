package com.trdp.config;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Debug configuration ({@code <debug>} element).
 * <p>
 * Specifies the debug log file name, size, log level (D/E/I/W), and
 * info categories (A/D/F/C).
 * <p>
 * Default: file-size=65536.
 */
public class DebugConfig {

    private final String fileName;
    private final long fileSize;
    private final String level;
    private final String info;

    public DebugConfig(
            @JacksonXmlProperty(localName = "file-name", isAttribute = true) String fileName,
            @JacksonXmlProperty(localName = "file-size", isAttribute = true) Long fileSize,
            @JacksonXmlProperty(localName = "level", isAttribute = true) String level,
            @JacksonXmlProperty(localName = "info", isAttribute = true) String info) {
        this.fileName = fileName;
        this.fileSize = fileSize != null ? fileSize : 65536;
        this.level = level;
        this.info = info;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getLevel() {
        return level;
    }

    public String getInfo() {
        return info;
    }

    @Override
    public String toString() {
        return String.format("DebugConfig{fileName='%s', fileSize=%d, level='%s', info='%s'}",
                fileName, fileSize, level, info);
    }
}
