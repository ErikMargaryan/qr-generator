package com.riccardo.qr;

import java.nio.ByteBuffer;
import java.util.Arrays;
class EncodedBlock {
    public int[] indices;
    public final int k;
    public final int totalBytes;
    public final int checksum;   // or use CRC32
    public byte[] data;    // The XORed block data

    public EncodedBlock(int[] indices, int k, int totalBytes, int checksum, byte[] data) {
        this.indices = indices;
        this.k = k;
        this.totalBytes = totalBytes;
        this.checksum = checksum;
        this.data = data;
    }

    /**
     * Convert to a "binary" byte[] with the layout:
     * [degree][indices...][k][bytes][checksum][data...]
     * Each integer is 4 bytes big-endian.
     */
    public byte[] toBinary() {
        int degree     = indices.length;
        int dataLength = data.length;

        int headerSize = 4 // degree
                + 4 * degree // indices
                + 4 // k
                + 4 // totalBytes
                + 4; // checksum

        ByteBuffer buf = ByteBuffer.allocate(headerSize + dataLength);
        buf.putInt(degree);
        for (int index : indices) {
            buf.putInt(index);
        }
        buf.putInt(k);
        buf.putInt(totalBytes);
        buf.putInt(Integer.parseInt(String.valueOf(checksum)));
        buf.put(data);

        return buf.array();
    }

    @Override
    public String toString() {
        return "EncodedBlock{degree=" + indices.length +
                ", indices=" + Arrays.toString(indices) +
                ", k=" + k +
                ", totalBytes=" + totalBytes +
                ", checksum=" + checksum +
                ", dataLen=" + data.length + '}';
    }
}
