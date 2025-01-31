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
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;

public class QrStream {

    private static final int SLICE_SIZE = 1000;
    private static final int FREQUENCY_HZ = 20;
    private static final int QR_CODE_SIZE = 600;
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

    /**
     * Read file fully into byte[].
     */
    public static byte[] readFile(String path) throws IOException {
        File f = new File(path);
        byte[] data = new byte[(int) f.length()];
        try (FileInputStream fis = new FileInputStream(f)) {
            int read = fis.read(data);
            if (read != data.length) {
                throw new IOException("Could not read entire file: " + path);
            }
        }
        return data;
    }

    private static void processEncodedBlocks(Iterator<EncodedBlock> fountain) throws InterruptedException, IOException, WriterException, NotFoundException {
        AtomicInteger blockCounter = new AtomicInteger(0);
        EncodedBlock block = fountain.next();

        // Convert this block to the same "binary" format that
        // the front-end code expects. Then base64-encode it for QR.
        byte[] blockBinary = block.toBinary();
        String base64String = encodeToBase64(blockBinary);
        // Optionally prefix with a URL or "myapp://"
        String finalData = urlPrefix + base64String;
//        int num = block.incrementAndGet();

        System.out.println("Block #"+000+", base64Len="+base64String.length());
        // -- LOGGING: block info for debugging
        int currentBlockNum = blockCounter.incrementAndGet();
        System.out.println("----------------------------------------------------");
        System.out.println("Block #" + currentBlockNum);
        System.out.println("EncodedBlock: " + block);
        System.out.println("Binary block length: " + blockBinary.length + " bytes");
        System.out.println("Base64 length:       " + base64String.length() + " chars");
        System.out.println("Final QR data:       " + (finalData.length() <= 80
                ? finalData
                : finalData.substring(0, 80) + "...(truncated)"));
        System.out.println("----------------------------------------------------");

        generateQRCode(base64String, QR_CODE_SIZE);

        // Sleep to maintain 20Hz frequency
        Thread.sleep(1000 / FREQUENCY_HZ);

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

    /**
     * Compress with Deflater (BEST_COMPRESSION).
     */
    public static byte[] compressData(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(data);
        deflater.finish();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        while (!deflater.finished()) {
            int count = deflater.deflate(buf);
            baos.write(buf, 0, count);
        }
        deflater.end();
        return baos.toByteArray();
    }

    /**
     * Must match the Vue side's "appendFileHeaderMetaToBuffer" => "readFileHeaderMetaFromBuffer":
     * <p>
     * (4 bytes little-endian) metaLength + metaBytes
     * (4 bytes little-endian) dataLength + dataBytes
     */
    public static byte[] appendMetaToBuffer(byte[] compressedData, String filePath, String contentType) throws IOException {
        Path path = Paths.get(filePath);
        String fileName = path.getFileName().toString();

        // 1) JSON meta
        String metaJson = "{\"filename\":\"" + fileName + "\",\"contentType\":\"" + contentType + "\"}";
        byte[] metaBytes = metaJson.getBytes();
        System.out.println("Meta JSON: " + metaJson);

        // 2) We combine them in a single ByteBuffer, with each chunk length in little-endian
        int totalLen = 4 + metaBytes.length + 4 + compressedData.length;
        ByteBuffer buf = ByteBuffer.allocate(totalLen).order(java.nio.ByteOrder.LITTLE_ENDIAN);

        // Write meta chunk
        buf.putInt(metaBytes.length);
        buf.put(metaBytes);

        // Write data chunk
        buf.putInt(compressedData.length);
        buf.put(compressedData);

        return buf.array();
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
        return base64String;  // Add prefix
//        return "https://qrss.netlify.app//#" + base64String;  // Add prefix
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
