import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

public class AdvancedLibrarySystem {
    private JFrame mainFrame;
    private DefaultTableModel localModel;
    private HttpServer webServer;

    private static Connection sharedConnection = null;
    private static final List<String> operationsLog = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, String> tokenToUserMap = Collections.synchronizedMap(new HashMap<>());

    public AdvancedLibrarySystem() {
        logSystemEvent("Initializing Fixed Cloud Library Application...");

        try {
            ensureDatabaseConnected();

            // SYSTEMRESET FORCE: Drops old broken schemas and sets up a flawless relational structure
            resetAndRebuildDatabaseSchema();
            seedDataInventory();
        } catch (Exception e) {
            System.err.println("Critical System Initialization Error: " + e.getMessage());
        }

        startLocalhostWebServer();

        boolean isHeadless = "true".equals(System.getProperty("java.awt.headless")) || System.getenv("PORT") != null;
        if (!isHeadless) {
            setupLocalDesktopFrame();
        } else {
            logSystemEvent("Headless execution environment configured successfully.");
        }
    }

    private static void logSystemEvent(String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String logLine = "[" + timestamp + "] " + message;
        operationsLog.add(0, logLine);
        System.out.println(logLine);
    }

    private synchronized Connection ensureDatabaseConnected() throws Exception {
        if (sharedConnection == null || sharedConnection.isClosed() || !sharedConnection.isValid(2)) {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String dbUrl = System.getenv("MYSQL_URL");
            if (dbUrl == null || dbUrl.isEmpty()) {
                dbUrl = "jdbc:mysql://localhost:3306/library_db?useSSL=false&allowPublicKeyRetrieval=true";
                sharedConnection = DriverManager.getConnection(dbUrl, "root", "password");
            } else {
                if (dbUrl.startsWith("mysql://")) {
                    dbUrl = "jdbc:" + dbUrl;
                }
                sharedConnection = DriverManager.getConnection(dbUrl);
            }
            logSystemEvent("Direct high-speed pipeline to database established.");
        }
        return sharedConnection;
    }

    private void resetAndRebuildDatabaseSchema() {
        try {
            Connection conn = ensureDatabaseConnected();
            try (Statement st = conn.createStatement()) {
                logSystemEvent("Executing explicit system structural reset drop sequences...");
                st.execute("DROP TABLE IF EXISTS books;");
                st.execute("DROP TABLE IF EXISTS users;");

                // Establish absolute state tables
                st.execute("CREATE TABLE books (" +
                        "id INT PRIMARY KEY, " +
                        "title VARCHAR(255) NOT NULL, " +
                        "status VARCHAR(50) DEFAULT 'Available', " +
                        "borrower VARCHAR(100) DEFAULT NULL, " +
                        "due_date VARCHAR(50) DEFAULT NULL);");

                st.execute("CREATE TABLE users (" +
                        "username VARCHAR(100) PRIMARY KEY, " +
                        "role VARCHAR(50) DEFAULT 'Standard Student');");

                logSystemEvent("Database completely wiped, reset, and built with clean mappings.");
            }
        } catch (Exception e) {
            System.err.println("Critical Schema Reset Failure: " + e.getMessage());
        }
    }

    private void seedDataInventory() {
        try {
            Connection conn = ensureDatabaseConnected();
            try (Statement st = conn.createStatement()) {
                st.execute("INSERT INTO users (username, role) VALUES ('admin', 'Administrator');");
                st.execute("INSERT INTO users (username, role) VALUES ('2025-200491', 'Standard Student');");

                st.execute("INSERT INTO books (id, title, status) VALUES (1001, 'Introduction to Java Cloud Design', 'Available');");
                st.execute("INSERT INTO books (id, title, status) VALUES (1002, 'Docker Containers Essentials', 'Available');");
                st.execute("INSERT INTO books (id, title, status) VALUES (1003, 'Advanced Database Distribution Architectures', 'Available');");
                st.execute("INSERT INTO books (id, title, status) VALUES (1004, 'The Great Gatsby', 'Available');");
                st.execute("INSERT INTO books (id, title, status) VALUES (1005, 'To Kill a Mockingbird', 'Available');");

                logSystemEvent("Production catalog index data successfully seeded.");
            }
        } catch (Exception e) {
            System.err.println("Seeding error fallback: " + e.getMessage());
        }
    }

