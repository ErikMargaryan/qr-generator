package com.riccardo.qr;

import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;

public class QrStream {

    private static final int SLICE_SIZE = 1000;
    private static final int FREQUENCY_HZ = 20;
    private static final int QR_CODE_SIZE = 500;
    private static final String urlPrefix = "";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java QrStream <file_path>");
            return;
        }

        try {
            String filePath = args[0];
            byte[] fileData = readFile(filePath);
            byte[] compressedData = compressData(fileData);
            String contentType = guessContentType(filePath);
            byte[] dataWithMeta = appendMetaToBuffer(compressedData, filePath, contentType);

            // Initialize the LtEncoder
            LtEncoder encoder = new LtEncoder(dataWithMeta, SLICE_SIZE);
            Iterator<EncodedBlock> fountain = encoder.fountain();

            // Generate QR codes for encoded data
            processEncodedBlocks(fountain);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Reads a file fully into a byte[]
    public static byte[] readFile(String filename) throws IOException {
        File file = new File(filename);
        byte[] data = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            int readBytes = fis.read(data);
            if (readBytes != data.length) {
                throw new IOException("Could not read the entire file: " + filename);
            }
        }
        return data;
    }

    private static void processEncodedBlocks(Iterator<EncodedBlock> fountain) throws InterruptedException, IOException, WriterException, NotFoundException {
        AtomicInteger blockCounter = new AtomicInteger(0);
        EncodedBlock block = fountain.next();
        if (fountain.hasNext()) {
            // Convert this block to the same "binary" format that
            // the front-end code expects. Then base64-encode it for QR.
            byte[] blockBinary = block.toBinary();
            String base64String = encodeToBase64(blockBinary);
            // Optionally prefix with a URL or "myapp://"
            String finalQRData = urlPrefix.isEmpty() ? base64String : (urlPrefix + base64String);
            // -- LOGGING: block info for debugging
            int currentBlockNum = blockCounter.incrementAndGet();
            System.out.println("----------------------------------------------------");
            System.out.println("Block #" + currentBlockNum);
            System.out.println("EncodedBlock: " + block);
            System.out.println("Binary block length: " + blockBinary.length + " bytes");
            System.out.println("Base64 length:       " + base64String.length() + " chars");
            System.out.println("Final QR data:       " + (finalQRData.length() <= 80
                    ? finalQRData
                    : finalQRData.substring(0, 80) + "...(truncated)"));
            System.out.println("----------------------------------------------------");

            generateQRCode(base64String, QR_CODE_SIZE);

            // Sleep to maintain 20Hz frequency
            Thread.sleep(1000 / FREQUENCY_HZ);
        }
    }

    // Guess content type of the file
    public static String guessContentType(String filename) {
        try {
            String probe = URLConnection.guessContentTypeFromName(filename);
            if (probe != null) return probe;
        } catch (Exception ignored) {
        }

        return getFileExtensionContentType(filename);
    }

    private static String getFileExtensionContentType(String filename) {
        int idx = filename.lastIndexOf('.');
        String ext = (idx > 0) ? filename.substring(idx + 1).toLowerCase() : "";

        switch (ext) {
            case "txt":
                return "text/plain";
            case "png":
                return "image/png";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "pdf":
                return "application/pdf";
            default:
                return "application/octet-stream";
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
    private static void generateQRCode(String data, int imageSize) throws IOException, WriterException, NotFoundException {
        Map<EncodeHintType, Object> hintMap = new HashMap<>();
        hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);

        MultiFormatWriter writer = new MultiFormatWriter();
        BitMatrix bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, imageSize, imageSize, hintMap);

        BufferedImage image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < imageSize; i++) {
            for (int j = 0; j < imageSize; j++) {
                image.setRGB(i, j, bitMatrix.get(i, j) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }

        String fileName = "QRCode_Slice_" + imageSize + ".png";
        ImageIO.write(image, "png", new File(fileName));
        System.out.println("QR code saved to: " + fileName);
    }
}
