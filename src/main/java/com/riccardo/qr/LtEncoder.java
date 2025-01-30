package com.riccardo.qr;

import java.util.*;

public class LtEncoder {

    public final List<byte[]> blocks;   // The original data slices
    public final int k;                // Number of slices
    public final int sliceSize;

    private final byte[] data;         // The entire input data
    private final Random random;

    //
    public LtEncoder(byte[] data, int sliceSize) {
        this.data = data;
        this.sliceSize = sliceSize;
        this.blocks = sliceData(data, sliceSize);
        this.k = blocks.size();
        this.random = new Random();
    }

    // Return an infinite iterator of encoded blocks
    public FountainIterator fountain() {
        return new FountainIterator();
    }

    /**
     * Slices the data into blocks of size `sliceSize`.
     * The last block may be smaller than sliceSize if data.length is not a multiple.
     */
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

    /**
     * The Ideal Soliton Distribution:
     * P(d=1) = 1/k
     * P(d=d) = 1/(d*(d-1))  for d = 2..k
     */
    private int idealSolitonDegree(int k) {
        double[] prob = new double[k];
        prob[0] = 1.0 / k;
        for (int d = 2; d <= k; d++) {
            prob[d - 1] = 1.0 / (d * (d - 1));
        }
        // Build cumulative distribution
        for (int i = 1; i < k; i++) {
            prob[i] += prob[i - 1];
        }
        double r = random.nextDouble();
        for (int i = 0; i < k; i++) {
            if (r <= prob[i]) {
                return i + 1;
            }
        }
        return k; // fallback
    }

    // XOR the selected blocks
    private byte[] xorBlocks(int[] indices) {
        byte[] result = new byte[sliceSize];
        for (int idx : indices) {
            byte[] b = blocks.get(idx);
            for (int i = 0; i < result.length; i++) {
                byte val = (i < b.length) ? b[i] : 0;
                result[i] ^= val;
            }
        }
        return result;
    }

    private class FountainIterator implements Iterator<EncodedBlock> {
        @Override
        public boolean hasNext() {
            return true; // infinite
        }

        @Override
        public EncodedBlock next() {
            // 1) Choose degree from the ideal soliton distribution
            int degree = idealSolitonDegree(k);

            // 2) Randomly pick 'degree' distinct indices
            Set<Integer> chosen = new HashSet<>();
            while (chosen.size() < degree) {
                chosen.add(random.nextInt(k));
            }

            // Efficiently create the indices list
            List<Integer> indicesList = new ArrayList<>(chosen);
            Collections.sort(indicesList); // Ensure indices are sorted

            int[] arrIndices = indicesList.stream().mapToInt(Integer::intValue).toArray();

            // 3) XOR them
            byte[] encodedData = xorBlocks(arrIndices);

            // 4) Build EncodedBlock
            return new EncodedBlock(arrIndices,
                    k,
                    data.length,
                    Arrays.hashCode(data),
                    encodedData);
        }
    }
}