    private void setupLocalDesktopFrame() {
        try {
            mainFrame = new JFrame("Advanced Server Gateway Controller");
            mainFrame.setSize(400, 200);
            mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            localModel = new DefaultTableModel(new String[]{"Service Engine Status"}, 0);
            JTable statusTable = new JTable(localModel);
            localModel.addRow(new Object[]{"HTTP Core Server Running Live..."});
            mainFrame.add(new JScrollPane(statusTable), BorderLayout.CENTER);
            mainFrame.setLocationRelativeTo(null);
            mainFrame.setVisible(true);
        } catch (Exception e) {}
    }

    private void startLocalhostWebServer() {
        try {
            String portEnv = System.getenv("PORT");
            int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;
            webServer = HttpServer.create(new InetSocketAddress(port), 0);
            webServer.createContext("/", new ApplicationRouterHandler());
            webServer.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
            webServer.start();
            logSystemEvent("Portal active on deployment gateway port: " + port);
        } catch (IOException e) {
            System.err.println("Failed to bind web socket channel: " + e.getMessage());
        }
    }

    private class ApplicationRouterHandler implements HttpHandler {

        private String getSessionUser(HttpExchange exchange) {
            List<String> cookies = exchange.getRequestHeaders().get("Cookie");
            if (cookies != null) {
                for (String cookieHeader : cookies) {
                    String[] pair = cookieHeader.split(";");
                    for (String c : pair) {
                        String[] parts = c.trim().split("=");
                        if (parts.length == 2 && "LIBRARY_USER_SESSION".equals(parts[0])) {
                            return tokenToUserMap.get(parts[1]);
                        }
                    }
                }
            }
            return null;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            Map<String, String> params = parseFormBody(exchange);

            String sessionUser = getSessionUser(exchange);

            if ("POST".equalsIgnoreCase(method)) {
                try {
                    Connection conn = ensureDatabaseConnected();

                    if ("/login".equals(path) || "/register".equals(path)) {
                        String uid = params.getOrDefault("userId", "").trim();
                        if (!uid.isEmpty()) {
                            try (PreparedStatement ps = conn.prepareStatement("SELECT role FROM users WHERE username=?")) {
                                ps.setString(1, uid);
                                ResultSet rs = ps.executeQuery();
                                if (!rs.next()) {
                                    // ADD BACK REGISTER SYSTEM: Dynamically provision user if they do not exist
                                    try (PreparedStatement insertPs = conn.prepareStatement("INSERT INTO users (username, role) VALUES (?, 'Standard Student')")) {
                                        insertPs.setString(1, uid);
                                        insertPs.executeUpdate();
                                        logSystemEvent("Registered new account ID token directly: " + uid);
                                    }
                                }
                                String token = UUID.randomUUID().toString();
                                tokenToUserMap.put(token, uid);
                                exchange.getResponseHeaders().add("Set-Cookie", "LIBRARY_USER_SESSION=" + token + "; Path=/; HttpOnly");
                                logSystemEvent("User session logged in successfully: " + uid);
                            }
                        }
                        redirect(exchange, "/");
                        return;
                    }
                    else if ("/logout".equals(path)) {
                        exchange.getResponseHeaders().add("Set-Cookie", "LIBRARY_USER_SESSION=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT");
                        redirect(exchange, "/");
                        return;
                    }
                    else if ("/borrow".equals(path)) {
                        String assetId = params.get("assetId");
                        if (sessionUser != null && assetId != null) {
                            Calendar cal = Calendar.getInstance();
                            cal.add(Calendar.DAY_OF_MONTH, 3);
                            String dueDate = new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());

                            // FORCE STRING NORMALIZATION ON UPDATE
                            try (PreparedStatement ps = conn.prepareStatement(
                                    "UPDATE books SET status='Borrowed Out', borrower=?, due_date=? WHERE id=?")) {
                                ps.setString(1, sessionUser.trim());
                                ps.setString(2, dueDate);
                                ps.setInt(3, Integer.parseInt(assetId));
                                int updated = ps.executeUpdate();
                                if (updated > 0) {
                                    logSystemEvent("Successful Borrow Event -> Asset: " + assetId + " assigned to User: " + sessionUser);
                                } else {
                                    logSystemEvent("Error matching asset entity updates for ID: " + assetId);
                                }
                            }
                        }
                        redirect(exchange, "/");
                        return;
                    }
                    else if ("/return".equals(path)) {
                        String assetId = params.get("assetId");
                        if (sessionUser != null && assetId != null) {
                            try (PreparedStatement ps = conn.prepareStatement(
                                    "UPDATE books SET status='Available', borrower=NULL, due_date=NULL WHERE id=?")) {
                                ps.setInt(1, Integer.parseInt(assetId));
                                ps.executeUpdate();
                                logSystemEvent("User '" + sessionUser + "' returned asset item ID: " + assetId);
                            }
                        }
                        redirect(exchange, "/");
                        return;
                    }
                } catch (Exception e) {
                    logSystemEvent("Gateway tracking fault error: " + e.getMessage());
                }
            }

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
            html.append("<title>High-Speed Library Access Hub</title>");
            html.append("<style>");
            html.append("body{font-family:'Segoe UI',Arial,sans-serif;margin:0;padding:0;background-color:#f4f7f9;color:#333;}");
            html.append(".navbar{background-color:#005abe;color:white;padding:15px 30px;display:flex;justify-content:space-between;align-items:center;}");
            html.append(".wrapper{max-width:1200px;margin:30px auto;padding:0 20px;} .card{background:#fff;padding:25px;border-radius:6px;box-shadow:0 2px 4px rgba(0,0,0,0.04);margin-bottom:25px;}");
            html.append("table{width:100%;border-collapse:collapse;margin-top:10px;} th,td{padding:12px 15px;text-align:left;border-bottom:1px solid #e2e8f0;font-size:14px;}");
            html.append("th{background-color:#f8fafc;color:#475569;font-weight:600;} .btn{background-color:#005abe;color:white;border:none;padding:10px 20px;border-radius:4px;cursor:pointer;font-weight:600;}");
            html.append(".btn-action{padding:6px 14px;font-size:12px;} .btn-disabled{background-color:#cbd5e1;color:#94a3b8;cursor:not-allowed;}");
            html.append(".login-container{width:400px;margin:100px auto;background:#fff;padding:30px;border-radius:8px;text-align:center;border-top:5px solid #005abe;}");
            html.append(".login-container input[type='text']{width:100%;padding:12px;margin:15px 0;box-sizing:border-box;border:1px solid #ccc;border-radius:4px;}");
            html.append("</style></head><body>");

