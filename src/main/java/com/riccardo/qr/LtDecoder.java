package com.riccardo.qr;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class LtDecoder {

    private static final int SLICE_SIZE = 1000;   // Same slice size as used in encoder
    private static final int QR_CODE_SIZE = 500;  // Size of the QR codes

    private final Map<List<Integer>, byte[]> decodedSlices;  // Decoded slices mapped by their indices

    public LtDecoder() {
        decodedSlices = new HashMap<>();
    }

    // Method to decode QR code images and reconstruct the original data
    public byte[] decodeData(List<File> qrCodeFiles) throws IOException, NotFoundException {
        List<EncodedBlock> blocks = new ArrayList<>();

        for (File qrFile : qrCodeFiles) {
            EncodedBlock block = decodeQRCode(qrFile);
            blocks.add(block);
        }

        // Reconstruct the original data
        return reconstructOriginalData(blocks);
    }

    // Decode a QR code file and extract the EncodedBlock
    private EncodedBlock decodeQRCode(File qrFile) throws IOException, NotFoundException {
        BufferedImage bufferedImage = ImageIO.read(qrFile);
        LuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        Result result = new MultiFormatReader().decode(bitmap);

        String base64Data = result.getText();
        String data = base64Data.substring("https://qrss.netlify.app//#".length());

        byte[] decodedData = Base64.getDecoder().decode(data);
        return extractEncodedBlock(decodedData);
    }

    // Extract the EncodedBlock from the decoded data
    private EncodedBlock extractEncodedBlock(byte[] decodedData) throws IOException {
        // Decode metadata from the data
        ByteArrayInputStream bais = new ByteArrayInputStream(decodedData);
        DataInputStream dis = new DataInputStream(bais);

        // Read metadata length and the metadata itself
        int metadataLength = dis.readInt();
        byte[] metadataBytes = new byte[metadataLength];
        dis.readFully(metadataBytes);

        String metadataJson = new String(metadataBytes, StandardCharsets.UTF_8);
        JSONObject jsonObject = new JSONObject(metadataJson);

        // Read the encoded data block
        byte[] blockData = new byte[decodedData.length - 4 - metadataLength];
        dis.readFully(blockData);

        // Return the block
        List<Integer> indices = parseIndices(jsonObject);
        return new EncodedBlock(indices, blockData.length, blockData.length, Arrays.hashCode(blockData), blockData);
    }

    // Parse the indices from the metadata (you can adjust as needed)
    private List<Integer> parseIndices(JSONObject metadata) {
        // The indices are stored as a list of integers in metadata
        List<Integer> indices = new ArrayList<>();
        // This is just an example of how you can extract the indices (adjust as needed)
        // The metadata structure should match the encoding logic on the encoder side
        return indices;
    }

    // Reconstruct the original data from the blocks (XOR the blocks together)
    private byte[] reconstructOriginalData(List<EncodedBlock> blocks) {
        byte[] reconstructedData = new byte[blocks.get(0).data.length];

        // XOR all blocks together to get the original data
        for (EncodedBlock block : blocks) {
            for (int i = 0; i < reconstructedData.length; i++) {
                reconstructedData[i] ^= block.data[i];
            }
        }

        return reconstructedData;
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java LtDecoder <qr_code_files>...");
            return;
        }

        try {
            List<File> qrCodeFiles = new ArrayList<>();
            for (String filePath : args) {
                qrCodeFiles.add(new File(filePath));
            }

            LtDecoder decoder = new LtDecoder();
            byte[] decodedData = decoder.decodeData(qrCodeFiles);

            // You can now write the decoded data back to a file or further process it
            Files.write(Paths.get("decoded_output"), decodedData);
            System.out.println("Decoded data written to 'decoded_output'");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

