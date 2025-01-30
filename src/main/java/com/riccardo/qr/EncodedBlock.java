package com.riccardo.qr;

import java.util.List;

class EncodedBlock {
    public List<Integer> indices;
    public final int k;
    public final int totalBytes;
    public final long checksum;   // or use CRC32
    public byte[] data;    // The XORed block data

    public EncodedBlock(List<Integer> indices, int k, int totalBytes, int checksum, byte[] data) {
        this.indices = indices;
        this.k = k;
        this.totalBytes = totalBytes;
        this.checksum = checksum;
        this.data = data;
    }

    @Override
    public String toString() {
        return "EncodedBlock{" + "indices=" + indices + ", k=" + k + ", totalBytes=" + totalBytes + ", checksum=" + checksum + ", dataLen=" + data.length + '}';
    }
}
