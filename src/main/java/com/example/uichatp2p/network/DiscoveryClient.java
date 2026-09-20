package com.example.uichatp2p.network;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.uichatp2p.model.PeerInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class DiscoveryClient {
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public DiscoveryClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.mapper = new ObjectMapper();
    }

    // Gửi bản tin đăng ký hoặc Heartbeat lên Server
    public boolean register(PeerInfo peer) {
        try {
            String jsonBody = mapper.writeValueAsString(peer);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            System.err.println("[-] Lỗi gửi Heartbeat tới Discovery Server: " + e.getMessage());
            return false;
        }
    }

    // Lấy toàn bộ danh sách Peer đang Online
    public List<PeerInfo> getOnlinePeers() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return mapper.readValue(response.body(), new TypeReference<List<PeerInfo>>() {});
            }
        } catch (Exception e) {
            System.err.println("[-] Lỗi lấy danh sách Peer Online: " + e.getMessage());
        }
        return Collections.emptyList();
    }

    // Tra cứu thông tin địa chỉ 1 peer theo username
    public PeerInfo lookup(String username) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/" + username))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return mapper.readValue(response.body(), PeerInfo.class);
            }
        } catch (Exception e) {
            System.err.println("[-] Lỗi tra cứu peer " + username + ": " + e.getMessage());
        }
        return null;
    }
}