package com.riccardo.qr;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class EncodedBlock {
    private static final int POLYNOMIAL = 0xEDB88320;
    private static final int[] CRC_TABLE = new int[256];

    static {
        generateCRCTable();
    }

    public final int k;
    public final int totalBytes;
    public final int checksum;
    public int[] indices;
    public byte[] data;
    public EncodedBlock(int[] indices, int k, int totalBytes, int checksum, byte[] data) {
        this.indices = indices;
        this.k = k;
        this.totalBytes = totalBytes;
        this.checksum = checksum;
        this.data = data;
    }

    private static void generateCRCTable() {
        for (int i = 0; i < 256; i++) {
            int crc = i;
            for (int j = 8; j > 0; j--) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ POLYNOMIAL;
                } else {
                    crc = crc >>> 1;
                }
            }
            CRC_TABLE[i] = crc & 0xFFFFFFFF;
        }
    }

    public static int getChecksum(byte[] data, int k) {
        int crc = 0xFFFFFFFF;

        for (byte b : data) {
            int byteValue = b & 0xFF;
            crc = (crc >>> 8) ^ CRC_TABLE[(crc ^ byteValue) & 0xFF];
        }

        return (crc ^ k ^ 0xFFFFFFFF) & 0xFFFFFFFF;
    }

    public byte[] toBinary() {
        int degree = indices.length;
        int dataLength = data.length;

        int headerSize = 4
                + 4 * degree
                + 4
                + 4
                + 4;

        ByteBuffer buf = ByteBuffer.allocate(headerSize + dataLength);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(degree);
        for (int index : indices) {
            buf.putInt(index);
        }
        buf.putInt(k);
        buf.putInt(totalBytes);
        buf.putInt(checksum);
        buf.put(data);

        return buf.array();
    }

    @Override
    public String toString() {
        return "EncodedBlock{" +
                "indices=" + Arrays.toString(indices) +
                ", k=" + k +
                ", totalBytes=" + totalBytes +
                ", checksum=" + checksum +
                ", data=" + Arrays.toString(data) +
                '}';
    }
}

