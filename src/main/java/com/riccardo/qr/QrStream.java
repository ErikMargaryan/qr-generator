package com.riccardo.qr;

import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.Deflater;

public class QrStream {

    private static final int SLICE_SIZE = 1000;
    private static final int FREQUENCY_HZ = 20;
    private static final int QR_CODE_SIZE = 500;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java QrStream <file_path>");
            return;
        }

        try {
            String filePath = args[0];
            byte[] fileData = readFile(filePath);
//            byte[] compressedData = compressData(fileData);
            String contentType = guessContentType(filePath);
            byte[] dataWithMeta = appendMetaToBuffer(fileData, filePath, contentType);

            // Initialize the LtEncoder
            LtEncoder encoder = new LtEncoder(dataWithMeta, SLICE_SIZE);
            Iterator<EncodedBlock> fountain = encoder.fountain();

            // Generate QR codes for encoded data
            processEncodedBlocks(fountain);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static byte[] readFile(String filePath) throws IOException {
        return Files.readAllBytes(Paths.get(filePath));
    }

    private static void processEncodedBlocks(Iterator<EncodedBlock> fountain) throws InterruptedException, IOException, WriterException, NotFoundException {
        int maxSlices = 1; // Set the maximum number of QR codes to generate
        int index = 0;

        while (fountain.hasNext() && index < maxSlices) {
            EncodedBlock encodedBlock = fountain.next();
            System.out.println("Encoded Data: " + Arrays.toString(encodedBlock.data));

            String base64String = encodeToBase64(encodedBlock.data);
            generateQRCode(base64String, index);

            // Sleep to maintain 20Hz frequency
            Thread.sleep(1000 / FREQUENCY_HZ);
            index++;
        }
    }

    // Guess content type of the file
    public static String guessContentType(String filename) {
        try {
            String probe = URLConnection.guessContentTypeFromName(filename);
            if (probe != null) return probe;
        } catch (Exception ignored) {}

        return getFileExtensionContentType(filename);
    }

    private static String getFileExtensionContentType(String filename) {
        int idx = filename.lastIndexOf('.');
        String ext = (idx > 0) ? filename.substring(idx + 1).toLowerCase() : "";

        switch (ext) {
            case "txt": return "text/plain";
            case "png": return "image/png";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "pdf": return "application/pdf";
            default: return "application/octet-stream";
        }
    }

    // Simple compression with Deflater
    public static byte[] compressData(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(data);
        deflater.finish();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            baos.write(buffer, 0, count);
        }
        deflater.end();

        return baos.toByteArray();
    }

    // Append metadata to the data
    public static byte[] appendMetaToBuffer(byte[] data, String filePath, String contentType) throws IOException {
        File file = new File(filePath);
        String fileName = file.getName();
        String metaJson = "{\"Filename\":\"" + fileName + "\",\"Content-Type\":\"" + contentType + "\"}";
        byte[] metaBytes = metaJson.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeInt(baos, metaBytes.length);
        baos.write(metaBytes);
        baos.write(data);
        return baos.toByteArray();
    }

    private static void writeInt(ByteArrayOutputStream baos, int value) throws IOException {
        baos.write((value >>> 24) & 0xFF);
        baos.write((value >>> 16) & 0xFF);
        baos.write((value >>> 8) & 0xFF);
        baos.write(value & 0xFF);
    }

    // Encode slice to Base64 with URL prefix
    private static String encodeToBase64(byte[] data) {
        String base64String = Base64.getEncoder().encodeToString(data);
        return "https://qrss.netlify.app//#" + base64String;  // Add prefix
    }

    // Generate a QR code image from a Base64-encoded string
    private static void generateQRCode(String data, int sliceIndex) throws IOException, WriterException, NotFoundException {
        Map<EncodeHintType, Object> hintMap = new HashMap<>();
        hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.Q);
//        hintMap.put(EncodeHintType.MARGIN, 1);
        hintMap.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        MultiFormatWriter writer = new MultiFormatWriter();
        BitMatrix bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE, hintMap);

        BufferedImage image = new BufferedImage(QR_CODE_SIZE, QR_CODE_SIZE, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < QR_CODE_SIZE; i++) {
            for (int j = 0; j < QR_CODE_SIZE; j++) {
                image.setRGB(i, j, bitMatrix.get(i, j) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }

        String fileName = "QRCode_Slice_" + sliceIndex + ".png";
        ImageIO.write(image, "png", new File(fileName));
        System.out.println("QR code saved to: " + fileName);
    }
}