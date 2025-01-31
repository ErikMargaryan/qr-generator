package com.riccardo.qr;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class EncodedBlock {
    public int[] indices;
    public final int k;
    public final int totalBytes;
    public final int checksum;   // CRC32 checksum, calculated later
    public byte[] data;    // The XORed block data

    public EncodedBlock(int[] indices, int k, int totalBytes, byte[] data) {
        this.indices = indices;
        this.k = k;
        this.totalBytes = totalBytes;
//        this.checksum = getChecksum(data, k);
        this.checksum = 1161127537;
        this.data = data;
    }

    /**
     * Convert to a "binary" byte[] with the layout:
     * [degree][indices...][k][bytes][checksum][data...]
     * Each integer is 4 bytes little-endian.
     */
    public byte[] toBinary() {
        int degree = indices.length;
        int dataLength = data.length;

        int headerSize = 4 // degree
                + 4 * degree // indices
                + 4 // k
                + 4 // totalBytes
                + 4; // checksum

        ByteBuffer buf = ByteBuffer.allocate(headerSize + dataLength);
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN); // Set to little-endian
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


    private static final int POLYNOMIAL = 0xEDB88320;
    private static final int[] CRC_TABLE = new int[256];

    static {
        generateCRCTable();
    }

    // Method to generate the CRC-32 table
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
            CRC_TABLE[i] = crc & 0xFFFFFFFF;  // Ensure unsigned 32-bit result
        }
    }

    // Method to compute the checksum (CRC-32 XOR with k)
    public static int getChecksum(byte[] data, int k) {
        int crc = 0xFFFFFFFF; // Initial value

        for (byte b : data) {
            int byteValue = b & 0xFF;  // Convert signed byte to unsigned value
            crc = (crc >>> 8) ^ CRC_TABLE[(crc ^ byteValue) & 0xFF];
        }

        return (crc ^ k ^ 0xFFFFFFFF) & 0xFFFFFFFF; // Final XOR value and ensure 32-bit unsigned
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

