package com.riccardo.qr;

import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.zip.Deflater;

public class QrStream {

    private static final int SLICE_SIZE = 1000;
    private static final int FREQUENCY_HZ = 20;
    private static final int QR_CODE_SIZE = 500;
    private static final String urlPrefix = "https://qrss.netlify.app//#";

    private static JFrame frame;
    private static JLabel label;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java QrStream <file_path>");
            return;
        }

        try {
            initializeFrame();

            String filePath = args[0];
            byte[] fileData = readFile(filePath);
            byte[] compressedData = compressData(fileData);
            String contentType = guessContentType(filePath);
            byte[] dataWithMeta = appendMetaToBuffer(compressedData, filePath, contentType);

            LtEncoder encoder = new LtEncoder(dataWithMeta, SLICE_SIZE);
            Iterator<EncodedBlock> fountain = encoder.fountain();

            processEncodedBlocks(fountain);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void initializeFrame() {
        frame = new JFrame("QR Code");
        label = new JLabel();
        frame.getContentPane().add(label);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

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
        while (true) {
            EncodedBlock block = fountain.next();

            byte[] blockBinary = block.toBinary();
            String base64String = encodeToBase64(blockBinary);

            generateQRCode(base64String, QR_CODE_SIZE);

            Thread.sleep(1000 / FREQUENCY_HZ);
        }

    }

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

    public static byte[] compressData(byte[] data) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Deflater deflater = new Deflater(Deflater.NO_FLUSH, true);
            deflater.setInput(data);
            deflater.finish();

            byte[] buf = new byte[4096];
            while (!deflater.finished()) {
                int count = deflater.deflate(buf);
                baos.write(buf, 0, count);
            }
            deflater.end();
            return baos.toByteArray();
        }
    }

    public static byte[] appendMetaToBuffer(byte[] compressedData, String filePath, String contentType) throws IOException {
        String fileName = Paths.get(filePath).getFileName().toString();

        String metaJson = "{\"filename\":\"" + fileName + "\",\"contentType\":\"" + contentType + "\"}";
        byte[] metaBytes = metaJson.getBytes(StandardCharsets.UTF_8);

        try {
            int totalLen = 4 + metaBytes.length + 4 + compressedData.length;
            ByteBuffer buf = ByteBuffer.allocate(totalLen).order(ByteOrder.BIG_ENDIAN);

            buf.putInt(metaBytes.length);
            buf.put(metaBytes);

            buf.putInt(compressedData.length);
            buf.put(compressedData);

            return buf.array();
        } catch (Exception e) {
            throw new IOException("Error appending meta to buffer", e);
        }
    }

    private static String encodeToBase64(byte[] data) {
        String base64String = Base64.getEncoder().encodeToString(data);
        return urlPrefix + base64String;
    }

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

        label.setIcon(new ImageIcon(image));
        frame.pack();

        System.out.println("QR code displayed on screen.");
    }

}