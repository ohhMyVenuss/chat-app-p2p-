package com.example.uichatp2p.network;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;

public class P2PClient {

    @FunctionalInterface
    public interface FileSendCallback {
        void onProgress(long bytesSent, long totalBytes, boolean completed);
    }

    // 1. Gửi tin nhắn Text trực tiếp
    public static void sendMessage(String targetIp, int targetPort, String sender, String message) throws IOException {
        try (Socket socket = new Socket()) {
            // Thiết lập timeout kết nối 5 giây
            socket.connect(new InetSocketAddress(targetIp, targetPort), 5000);

            try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
                dos.writeInt(1); // 1 = CHAT
                dos.writeUTF(sender);
                dos.writeUTF(message);
                dos.flush();
            }
        }
    }

    // 2. Gửi File nhị phân trực tiếp theo Chunk 8192 bytes
    public static void sendFile(String targetIp, int targetPort, String sender, File file, FileSendCallback callback) throws IOException {
        if (!file.exists() || !file.isFile()) {
            throw new FileNotFoundException("File không tồn tại: " + file.getAbsolutePath());
        }

        long fileSize = file.length();

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(targetIp, targetPort), 5000);

            try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                 FileInputStream fis = new FileInputStream(file);
                 BufferedInputStream bis = new BufferedInputStream(fis)) {

                // Ghi Header
                dos.writeInt(2); // 2 = FILE
                dos.writeUTF(sender);
                dos.writeUTF(file.getName());
                dos.writeLong(fileSize);
                dos.flush();

                // Ghi Data Body theo từng chunk
                byte[] buffer = new byte[8192];
                int read;
                long totalSent = 0;

                while ((read = bis.read(buffer)) != -1) {
                    dos.write(buffer, 0, read);
                    totalSent += read;

                    if (callback != null) {
                        callback.onProgress(totalSent, fileSize, false);
                    }
                }
                dos.flush();

                if (callback != null) {
                    callback.onProgress(totalSent, fileSize, true);
                }
            }
        }
    }
}