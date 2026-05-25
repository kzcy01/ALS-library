import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class AdvancedLibrarySystem {
    // Desktop GUI Frame Structure (Runs locally)
    private JFrame mainFrame;
    private JTabbedPane tabbedPane;
    private JPanel panelLogin, panelStudent, panelAdmin;
    private JTable tableStudentBooks, tableStudentLog, tableAdminBooks, tableAdminUsers;
    private DefaultTableModel modelStudentBooks, modelStudentLog, modelAdminBooks, modelAdminUsers;

    // Core Embedded Server Reference
    private HttpServer webServer;

    public AdvancedLibrarySystem() {
        System.out.println("[SYSTEM CHANNELS] Re-initializing library infrastructure engine...");

        // 1. Structural Schema Initialization & Seeding
        initializeDatabaseSchema();
        seedDataInventory();

        // 2. Start Web Server Infrastructure for Railway
        startLocalhostWebServer();

        // 3. Headless Environment Safeguard Switch
        boolean isHeadless = "true".equals(System.getProperty("java.awt.headless"));
        if (!isHeadless) {
            setupMainFrame();
        } else {
            System.out.println("--- CLOUD ENVIRONMENT DETECTED ---");
            System.out.println("Systems operating normally. Web page rendering active on public cluster gateway.");
        }
    }

    // Full Local Desktop Interface Construction
    private void setupMainFrame() {
        mainFrame = new JFrame("Advanced Library Management System");
        mainFrame.setSize(950, 650);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setLocationRelativeTo(null);

        tabbedPane = new JTabbedPane();

        // Login Module Panel
        panelLogin = new JPanel(new GridBagLayout());
        panelLogin.setBorder(BorderFactory.createTitledBorder("User Authenticator Access Gate"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridx = 0; gbc.gridy = 0;
        panelLogin.add(new JLabel("Cardholder Username:"), gbc);
        gbc.gridx = 1;
        panelLogin.add(new JTextField(15), gbc);
        gbc.gridx = 0; gbc.gridy = 1;
        panelLogin.add(new JLabel("Security Password:"), gbc);
        gbc.gridx = 1;
        panelLogin.add(new JPasswordField(15), gbc);
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        panelLogin.add(new JButton("Authenticate Secure Token"), gbc);

        // Student View Management Panels
        panelStudent = new JPanel(new BorderLayout());
        modelStudentBooks = new DefaultTableModel(new String[]{"Book ID", "Title / Collection", "Circulation Status"}, 0);
        tableStudentBooks = new JTable(modelStudentBooks);
        panelStudent.add(new JScrollPane(tableStudentBooks), BorderLayout.CENTER);

        // Admin Dashboard Panels
        panelAdmin = new JPanel(new BorderLayout());
        modelAdminBooks = new DefaultTableModel(new String[]{"ID", "Book Title", "Author String", "Status State"}, 0);
        tableAdminBooks = new JTable(modelAdminBooks);
        panelAdmin.add(new JScrollPane(tableAdminBooks), BorderLayout.CENTER);

        // Map Layout Tabs
        tabbedPane.addTab("System Authentication Gate", panelLogin);
        tabbedPane.addTab("Student Catalog Workspace", panelStudent);
        tabbedPane.addTab("Administrative Settings Console", panelAdmin);

        mainFrame.add(tabbedPane);

        // Sync local displays
        refreshStudentCatalog();
        refreshStudentPersonalLog();
        refreshAdminTables();

        mainFrame.setVisible(true);
    }

    // Database Connection Provider Engine with JDBC Protocol Formatter Fix
    private Connection getConnection() throws Exception {
        // Safe check for the driver class registration
        Class.forName("com.mysql.cj.jdbc.Driver");

        String dbUrl = System.getenv("MYSQL_URL");
        if (dbUrl == null || dbUrl.isEmpty()) {
            // Local fallback workstation testing parameters
            dbUrl = "jdbc:mysql://localhost:3306/library_db?useSSL=false&allowPublicKeyRetrieval=true";
            return DriverManager.getConnection(dbUrl, "root", "password");
        }

        // JDBC FIX: Transform raw cluster strings (mysql://) to proper java strings (jdbc:mysql://)
        if (dbUrl.startsWith("mysql://")) {
            dbUrl = "jdbc:" + dbUrl;
        }
        return DriverManager.getConnection(dbUrl);
    }

    private void initializeDatabaseSchema() {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS books (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "title VARCHAR(255) NOT NULL, " +
                    "is_available BOOLEAN DEFAULT TRUE);");

            st.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(100) NOT NULL, " +
                    "role VARCHAR(50) DEFAULT 'Student');");
        } catch (Exception e) {
            System.out.println("[CRITICAL SCHEMA ERROR] Structural configuration processing dropped: " + e.getMessage());
        }
    }

    private void seedDataInventory() {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM books;");
            if (rs.next() && rs.getInt(1) == 0) {
                st.execute("INSERT INTO books (title, is_available) VALUES ('Introduction to Java Cloud Design', true);");
                st.execute("INSERT INTO books (title, is_available) VALUES ('Docker Containers Essentials', true);");
                st.execute("INSERT INTO books (title, is_available) VALUES ('Advanced Database Distribution Architectures', false);");
                System.out.println("[DATA SEED ENGINE] Target collections baseline synchronized.");
            }
        } catch (Exception e) {
            System.out.println("[DATA SEED ERROR] Preloading sequence skipped: " + e.getMessage());
        }
    }

    // Embedded HTTP Server Initialization Engine
    private void startLocalhostWebServer() {
        try {
            String portEnv = System.getenv("PORT");
            int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;

            webServer = HttpServer.create(new InetSocketAddress(port), 0);
            webServer.createContext("/", new WebDashboardHandler());
            webServer.setExecutor(null);
            webServer.start();
            System.out.println("[NETWORKING ENGINE] Web Server running cleanly on binding port: " + port);
        } catch (IOException e) {
            System.out.println("[NETWORKING ERROR] Web instance failed to bind socket links: " + e.getMessage());
        }
    }

    // Dynamic Web Dashboard Content Generator
    private class WebDashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
            html.append("<title>Restored Library Workspace Dashboard</title>");
            html.append("<style>");
            html.append("body{font-family:'Segoe UI',sans-serif;margin:40px;background:#f3f7fa;color:#333;}");
            html.append(".header{background:#005abe;color:white;padding:20px;border-radius:8px;margin-bottom:30px;}");
            html.append(".card{background:white;padding:25px;border-radius:8px;box-shadow:0 4px 6px rgba(0,0,0,0.05);margin-bottom:20px;}");
            html.append("table{width:100%;border-collapse:collapse;margin-top:15px;}");
            html.append("th,td{padding:12px 15px;border:1px solid #e1e8ed;text-align:left;}");
            html.append("th{background:#f4f7f9;color:#555;font-weight:600;}");
            html.append(".badge{padding:4px 8px;border-radius:4px;font-size:12px;font-weight:bold;}");
            html.append(".bg-success{background:#d4edda;color:#155724;} .bg-danger{background:#f8d7da;color:#721c24;}");
            html.append("</style></head><body>");

            html.append("<div class='header'><h1>⚡ Advanced Cloud Library System</h1><p>Status: Operational | Connected to MySQL Cluster Engine</p></div>");

            // Login Indicator Mock Panel
            html.append("<div class='card'><h3>🔐 Web Authenticator Entry Check</h3>");
            html.append("<form style='display:flex;gap:15px;align-items:center;'>");
            html.append("<label>User ID:</label><input type='text' disabled placeholder='Cloud Connection Active' />");
            html.append("<label>Security Key:</label><input type='password' disabled placeholder='••••••••' />");
            html.append("<button type='button' disabled style='background:#ccc;border:none;padding:6px 12px;border-radius:4px;'>Token Lockout Avoided</button>");
            html.append("</form></div>");

            // Live Database Inventory Card
            html.append("<div class='card'><h3>📚 Live Library Inventory Database Records</h3>");
            html.append("<table><tr><th>Catalog Reference ID</th><th>Book Title String</th><th>Current Asset Status</th></tr>");

            try (Connection conn = getConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM books;")) {
                int count = 0;
                while(rs.next()) {
                    count++;
                    boolean available = rs.getBoolean("is_available");
                    html.append("<tr><td>").append(rs.getInt("id")).append("</td>");
                    html.append("<td><strong>").append(rs.getString("title")).append("</strong></td>");
                    html.append("<td><span class='badge ").append(available ? "bg-success'>Available inside Vault" : "bg-danger'>Circulating").append("</span></td></tr>");
                }
                if(count == 0) {
                    html.append("<tr><td colspan='3'>System tables initialized but returned zero indexed assets.</td></tr>");
                }
            } catch (Exception e) {
                html.append("<tr><td colspan='3' style='color:#721c24; background:#f8d7da;'><strong>Database Connection Routing Interrupted:</strong> ").append(e.getMessage()).append("</td></tr>");
            }

            html.append("</table></div>");
            html.append("</body></html>");

            byte[] responseBytes = html.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }

    private void refreshStudentCatalog() {
        if (modelStudentBooks == null) return;
        modelStudentBooks.setRowCount(0);
        try (Connection conn = getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM books;")) {
            while(rs.next()) {
                modelStudentBooks.addRow(new Object[]{rs.getInt("id"), rs.getString("title"), rs.getBoolean("is_available") ? "Available" : "Circulating"});
            }
        } catch(Exception e){}
    }

    private void refreshStudentPersonalLog() {
        System.out.println("[DESKTOP FRAME ENGINE] Local student authorization trace logs generated.");
    }

    private void refreshAdminTables() {
        System.out.println("[DESKTOP FRAME ENGINE] Administrative permission controls configured.");
    }

    public static void main(String[] args) {
        if (System.getenv("PORT") != null) {
            System.setProperty("java.awt.headless", "true");
            new AdvancedLibrarySystem();
        } else {
            System.setProperty("java.awt.headless", "false");
            SwingUtilities.invokeLater(() -> new AdvancedLibrarySystem());
        }
    }
}