package com.example.BPA_project.service;

import java.util.Base64;
import org.springframework.util.StringUtils;

public class VisualAsset {

    private final String label;
    private final String mediaType;
    private final byte[] data;

    public VisualAsset(String label, String mediaType, byte[] data) {
        this.label = StringUtils.hasText(label) ? label.trim() : "Visual asset";
        this.mediaType = StringUtils.hasText(mediaType) ? mediaType.trim() : "image/png";
        this.data = data == null ? new byte[0] : data.clone();
    }

    public String getLabel() {
        return label;
    }

    public String getMediaType() {
        return mediaType;
    }

    public byte[] getData() {
        return data.clone();
    }

    public boolean hasData() {
        return data.length > 0;
    }

    public String toDataUrl() {
        return "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(data);
    }
}