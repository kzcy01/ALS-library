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
    private JFrame mainFrame;
    private JTabbedPane tabbedPane;
    private JPanel panelStudent, panelAdmin;
    private JTable tableStudentBooks;
    private DefaultTableModel modelStudentBooks;

    private HttpServer webServer;

    public AdvancedLibrarySystem() {
        System.out.println("[SYSTEM CHANNELS] Initializing library subsystems engine...");

        // 1. Initialize DB and Seed Data
        initializeDatabaseSchema();
        seedDataInventory();

        // 2. Start Web Server for Railway
        startLocalhostWebServer();

        // 3. Safe Check for Desktop vs Cloud Environment
        boolean isHeadless = "true".equals(System.getProperty("java.awt.headless"));
        if (!isHeadless) {
            setupMainFrame();
        } else {
            System.out.println("--- CLOUD ENVIRONMENT DETECTED ---");
            System.out.println("Running headlessly. Open your Railway public domain link to view the UI!");
        }
    }

    private void setupMainFrame() {
        mainFrame = new JFrame("Advanced Library Management System");
        mainFrame.setSize(900, 600);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setLocationRelativeTo(null);

        tabbedPane = new JTabbedPane();
        panelStudent = new JPanel(new BorderLayout());
        modelStudentBooks = new DefaultTableModel(new String[]{"ID", "Title", "Availability"}, 0);
        tableStudentBooks = new JTable(modelStudentBooks);
        panelStudent.add(new JScrollPane(tableStudentBooks), BorderLayout.CENTER);

        panelAdmin = new JPanel(new BorderLayout());
        tabbedPane.addTab("Student Portal", panelStudent);
        tabbedPane.addTab("Admin Dashboard", panelAdmin);
        mainFrame.add(tabbedPane);

        refreshStudentCatalog();
        mainFrame.setVisible(true);
    }

    private Connection getConnection() throws Exception {
        String dbUrl = System.getenv("MYSQL_URL");
        if (dbUrl == null || dbUrl.isEmpty()) {
            dbUrl = "jdbc:mysql://localhost:3306/library_db?useSSL=false&allowPublicKeyRetrieval=true";
            return DriverManager.getConnection(dbUrl, "root", "password");
        }
        return DriverManager.getConnection(dbUrl);
    }

    private void initializeDatabaseSchema() {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS books (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "title VARCHAR(255) NOT NULL, " +
                    "is_available BOOLEAN DEFAULT TRUE);");
            System.out.println("[DATABASE] Schema checked.");
        } catch (Exception e) {
            System.out.println("[DATABASE ERROR] Schema check failed: " + e.getMessage());
        }
    }

    private void seedDataInventory() {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM books;");
            if (rs.next() && rs.getInt(1) == 0) {
                st.execute("INSERT INTO books (title, is_available) VALUES ('Introduction to Java Cloud Design', true);");
                st.execute("INSERT INTO books (title, is_available) VALUES ('Docker Containers Essentials', true);");
                System.out.println("[DATABASE] Books seeded.");
            }
        } catch (Exception e) {
            System.out.println("[DATABASE ERROR] Seeding failed: " + e.getMessage());
        }
    }

    // This block builds the web interface that you see in your browser!
    private void startLocalhostWebServer() {
        try {
            // Railway provides a dynamic public port variable. If it's missing, default to 8080 locally.
            String portEnv = System.getenv("PORT");
            int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;

            webServer = HttpServer.create(new InetSocketAddress(port), 0);

            // Map the root url "/" to our custom web dashboard renderer
            webServer.createContext("/", new WebDashboardHandler());
            webServer.setExecutor(null);
            webServer.start();
            System.out.println("[NETWORKING] Live Library Website hosting on gateway port: " + port);
        } catch (IOException e) {
            System.out.println("[NETWORKING ERROR] Web server failed to bind: " + e.getMessage());
        }
    }

    // Renders live HTML and content straight out of your database to the web browser
    private class WebDashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
            html.append("<title>Cloud Library System</title>");
            html.append("<style>body{font-family:'Segoe UI',sans-serif;margin:40px;background:#f0f4f8;} h1{color:#005abe;} table{width:100%;border-collapse:collapse;background:white;margin-top:20px;} th,td{padding:12px;border:1px solid #ccc;text-align:left;} th{background:#005abe;color:white;}</style>");
            html.append("</head><body>");
            html.append("<h1>⚡ Live Library System Deployed on Railway</h1>");
            html.append("<p>Below is data pulled directly from your active MySQL Cloud Database instance:</p>");

            html.append("<table><tr><th>Book Database ID</th><th>Book Title</th><th>Current Inventory Status</th></tr>");

            try (Connection conn = getConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM books;")) {
                while(rs.next()) {
                    html.append("<tr><td>").append(rs.getInt("id")).append("</td>");
                    html.append("<td>").append(rs.getString("title")).append("</td>");
                    html.append("<td><strong>").append(rs.getBoolean("is_available") ? "Available inside Vault" : "Circulating").append("</strong></td></tr>");
                }
            } catch (Exception e) {
                html.append("<tr><td colspan='3'>Error reading data tables: ").append(e.getMessage()).append("</td></tr>");
            }

            html.append("</table></body></html>");

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