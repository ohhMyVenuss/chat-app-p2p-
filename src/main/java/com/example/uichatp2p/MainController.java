package com.example.uichatp2p;

import com.example.uichatp2p.model.PeerInfo;
import com.example.uichatp2p.network.DiscoveryClient;
import com.example.uichatp2p.network.P2PClient;
import com.example.uichatp2p.network.P2PListener;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainController {

    @FXML private ListView<PeerInfo> peerListView;
    @FXML private TextArea chatArea;
    @FXML private TextField messageInput;
    @FXML private Label targetPeerLabel;
    @FXML private Label statusLabel;
    @FXML private ProgressBar fileProgressBar;

    // Các thành phần cho tính năng Tìm kiếm File
    @FXML private TextField searchFileInput;
    @FXML private ListView<String> searchResultsListView;

    private String myUsername;
    private String myIp;
    private int myP2pPort;
    private String discoveryUrl;

    private DiscoveryClient discoveryClient;
    private P2PListener p2pListener;
    private ScheduledExecutorService scheduler;
    private ChatHistoryManager historyManager;

    // Danh sách các file mà client này đang chia sẻ (an toàn đa luồng)
    private final List<String> mySharedFiles = new CopyOnWriteArrayList<>();

    public void initData(String username, String ip, int port, String discoveryUrl) {
        this.myUsername = username;
        this.myIp = ip;
        this.myP2pPort = port;
        this.discoveryUrl = discoveryUrl;
        this.discoveryClient = new DiscoveryClient(discoveryUrl);
        this.historyManager = new ChatHistoryManager(myUsername);

        // Tùy chỉnh hiển thị ListView danh bạ
        peerListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(PeerInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getUsername() + " [" + item.getIp() + ":" + item.getP2pPort() + "]");
                }
            }
        });

        // Click chọn bạn chat
        peerListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                targetPeerLabel.setText("Đang kết nối tới: " + newVal.getUsername());
                String oldChat = historyManager.loadHistory(newVal.getUsername());
                chatArea.setText(oldChat);
                chatArea.positionCaret(chatArea.getText().length());
            }
        });

        startP2PListener();

        // Định kỳ 10s đồng bộ Heartbeat kèm danh sách sharedFiles
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::syncWithDiscoveryServer, 0, 10, TimeUnit.SECONDS);
    }

    private void startP2PListener() {
        p2pListener = new P2PListener(
                myP2pPort,
                // Nhận tin nhắn chat
                (sender, message) -> Platform.runLater(() -> {
                    historyManager.appendMessage(sender, sender, message);
                    PeerInfo currentSelected = peerListView.getSelectionModel().getSelectedItem();
                    if (currentSelected != null && currentSelected.getUsername().equalsIgnoreCase(sender)) {
                        chatArea.appendText(String.format("[%s]: %s\n", sender, message));
                        chatArea.positionCaret(chatArea.getText().length());
                    } else {
                        statusLabel.setText("Có tin nhắn mới từ: " + sender);
                    }
                }),
                // Nhận file qua P2P
                (sender, fileName, cur, total, done) -> Platform.runLater(() -> {
                    fileProgressBar.setVisible(true);
                    double progress = (double) cur / total;
                    fileProgressBar.setProgress(progress);

                    if (done) {
                        statusLabel.setText("Đã nhận xong: " + fileName);

                        // 1. Thêm vào danh sách chia sẻ của mình (Node nhận trở thành Seed)
                        if (!mySharedFiles.contains(fileName)) {
                            mySharedFiles.add(fileName);
                            new Thread(this::syncWithDiscoveryServer).start(); // Cập nhật Server ngay
                        }

                        // 2. Ghi nhận vào file lịch sử chat UTF-8
                        historyManager.appendMessage(sender, sender, "[Đã gửi một tệp tin/ảnh: " + fileName + "]");

                        chatArea.appendText("[Hệ thống]: Đã lưu file '" + fileName + "' vào thư mục Downloads từ " + sender + "\n");
                        chatArea.positionCaret(chatArea.getText().length());
                        fileProgressBar.setVisible(false);
                    } else {
                        statusLabel.setText(String.format("Đang nhận %s: %.1f%%", fileName, progress * 100));
                    }
                })
        );
        p2pListener.setDaemon(true);
        p2pListener.start();
    }

    private void syncWithDiscoveryServer() {
        try {
            // GỬI KÈM DANH SÁCH mySharedFiles LÊN SERVER THAY VÌ MẢNG RỖNG
            discoveryClient.register(new PeerInfo(myUsername, myIp, myP2pPort, new ArrayList<>(mySharedFiles)));
            List<PeerInfo> peers = discoveryClient.getOnlinePeers();

            Platform.runLater(() -> {
                PeerInfo currentSelected = peerListView.getSelectionModel().getSelectedItem();
                String selectedUsername = (currentSelected != null) ? currentSelected.getUsername() : null;

                peerListView.getItems().clear();
                PeerInfo reSelectPeer = null;

                for (PeerInfo p : peers) {
                    if (!p.getUsername().equalsIgnoreCase(myUsername)) {
                        peerListView.getItems().add(p);
                        if (selectedUsername != null && p.getUsername().equalsIgnoreCase(selectedUsername)) {
                            reSelectPeer = p;
                        }
                    }
                }

                if (reSelectPeer != null) {
                    peerListView.getSelectionModel().select(reSelectPeer);
                }
            });
        } catch (Exception e) {
            Platform.runLater(() -> statusLabel.setText("Không thể kết nối tới Discovery Server!"));
        }
    }

    @FXML
    private void handleSearchFiles() {
        String keyword = searchFileInput.getText().trim();
        if (keyword.isEmpty()) return;

        new Thread(() -> {
            List<Map<String, Object>> results = discoveryClient.searchFiles(keyword);
            Platform.runLater(() -> {
                searchResultsListView.getItems().clear();
                if (results.isEmpty()) {
                    searchResultsListView.getItems().add("Không tìm thấy file nào khớp!");
                } else {
                    for (Map<String, Object> item : results) {
                        String file = (String) item.get("filename");
                        String owner = (String) item.get("owner");
                        String ip = (String) item.get("ip");
                        int port = (Integer) item.get("port");
                        searchResultsListView.getItems().add(String.format("📄 %s (tại %s @ %s:%d)", file, owner, ip, port));
                    }
                }
            });
        }).start();
    }

    @FXML
    private void handleRefreshPeers() {
        new Thread(this::syncWithDiscoveryServer).start();
    }

    @FXML
    private void handleSendMessage() {
        PeerInfo target = peerListView.getSelectionModel().getSelectedItem();
        String message = messageInput.getText().trim();

        if (target == null) {
            statusLabel.setText("Vui lòng chọn một người từ danh sách bên trái!");
            return;
        }
        if (message.isEmpty()) return;

        new Thread(() -> {
            try {
                P2PClient.sendMessage(target.getIp(), target.getP2pPort(), myUsername, message);
                Platform.runLater(() -> {
                    historyManager.appendMessage(target.getUsername(), myUsername, message);
                    chatArea.appendText("[Tôi -> " + target.getUsername() + "]: " + message + "\n");
                    chatArea.positionCaret(chatArea.getText().length());
                    messageInput.clear();
                    statusLabel.setText("Đã gửi tin nhắn.");
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("Lỗi gửi tin: " + e.getMessage()));
            }
        }).start();
    }

    @FXML
    private void handleSendFile() {
        PeerInfo target = peerListView.getSelectionModel().getSelectedItem();
        if (target == null) {
            statusLabel.setText("Vui lòng chọn người nhận file!");
            return;
        }

        Stage stage = (Stage) chatArea.getScene().getWindow();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn file gửi qua P2P");
        File file = fileChooser.showOpenDialog(stage);

        if (file == null) return;

        fileProgressBar.setVisible(true);
        fileProgressBar.setProgress(0.0);
        statusLabel.setText("Đang chuẩn bị gửi file: " + file.getName());

        new Thread(() -> {
            try {
                P2PClient.sendFile(target.getIp(), target.getP2pPort(), myUsername, file, (sent, total, completed) -> {
                    Platform.runLater(() -> {
                        double progress = (double) sent / total;
                        fileProgressBar.setProgress(progress);

                        if (completed) {
                            statusLabel.setText("Đã gửi xong: " + file.getName());

                            // 1. Bên gửi thêm file vào danh sách chia sẻ của mình
                            if (!mySharedFiles.contains(file.getName())) {
                                mySharedFiles.add(file.getName());
                                new Thread(this::syncWithDiscoveryServer).start(); // Cập nhật Server ngay
                            }

                            // 2. Ghi nhận log gửi file vào lịch sử chat
                            historyManager.appendMessage(target.getUsername(), myUsername, "[Đã gửi một tệp tin/ảnh: " + file.getName() + "]");
                            chatArea.appendText("[Hệ thống]: Đã gửi thành công file '" + file.getName() + "' tới " + target.getUsername() + "\n");
                            chatArea.positionCaret(chatArea.getText().length());
                            fileProgressBar.setVisible(false);
                        } else {
                            statusLabel.setText(String.format("Đang tải lên %s: %.1f%%", file.getName(), progress * 100));
                        }
                    });
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Lỗi truyền file: " + e.getMessage());
                    fileProgressBar.setVisible(false);
                });
            }
        }).start();
    }

    public void shutdown() {
        if (p2pListener != null) p2pListener.stopListener();
        if (scheduler != null && !scheduler.isShutdown()) scheduler.shutdownNow();
    }
}