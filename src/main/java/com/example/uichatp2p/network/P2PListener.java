package com.example.uichatp2p.network;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.BiConsumer;

public class P2PListener extends Thread {
    private final int port;
    private final File downloadDirectory;
    private final BiConsumer<String, String> onMessageReceived;
    private final FileReceiveCallback onFileProgress;
    private volatile boolean isRunning = true;
    private ServerSocket serverSocket;

    @FunctionalInterface
    public interface FileReceiveCallback {
        void onProgress(String sender, String fileName, long bytesReceived, long totalBytes, boolean completed);
    }

    public P2PListener(int port,
                       BiConsumer<String, String> onMessageReceived,
                       FileReceiveCallback onFileProgress) {
        this.port = port;
        this.onMessageReceived = onMessageReceived;
        this.onFileProgress = onFileProgress;

        // Trỏ cố định vào thư mục Downloads của hệ điều hành máy tính
        String userHome = System.getProperty("user.home");
        this.downloadDirectory = new File(userHome, "Downloads");
        if (!this.downloadDirectory.exists()) {
            this.downloadDirectory.mkdirs();
        }
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("[+] ServerSocket P2P đang lắng nghe tại cổng: " + port);

            while (isRunning) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handleIncomingConnection(socket)).start();
            }
        } catch (IOException e) {
            if (isRunning) {
                System.err.println("[-] Lỗi ServerSocket P2P: " + e.getMessage());
            }
        }
    }

    private void handleIncomingConnection(Socket socket) {
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {
            int messageType = dis.readInt();

            if (messageType == 1) {
                // GÓI TIN CHAT
                String sender = dis.readUTF();
                String message = dis.readUTF();
                if (onMessageReceived != null) {
                    onMessageReceived.accept(sender, message);
                }
            } else if (messageType == 2) {
                // GÓI TIN TRUYỀN FILE
                String sender = dis.readUTF();
                String originalFileName = dis.readUTF();
                long fileSize = dis.readLong();

                // Đảm bảo không ghi đè nếu file đã trùng tên trong Downloads
                File targetFile = new File(downloadDirectory, originalFileName);
                int count = 1;
                String baseName = originalFileName.contains(".") ? originalFileName.substring(0, originalFileName.lastIndexOf('.')) : originalFileName;
                String extension = originalFileName.contains(".") ? originalFileName.substring(originalFileName.lastIndexOf('.')) : "";

                while (targetFile.exists()) {
                    targetFile = new File(downloadDirectory, baseName + " (" + count + ")" + extension);
                    count++;
                }

                // Ghi dữ liệu file
                try (FileOutputStream fos = new FileOutputStream(targetFile);
                     BufferedOutputStream bos = new BufferedOutputStream(fos)) {

                    byte[] buffer = new byte[8192];
                    long totalRead = 0;
                    int read;

                    while (totalRead < fileSize &&
                            (read = dis.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalRead))) != -1) {
                        bos.write(buffer, 0, read);
                        totalRead += read;

                        if (onFileProgress != null) {
                            onFileProgress.onProgress(sender, targetFile.getName(), totalRead, fileSize, false);
                        }
                    }
                    bos.flush();

                    if (onFileProgress != null) {
                        onFileProgress.onProgress(sender, targetFile.getName(), totalRead, fileSize, true);
                    }
                    System.out.println("[*] Đã lưu file thành công tại: " + targetFile.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            System.err.println("[-] Lỗi nhận dữ liệu qua socket: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    public void stopListener() {
        this.isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}
    }
}