            if (sessionUser == null) {
                html.append("<div class='login-container'><h2>Library Web Access</h2>");
                html.append("<form method='POST'>");
                html.append("<input type='text' name='userId' placeholder='Identity Token (e.g., 2025-200491)' required autocomplete='off'/>");
                html.append("<button type='submit' formaction='/login' class='btn' style='width:100%; margin-bottom:10px;'>Login Session</button>");
                // REGISTER SELECTION ADDED BACK
                html.append("<button type='submit' formaction='/register' class='btn' style='width:100%; background-color:#22c55e;'>Register New ID</button>");
                html.append("</form></div>");
            }
            else {
                html.append("<div class='navbar'><h2>Hello, ").append(sessionUser).append("! | Student Desk Portal</h2>");
                html.append("<form action='/logout' method='POST' style='margin:0;'><button type='submit' style='background:#ef4444; color:white; border:none; padding:8px 16px; border-radius:4px; cursor:pointer;'>Logout</button></form></div>");

                html.append("<div class='wrapper'>");
                html.append("<div class='card'><h3>My Active Borrowed Log</h3>");
                html.append("<table><tr><th>Asset ID</th><th>Resource Title</th><th>Due Date (3-Day Limit)</th><th>Action</th></tr>");

                int borrowedLogSize = 0;
                try {
                    Connection conn = ensureDatabaseConnected();
                    try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM books WHERE TRIM(borrower)=? ORDER BY id ASC")) {
                        ps.setString(1, sessionUser.trim());
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                borrowedLogSize++;
                                int id = rs.getInt("id");
                                html.append("<tr><td>").append(id).append("</td>");
                                html.append("<td>").append(rs.getString("title")).append("</td>");
                                html.append("<td style='color:#ef4444; font-weight:bold;'>").append(rs.getString("due_date")).append("</td>");
                                html.append("<td><form action='/return' method='POST' style='margin:0;'>");
                                html.append("<input type='hidden' name='assetId' value='").append(id).append("'/>");
                                html.append("<button type='submit' class='btn btn-action' style='background-color:#22c55e;'>Return Resource</button>");
                                html.append("</form></td></tr>");
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Active view processing crash: " + e.getMessage());
                }
                
                if (borrowedLogSize == 0) {
                    html.append("<tr><td colspan='4' style='color:grey; text-align:center;'>You have no active borrowed items.</td></tr>");
                }
                html.append("</table></div>");

                html.append("<div class='card'><h3>Global Inventory Index Catalog</h3>");
                html.append("<table><tr><th>Asset ID</th><th>Resource Title</th><th>Status</th><th>Operation</th></tr>");
                
                try {
                    Connection conn = ensureDatabaseConnected();
                    try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM books ORDER BY id ASC")) {
                        while (rs.next()) {
                            int id = rs.getInt("id");
                            String title = rs.getString("title");
                            String status = rs.getString("status");
                            
                            html.append("<tr><td>").append(id).append("</td>");
                            html.append("<td>").append(title).append("</td>");
                            html.append("<td>").append("Available".equals(status) || status == null ? "Available" : "<span style='color:red;'>Borrowed Out</span>").append("</td>");
                            html.append("<td>");
                            if ("Available".equals(status) || status == null) {
                                html.append("<form action='/borrow' method='POST' style='margin:0;'>");
                                html.append("<input type='hidden' name='assetId' value='").append(id).append("'/>");
                                html.append("<button type='submit' class='btn btn-action'>Borrow</button>");
                                html.append("</form>");
                            } else {
                                html.append("<button class='btn btn-action btn-disabled' disabled>Unavailable</button>");
                            }
                            html.append("</td></tr>");
                        }
                    }
                } catch (Exception e) {}
                html.append("</table></div></div>");
            }

            html.append("</body></html>");
            byte[] responseBytes = html.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }

        private Map<String, String> parseFormBody(HttpExchange exchange) throws IOException {
            Map<String, String> result = new HashMap<>();
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) return result;
            
            InputStream is = exchange.getRequestBody();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            String formText = bos.toString(StandardCharsets.UTF_8.name());
            if (formText.isEmpty()) return result;
            
            String[] pairs = formText.split("&");
            for (String pair : pairs) {
                String[] idx = pair.split("=");
                if (idx.length == 2) {
                    String key = URLDecoder.decode(idx[0], StandardCharsets.UTF_8.name());
                    String val = URLDecoder.decode(idx[1], StandardCharsets.UTF_8.name());
                    result.put(key, val);
                }
            }
            return result;
        }

        private void redirect(HttpExchange exchange, String targetPath) throws IOException {
            exchange.getResponseHeaders().set("Location", targetPath);
            exchange.sendResponseHeaders(303, -1);
        }
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        new AdvancedLibrarySystem();
    }
}