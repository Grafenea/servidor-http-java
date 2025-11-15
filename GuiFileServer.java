import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.datatransfer.StringSelection;

import java.io.IOException;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URLDecoder;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.*;
import java.util.List;

public class GuiFileServer {

    private static HttpServer server;
    private static int port;
    private static Path baseDir;
    private static JLabel statusLabel;

    public static void main(String[] args) {
        try {
            // Configura Look and Feel moderno
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        
        try {
            detectJarDirectory();
            startServer();
            SwingUtilities.invokeLater(GuiFileServer::showWindow);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, 
                "Erro ao iniciar servidor: " + e.getMessage(),
                "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ================================================================
    // Detecta diretório do JAR
    // ================================================================
    private static void detectJarDirectory() throws Exception {
        String rawPath = GuiFileServer.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getPath();

        rawPath = URLDecoder.decode(rawPath, "UTF-8");

        if (rawPath.matches("^/[A-Za-z]:/.*")) {
            rawPath = rawPath.substring(1);
        }

        Path jarFile = Path.of(rawPath);
        baseDir = jarFile.getParent().toAbsolutePath();
        System.out.println("Diretório base detectado: " + baseDir);
    }

    // ================================================================
    // Inicia servidor
    // ================================================================
    private static void startServer() throws Exception {
        port = new Random().nextInt(65535 - 49152) + 49152;

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new FileHandler());
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();

        System.out.println("Servidor iniciado: http://localhost:" + port);
    }

    // ================================================================
    // Handler HTTP
    // ================================================================
    static class FileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            URI uri = exchange.getRequestURI();
            Path target = baseDir.resolve("." + uri.getPath()).normalize();

            if (!target.startsWith(baseDir)) {
                exchange.sendResponseHeaders(403, -1);
                return;
            }

            if (Files.isDirectory(target)) {
                StringBuilder sb = new StringBuilder(
                    "<!DOCTYPE html><html><head><meta charset='utf-8'>" +
                    "<style>body{font-family:Arial,sans-serif;margin:40px;background:#f5f5f5}" +
                    "h2{color:#333}ul{list-style:none;padding:0}" +
                    "li{margin:10px 0;padding:10px;background:white;border-radius:5px;box-shadow:0 2px 4px rgba(0,0,0,0.1)}" +
                    "a{color:#0066cc;text-decoration:none;font-size:16px}a:hover{text-decoration:underline}</style>" +
                    "</head><body><h2>Arquivos:</h2><ul>"
                );
                
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(target)) {
                    for (Path p : stream) {
                        String name = p.getFileName().toString();
                        String prefix = uri.getPath();
                        if (!prefix.endsWith("/")) prefix += "/";
                        
                        sb.append("<li><a href=\"")
                          .append(prefix)
                          .append(name)
                          .append("\">")
                          .append(name)
                          .append("</a></li>");
                    }
                }
                sb.append("</ul></body></html>");
                byte[] bytes = sb.toString().getBytes();
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
                return;
            }

            if (!Files.exists(target)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            byte[] data = Files.readAllBytes(target);
            exchange.getResponseHeaders().set("Content-Type",
                    Optional.ofNullable(Files.probeContentType(target)).orElse("application/octet-stream"));
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data);
            exchange.close();
        }
    }

    // ================================================================
    // GUI Melhorada
    // ================================================================
    private static void showWindow() {
        JFrame frame = new JFrame("Servidor de Arquivos HTTP");
        frame.setSize(700, 600);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopServer();
            }
        });

        // Painel principal com bordas
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        mainPanel.setBackground(new Color(245, 245, 245));

        // ===== HEADER =====
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(41, 128, 185));
        headerPanel.setBorder(new EmptyBorder(15, 20, 15, 20));

        JLabel titleLabel = new JLabel("Servidor HTTP Ativo");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        
        statusLabel = new JLabel("Online");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        statusLabel.setForeground(new Color(46, 204, 113));
        
        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(statusLabel, BorderLayout.EAST);

        // ===== INFO PANEL =====
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBackground(Color.WHITE);
        infoPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220)),
            new EmptyBorder(15, 15, 15, 15)
        ));

        // Diretório compartilhado
        addInfoRow(infoPanel, "Diretorio:", baseDir.toString());
        addInfoRow(infoPanel, "Porta:", String.valueOf(port));

        // ===== LOCALHOST SECTION =====
        JPanel localhostPanel = createStyledPanel("Acesso Local");
        
        String localhostUrl = "http://localhost:" + port;
        JTextField localhostField = createUrlField(localhostUrl);
        
        JPanel localhostButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        localhostButtons.setOpaque(false);
        
        JButton btnOpenLocal = createStyledButton("Abrir", new Color(52, 152, 219));
        btnOpenLocal.addActionListener(e -> openUrl(localhostUrl));
        
        JButton btnCopyLocal = createStyledButton("Copiar", new Color(149, 165, 166));
        btnCopyLocal.addActionListener(e -> {
            copyToClipboard(localhostUrl);
            JOptionPane.showMessageDialog(frame, "URL copiada!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
        });
        
        localhostButtons.add(btnOpenLocal);
        localhostButtons.add(btnCopyLocal);
        
        localhostPanel.add(localhostField);
        localhostPanel.add(localhostButtons);

        // ===== NETWORK SECTION =====
        JPanel networkPanel = createStyledPanel("Acesso pela Rede Local");
        
        List<String> ips = getIPv4List();
        
        if (ips.isEmpty()) {
            JLabel noIpLabel = new JLabel("Nenhum endereco IPv4 detectado");
            noIpLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            noIpLabel.setForeground(new Color(231, 76, 60));
            networkPanel.add(noIpLabel);
        } else {
            for (String ip : ips) {
                String url = "http://" + ip + ":" + port;
                
                JPanel ipRow = new JPanel(new BorderLayout(10, 5));
                ipRow.setOpaque(false);
                ipRow.setBorder(new EmptyBorder(5, 0, 5, 0));
                
                JTextField ipField = createUrlField(url);
                
                JPanel ipButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
                ipButtons.setOpaque(false);
                
                JButton btnOpen = createStyledButton("Abrir", new Color(52, 152, 219));
                btnOpen.addActionListener(e -> openUrl(url));
                
                JButton btnCopy = createStyledButton("Copiar", new Color(149, 165, 166));
                btnCopy.addActionListener(e -> {
                    copyToClipboard(url);
                    JOptionPane.showMessageDialog(frame, "URL copiada!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                });
                
                ipButtons.add(btnOpen);
                ipButtons.add(btnCopy);
                
                ipRow.add(ipField, BorderLayout.CENTER);
                ipRow.add(ipButtons, BorderLayout.EAST);
                
                networkPanel.add(ipRow);
            }
        }

        // ===== SCROLL PANEL =====
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(new Color(245, 245, 245));
        
        contentPanel.add(infoPanel);
        contentPanel.add(Box.createVerticalStrut(15));
        contentPanel.add(localhostPanel);
        contentPanel.add(Box.createVerticalStrut(15));
        contentPanel.add(networkPanel);
        contentPanel.add(Box.createVerticalGlue());
        
        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        // ===== FOOTER =====
        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        footerPanel.setBackground(new Color(245, 245, 245));
        
        JButton btnStop = createStyledButton("Parar Servidor", new Color(231, 76, 60));
        btnStop.setPreferredSize(new Dimension(200, 40));
        btnStop.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(frame,
                "Deseja realmente parar o servidor?",
                "Confirmar", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                stopServer();
                frame.dispose();
            }
        });
        
        footerPanel.add(btnStop);

        // ===== MONTAGEM FINAL =====
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(footerPanel, BorderLayout.SOUTH);
        
        frame.add(mainPanel);
        frame.setVisible(true);
    }

    // ================================================================
    // Funções auxiliares GUI
    // ================================================================

    private static JPanel createStyledPanel(String title) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220)),
            new EmptyBorder(15, 15, 15, 15)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(new Color(52, 73, 94));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(10));

        return panel;
    }

    private static JTextField createUrlField(String url) {
        JTextField field = new JTextField(url);
        field.setEditable(false);
        field.setFont(new Font("Consolas", Font.PLAIN, 13));
        field.setBackground(new Color(248, 249, 250));
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(206, 212, 218)),
            new EmptyBorder(8, 10, 8, 10)
        ));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        return field;
    }

    private static JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setBackground(bgColor);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(110, 32));
        
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(bgColor.darker());
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(bgColor);
            }
        });
        
        return button;
    }

    private static void addInfoRow(JPanel panel, String label, String value) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        row.setOpaque(false);
        
        JLabel lblLabel = new JLabel(label);
        lblLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblLabel.setForeground(new Color(52, 73, 94));
        
        JLabel lblValue = new JLabel(value);
        lblValue.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblValue.setForeground(new Color(127, 140, 141));
        
        row.add(lblLabel);
        row.add(lblValue);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        panel.add(row);
    }

    private static void copyToClipboard(String s) {
        Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(s), null);
    }

    private static void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (Exception ignored) {}

        try {
            new ProcessBuilder("xdg-open", url).start();
            return;
        } catch (Exception ignored) {}

        try {
            new ProcessBuilder("open", url).start();
            return;
        } catch (Exception ignored) {}

        JOptionPane.showMessageDialog(null,
            "Não foi possível abrir o navegador automaticamente.\nCopie a URL manualmente.",
            "Aviso", JOptionPane.WARNING_MESSAGE);
    }

    // ================================================================
    // Lista IPv4
    // ================================================================
    private static List<String> getIPv4List() {
        List<String> list = new ArrayList<>();

        try {
            Enumeration<NetworkInterface> ints = NetworkInterface.getNetworkInterfaces();
            while (ints.hasMoreElements()) {
                NetworkInterface iface = ints.nextElement();

                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual())
                    continue;

                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address) {
                        list.add(a.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    // ================================================================
    // Stop
    // ================================================================
    private static void stopServer() {
        System.out.println("Servidor encerrado.");
        if (server != null)
            server.stop(0);
        System.exit(0);
    }
}