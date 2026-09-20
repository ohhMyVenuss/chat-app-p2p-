package com.example.uichatp2p.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PeerInfo {
    private String username;
    private String ip;
    private int p2pPort;
    private List<String> sharedFiles;

    public PeerInfo() {}

    public PeerInfo(String username, String ip, int p2pPort, List<String> sharedFiles) {
        this.username = username;
        this.ip = ip;
        this.p2pPort = p2pPort;
        this.sharedFiles = sharedFiles;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public int getP2pPort() { return p2pPort; }
    public void setP2pPort(int p2pPort) { this.p2pPort = p2pPort; }

    public List<String> getSharedFiles() { return sharedFiles; }
    public void setSharedFiles(List<String> sharedFiles) { this.sharedFiles = sharedFiles; }

    @Override
    public String toString() {
        return username + " (" + ip + ":" + p2pPort + ")";
    }
}