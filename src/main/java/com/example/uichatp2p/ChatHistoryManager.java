package com.example.uichatp2p;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChatHistoryManager {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final File downloadFolder;
    private final String myUsername;

    public ChatHistoryManager(String myUsername) {
        this.myUsername = myUsername;
        String userHome = System.getProperty("user.home");
        this.downloadFolder = new File(userHome, "Downloads");
        if (!this.downloadFolder.exists()) {
            this.downloadFolder.mkdirs();
        }
    }

    private File getHistoryFile(String partnerUsername) {
        String fileName = String.format("chat_history_%s_with_%s.txt", myUsername, partnerUsername);
        return new File(downloadFolder, fileName);
    }

    // Ghi thêm 1 dòng tin nhắn vào file (Chuẩn UTF-8)
    public synchronized void appendMessage(String partnerUsername, String sender, String content) {
        File file = getHistoryFile(partnerUsername);
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String record = String.format("[%s] [%s]: %s%n", timestamp, sender, content);

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8);
             BufferedWriter bw = new BufferedWriter(writer)) {
            bw.write(record);
            bw.flush();
        } catch (IOException e) {
            System.err.println("[-] Lỗi ghi lịch sử chat UTF-8: " + e.getMessage());
        }
    }

    // Đọc lại toàn bộ nội dung lịch sử trò chuyện khi bấm chọn người đó
    public String loadHistory(String partnerUsername) {
        File file = getHistoryFile(partnerUsername);
        if (!file.exists()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            System.err.println("[-] Lỗi đọc lịch sử chat UTF-8: " + e.getMessage());
        }
        return sb.toString();
    }
}