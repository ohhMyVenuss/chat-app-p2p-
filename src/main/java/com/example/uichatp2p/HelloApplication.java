package com.example.uichatp2p;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.util.Optional;

public class HelloApplication extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        // 1. Hiển thị Dialog thiết lập thông số khi chạy app
        Dialog<ClientConfig> dialog = new Dialog<>();
        dialog.setTitle("Cấu hình P2P Client");
        dialog.setHeaderText("Nhập thông tin kết nối");

        ButtonType loginButtonType = new ButtonType("Vào Chat", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField usernameField = new TextField("alice");
//        TextField ipField = new TextField("192.168.5.5"); // IP Wi-Fi thực tế của máy
        TextField ipField = new TextField(getLocalLanIp());
        TextField portField = new TextField("9001");
        TextField serverUrlField = new TextField("http://192.168.5.5:8080/api/peers");

        grid.add(new Label("Tên người dùng:"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(new Label("IP máy này:"), 0, 1);
        grid.add(ipField, 1, 1);
        grid.add(new Label("Cổng P2P:"), 0, 2);
        grid.add(portField, 1, 2);
        grid.add(new Label("Discovery Server:"), 0, 3);
        grid.add(serverUrlField, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return new ClientConfig(
                        usernameField.getText().trim(),
                        ipField.getText().trim(),
                        Integer.parseInt(portField.getText().trim()),
                        serverUrlField.getText().trim()
                );
            }
            return null;
        });

        Optional<ClientConfig> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return; // Người dùng bấm Hủy
        }

        ClientConfig config = result.get();

        // 2. Tải giao diện FXML chính
        FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("main-view.fxml"));
        Scene scene = new Scene(loader.load(), 850, 550);

        MainController controller = loader.getController();
        controller.initData(config.username, config.ip, config.port, config.serverUrl);

        stage.setTitle("P2P Hybrid Chat - [" + config.username + " @ Port " + config.port + "]");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            controller.shutdown();
            System.exit(0);
        });
        stage.show();
    }

    private record ClientConfig(String username, String ip, int port, String serverUrl) {}
    // Hàm lấy IPv4 mạng LAN thật (bỏ qua card mạng ảo VirtualBox/VMware/Loopback)
    private String getLocalLanIp() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface iface = interfaces.nextElement();
                // Bỏ qua card mạng ảo, card tắt, loopback
                if (iface.isLoopback() || !iface.isUp() || iface.getDisplayName().toLowerCase().contains("virtual") || iface.getDisplayName().toLowerCase().contains("vmnet")) {
                    continue;
                }
                java.util.Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        // Thường dải mạng gia đình/trường học bắt đầu bằng 192.168. hoặc 10.
                        if (ip.startsWith("192.168.") || ip.startsWith("10.")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }
    public static void main(String[] args) {
        launch();
    }
}