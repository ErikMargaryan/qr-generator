package com.riccardo.qr;

import java.util.*;

public class LtEncoder {
    public final List<byte[]> blocks;
    public final int k;
    public final int sliceSize;
    private final byte[] data;
    private final Random random;
    private final int checksum;

    public LtEncoder(byte[] data, int sliceSize) {
        this.data = data;
        this.sliceSize = sliceSize;
        this.blocks = sliceData(data, sliceSize);
        this.k = blocks.size();
        this.random = new Random();
        this.checksum = EncodedBlock.getChecksum(data, k);
    }

    public FountainIterator fountain() {
        return new FountainIterator();
    }

    private List<byte[]> sliceData(byte[] data, int sliceSize) {
        List<byte[]> out = new ArrayList<>();
        int offset = 0;
        while (offset < data.length) {
            int len = Math.min(sliceSize, data.length - offset);
            byte[] slice = new byte[len];
            System.arraycopy(data, offset, slice, 0, len);
            out.add(slice);
            offset += len;
        }
        return out;
    }

    private int idealSolitonDegree(int k) {
        double[] prob = new double[k];
        prob[0] = 1.0 / k;
        for (int d = 2; d <= k; d++) {
            prob[d - 1] = 1.0 / (d * (d - 1));
        }
        for (int i = 1; i < k; i++) {
            prob[i] += prob[i - 1];
        }
        double r = random.nextDouble();
        for (int i = 0; i < k; i++) {
            if (r <= prob[i]) return i + 1;
        }
        return k;
    }

    private byte[] xorBlocks(int[] idxs) {
        byte[] result = new byte[sliceSize];
        for (int idx : idxs) {
            byte[] b = blocks.get(idx);
            for (int i = 0; i < result.length; i++) {
                byte val = (i < b.length) ? b[i] : 0;
                result[i] ^= val;
            }
        }
        return result;
    }

    class FountainIterator implements Iterator<EncodedBlock> {
        public boolean hasNext() {
            return true;
        }

        public EncodedBlock next() {
            int deg = idealSolitonDegree(k);
            Set<Integer> chosen = new HashSet<>();
            while (chosen.size() < deg) {
                chosen.add(random.nextInt(k));
            }
            int[] arr = chosen.stream().mapToInt(Integer::intValue).sorted().toArray();
            byte[] xored = xorBlocks(arr);
            return new EncodedBlock(
                    arr,
                    k,
                    data.length,
                    checksum,
                    xored
            );
        }
    }
}
