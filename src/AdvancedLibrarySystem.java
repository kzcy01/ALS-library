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
import java.util.regex.Pattern;

public class AdvancedLibrarySystem {
    private JFrame mainFrame;
    private DefaultTableModel localModel;
    private HttpServer webServer;

    private static Connection sharedConnection = null;
    private static final List<String> operationsLog = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, String> tokenToUserMap = Collections.synchronizedMap(new HashMap<>());

    // REGEX VALIDATION CONSTRAINT: Enforces strict XXXX-XXXXXX numerical requirement
    private static final Pattern STUDENT_ID_PATTERN = Pattern.compile("^\\d{4}-\\d{6}$");

    public AdvancedLibrarySystem() {
        logSystemEvent("Initializing Advanced Library Suite with Realtime Audit Logs...");

        try {
            ensureDatabaseConnected();
            initializeAndRepairDatabaseSchema();
            seedDataInventory();
        } catch (Exception e) {
            System.err.println("Critical System Initialization Error: " + e.getMessage());
        }

        startLocalhostWebServer();

        boolean isHeadless = "true".equals(System.getProperty("java.awt.headless")) || System.getenv("PORT") != null;
        if (!isHeadless) {
            setupLocalDesktopFrame();
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
        }
        return sharedConnection;
    }

    private void initializeAndRepairDatabaseSchema() {
        try {
            Connection conn = ensureDatabaseConnected();
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS books (" +
                        "id INT PRIMARY KEY, " +
                        "title VARCHAR(255) NOT NULL, " +
                        "status VARCHAR(50) DEFAULT 'Available', " +
                        "borrower VARCHAR(100) DEFAULT NULL, " +
                        "due_date VARCHAR(50) DEFAULT NULL);");

                st.execute("CREATE TABLE IF NOT EXISTS users (" +
                        "username VARCHAR(100) PRIMARY KEY, " +
                        "role VARCHAR(50) DEFAULT 'Standard Student');");

                st.execute("CREATE TABLE IF NOT EXISTS favorites (" +
                        "username VARCHAR(100), " +
                        "book_id INT, " +
                        "PRIMARY KEY (username, book_id));");

                // REALTIME TRACKING REQ: Table configuration logging all user activity events
                st.execute("CREATE TABLE IF NOT EXISTS activity_logs (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "username VARCHAR(100), " +
                        "action_type VARCHAR(100), " +
                        "details VARCHAR(255), " +
                        "timestamp VARCHAR(50));");

                logSystemEvent("Dynamic runtime infrastructure schema successfully synced.");
            }
        } catch (Exception e) {
            System.err.println("Schema Modification Error: " + e.getMessage());
        }
    }

    // HELPER: Easily record structural data tracking events directly into MySQL pipeline
    private void recordUserActivity(String username, String actionType, String details) {
        try {
            Connection conn = ensureDatabaseConnected();
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO activity_logs (username, action_type, details, timestamp) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, username);
                ps.setString(2, actionType);
                ps.setString(3, details);
                ps.setString(4, timestamp);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Failed to write to activity logs: " + e.getMessage());
        }
    }

    private void seedDataInventory() {
        try {
            Connection conn = ensureDatabaseConnected();
            try (Statement st = conn.createStatement()) {
                st.execute("INSERT IGNORE INTO users (username, role) VALUES ('admin', 'Administrator');");
                st.execute("INSERT IGNORE INTO users (username, role) VALUES ('2025-200491', 'Standard Student');");

                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM books;");
                if (rs.next() && rs.getInt(1) < 5) {
                    st.execute("INSERT IGNORE INTO books (id, title, status) VALUES (1001, 'Introduction to Java Cloud Design', 'Available');");
                    st.execute("INSERT IGNORE INTO books (id, title, status) VALUES (1002, 'Docker Containers Essentials', 'Available');");
                    st.execute("INSERT IGNORE INTO books (id, title, status) VALUES (1003, 'Advanced Database Distribution Architectures', 'Available');");
                    st.execute("INSERT IGNORE INTO books (id, title, status) VALUES (1004, 'The Great Gatsby', 'Available');");
                    st.execute("INSERT IGNORE INTO books (id, title, status) VALUES (1005, 'To Kill a Mockingbird', 'Available');");
                }
            }
        } catch (Exception e) {}
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
            logSystemEvent("System application framework operating live on gateway port: " + port);
        } catch (IOException e) {
            System.err.println("Failed to bind platform socket channel: " + e.getMessage());
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
            String query = exchange.getRequestURI().getQuery();
            String method = exchange.getRequestMethod();
            Map<String, String> params = parseFormBody(exchange);

            String sessionUser = getSessionUser(exchange);
            String searchQuery = "";

            if (query != null && query.contains("search=")) {
                try {
                    String[] queryParts = query.split("search=");
                    if (queryParts.length > 1) {
                        searchQuery = URLDecoder.decode(queryParts[1], StandardCharsets.UTF_8.name()).trim();
                    }
                } catch (Exception e) {}
            }

            if ("POST".equalsIgnoreCase(method)) {
                try {
                    Connection conn = ensureDatabaseConnected();

                    if ("/login".equals(path) || "/register".equals(path)) {
                        String uid = params.getOrDefault("userId", "").trim();

                        // VALIDATION ROUTINE CHECK: Allow "admin", otherwise filter with exact digit pattern rules
                        if (!"admin".equalsIgnoreCase(uid) && !STUDENT_ID_PATTERN.matcher(uid).matches()) {
                            displayValidationError(exchange, "Access Denied: Invalid Formatting standard found. Account IDs must exactly fit numerical pattern format: XXXX-XXXXXX (Ex: 2025-200491)");
                            return;
                        }

                        if (!uid.isEmpty()) {
                            try (PreparedStatement ps = conn.prepareStatement("SELECT role FROM users WHERE username=?")) {
                                ps.setString(1, uid);
                                ResultSet rs = ps.executeQuery();
                                if (!rs.next()) {
                                    try (PreparedStatement insertPs = conn.prepareStatement("INSERT INTO users (username, role) VALUES (?, 'Standard Student')")) {
                                        insertPs.setString(1, uid);
                                        insertPs.executeUpdate();
                                        recordUserActivity(uid, "Registered", "Created clean structural storage token identifier.");
                                    }
                                }
                                String token = UUID.randomUUID().toString();
                                tokenToUserMap.put(token, uid);
                                exchange.getResponseHeaders().add("Set-Cookie", "LIBRARY_USER_SESSION=" + token + "; Path=/; HttpOnly");
                                recordUserActivity(uid, "Logged In", "User account online session authorized.");
                            }
                        }
                        redirect(exchange, "/");
                        return;
                    }
                    else if ("/logout".equals(path)) {
                        if (sessionUser != null) {
                            recordUserActivity(sessionUser, "Logged Out", "Active web access portal connection severed cleanly.");
                        }
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

                            try (PreparedStatement ps = conn.prepareStatement(
                                    "UPDATE books SET status='Borrowed Out', borrower=?, due_date=? WHERE id=?")) {
                                ps.setString(1, sessionUser.trim());
                                ps.setString(2, dueDate);
                                ps.setInt(3, Integer.parseInt(assetId));
                                ps.executeUpdate();
                                recordUserActivity(sessionUser, "Borrowed Book", "Assigned Asset ID: " + assetId + " (Due Date: " + dueDate + ")");
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
                                recordUserActivity(sessionUser, "Returned Book", "Returned Asset ID: " + assetId + " to active catalog index.");
                            }
                        }
                        redirect(exchange, "/");
                        return;
                    }
                    else if ("/addBook".equals(path)) {
                        String title = params.getOrDefault("title", "").trim();
                        if ("admin".equals(sessionUser) && !title.isEmpty()) {
                            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT MAX(id) FROM books")) {
                                int nextId = (rs.next() && rs.getInt(1) >= 1001) ? rs.getInt(1) + 1 : 1001;
                                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO books (id, title, status) VALUES (?, ?, 'Available')")) {
                                    ps.setInt(1, nextId);
                                    ps.setString(2, title);
                                    ps.executeUpdate();
                                    recordUserActivity("admin", "Added Book", "Inserted asset entity: " + title + " as Asset ID: " + nextId);
                                }
                            }
                        }
                        redirect(exchange, "/");
                        return;
                    }
                    else if ("/deleteBook".equals(path)) {
                        String assetId = params.get("assetId");
                        if ("admin".equals(sessionUser) && assetId != null) {
                            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM books WHERE id=?")) {
                                ps.setInt(1, Integer.parseInt(assetId));
                                ps.executeUpdate();
                                recordUserActivity("admin", "Deleted Book", "Purged catalog file asset with ID target: " + assetId);
                            }
                        }
                        redirect(exchange, "/");
                        return;
                    }
                    else if ("/addFavorite".equals(path)) {
                        String assetId = params.get("assetId");
                        if (sessionUser != null && assetId != null) {
                            try (PreparedStatement ps = conn.prepareStatement("INSERT IGNORE INTO favorites (username, book_id) VALUES (?, ?)")) {
                                ps.setString(1, sessionUser.trim());
                                ps.setInt(2, Integer.parseInt(assetId));
                                ps.executeUpdate();
                            }
                        }
                        redirect(exchange, "/");
                        return;
                    }
                    else if ("/removeFavorite".equals(path)) {
                        String assetId = params.get("assetId");
                        if (sessionUser != null && assetId != null) {
                            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM favorites WHERE username=? AND book_id=?")) {
                                ps.setString(1, sessionUser.trim());
                                ps.setInt(2, Integer.parseInt(assetId));
                                ps.executeUpdate();
                            }
                        }
                        redirect(exchange, "/");
                        return;
                    }
                } catch (Exception e) {
                    logSystemEvent("Transactional processing error: " + e.getMessage());
                }
            }

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
            html.append("<title>High-Speed Library Access Hub</title>");
            html.append("<style>");
            html.append("body{font-family:'Segoe UI',Arial,sans-serif;margin:0;padding:0;background-color:#f4f7f9;color:#333;}");
            html.append(".navbar{background-color:#005abe;color:white;padding:15px 30px;display:flex;justify-content:space-between;align-items:center;box-shadow:0 2px 5px rgba(0,0,0,0.1);}");
            html.append(".navbar h2{margin:0;font-size:20px;font-weight:500;} .btn-logout{background:#ef4444;color:white;border:none;padding:8px 16px;font-size:13px;border-radius:4px;cursor:pointer;}");
            html.append(".wrapper{max-width:1200px;margin:30px auto;padding:0 20px;} .card{background:#fff;padding:25px;border-radius:6px;box-shadow:0 2px 4px rgba(0,0,0,0.04);margin-bottom:25px;}");
            html.append("table{width:100%;border-collapse:collapse;margin-top:10px;} th,td{padding:12px 15px;text-align:left;border-bottom:1px solid #e2e8f0;font-size:14px;}");
            html.append("th{background-color:#f8fafc;color:#475569;font-weight:600;} .btn{background-color:#005abe;color:white;border:none;padding:10px 20px;border-radius:4px;cursor:pointer;font-weight:600;}");
            html.append(".btn-action{padding:6px 14px;font-size:12px; border-radius:4px;} .btn-delete{background-color:#ef4444; color:white;} .btn-disabled{background-color:#cbd5e1;color:#94a3b8;cursor:not-allowed;}");
            html.append(".form-inline{display:flex;gap:10px;margin-bottom:15px;} .form-inline input[type='text']{padding:8px 12px;border:1px solid #cbd5e1;border-radius:4px;width:300px;}");
            html.append(".badge-borrowed{color:#ef4444;font-weight:500;} .search-container{margin-bottom:20px; display:flex; gap:10px;} .search-input{padding:10px; width:70%; border:1px solid #ccc; border-radius:4px;}");
            html.append(".login-container{width:420px;margin:100px auto;background:#fff;padding:30px;border-radius:8px;text-align:center;border-top:5px solid #005abe;box-shadow:0 4px 15px rgba(0,0,0,0.08);}");
            html.append(".login-container input[type='text']{width:100%;padding:12px;margin:15px 0;box-sizing:border-box;border:1px solid #ccc;border-radius:4px;}");
            html.append(".btn-fav{background-color:#f59e0b; color:white;} .btn-fav-remove{background-color:#64748b; color:white;}");
            html.append(".badge-log{padding:4px 8px; border-radius:4px; font-weight:600; font-size:11px; text-transform:uppercase;}");
            html.append(".badge-login{background-color:#dbeafe; color:#1e40af;} .badge-logout{background-color:#fef2f2; color:#991b1b;}");
            html.append(".badge-borrow{background-color:#fef3c7; color:#92400e;} .badge-return{background-color:#dcfce7; color:#166534;} .badge-register{background-color:#f3e8ff; color:#6b21a8;}");
            html.append("</style></head><body>");

            if (sessionUser == null) {
                html.append("<div class='login-container'><h2>Library Web Access</h2>");
                html.append("<p style='font-size:12px; color:gray; margin-top:-10px;'>Format Constraint Rules: <b>XXXX-XXXXXX</b> (Numbers only)</p>");
                html.append("<form method='POST'>");
                html.append("<input type='text' name='userId' placeholder='Numerical Format: 2025-200491' required autocomplete='off' pattern='\\d{4}-\\d{6}' title='Must match format rules exactly: 4 digits, hyphen, 6 digits'/>");
                html.append("<button type='submit' formaction='/login' class='btn' style='width:100%; margin-bottom:10px;'>Login Session</button>");
                html.append("<button type='submit' formaction='/register' class='btn' style='width:100%; background-color:#22c55e;'>Register New ID</button>");
                html.append("</form></div>");
            }
            else if ("admin".equalsIgnoreCase(sessionUser)) {
                html.append("<div class='navbar'><h2>Hello, admin! (ADMINISTRATOR DESK)</h2>");
                html.append("<form action='/logout' method='POST' style='margin:0;'><button type='submit' class='btn btn-logout'>Logout</button></form></div>");

                html.append("<div class='wrapper'>");

                // NEW REALTIME AUDIT LOG TAB PANELS: View user history logs natively
                html.append("<div class='card' style='border-top: 4px solid #6b21a8;'><h3>📊 Realtime User Activity & Transaction Logs</h3>");
                html.append("<div style='max-height: 250px; overflow-y: auto; border: 1px solid #e2e8f0;'>");
                html.append("<table><thead><tr><th>Timestamp</th><th>Account Reference</th><th>Event Context</th><th>Operational Log details</th></tr></thead><tbody>");
                try {
                    Connection conn = ensureDatabaseConnected();
                    try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM activity_logs ORDER BY id DESC LIMIT 50")) {
                        while (rs.next()) {
                            String act = rs.getString("action_type");
                            String badgeClass = "badge-login";
                            if ("Logged Out".equalsIgnoreCase(act)) badgeClass = "badge-logout";
                            else if ("Borrowed Book".equalsIgnoreCase(act)) badgeClass = "badge-borrow";
                            else if ("Returned Book".equalsIgnoreCase(act)) badgeClass = "badge-return";
                            else if ("Registered".equalsIgnoreCase(act)) badgeClass = "badge-register";

                            html.append("<tr><td style='color:gray; font-size:12px;'>").append(rs.getString("timestamp")).append("</td>");
                            html.append("<td><b>").append(rs.getString("username")).append("</b></td>");
                            html.append("<td><span class='badge-log ").append(badgeClass).append("'>").append(act).append("</span></td>");
                            html.append("<td style='color:#475569;'>").append(rs.getString("details")).append("</td></tr>");
                        }
                    }
                } catch (Exception e) {}
                html.append("</tbody></table></div></div>");

                html.append("<div class='card'><h3>Add New Book Asset</h3>");
                html.append("<form action='/addBook' method='POST' class='form-inline'>");
                html.append("<input type='text' name='title' placeholder='Book Title String' required autocomplete='off'/>");
                html.append("<button type='submit' class='btn'>Add Book</button>");
                html.append("</form></div>");

                html.append("<div class='card'><h3>Active Borrow Logs Matrix</h3>");
                html.append("<table><tr><th>Book ID</th><th>Resource Title</th><th>Active Student Borrower</th><th>Target Due Date</th></tr>");
                int logEntries = 0;
                try {
                    Connection conn = ensureDatabaseConnected();
                    try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM books WHERE borrower IS NOT NULL ORDER BY id ASC")) {
                        while (rs.next()) {
                            logEntries++;
                            html.append("<tr><td>").append(rs.getInt("id")).append("</td>");
                            html.append("<td>").append(rs.getString("title")).append("</td>");
                            html.append("<td><b style='color:#005abe;'>").append(rs.getString("borrower")).append("</b></td>");
                            html.append("<td style='color:red;'>").append(rs.getString("due_date")).append("</td></tr>");
                        }
                    }
                } catch (Exception e) {}
                if (logEntries == 0) html.append("<tr><td colspan='4' style='color:gray; text-align:center;'>No records are currently checked out.</td></tr>");
                html.append("</table></div>");

                html.append("<div class='card'><h3>Physical Catalog Inventory</h3>");
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
                            html.append("<td>").append("Available".equals(status) ? "Available" : "<span class='badge-borrowed'>Borrowed Out</span>").append("</td>");
                            html.append("<td><form action='/deleteBook' method='POST' style='margin:0;'>");
                            html.append("<input type='hidden' name='assetId' value='").append(id).append("'/>");
                            html.append("<button type='submit' class='btn btn-action btn-delete'>Remove Book</button>");
                            html.append("</form></td></tr>");
                        }
                    }
                } catch (Exception e) {}
                html.append("</table></div></div>");
            }
            else {
                html.append("<div class='navbar'><h2>Hello, ").append(sessionUser).append("! | Student Desk Portal</h2>");
                html.append("<form action='/logout' method='POST' style='margin:0;'><button type='submit' class='btn btn-logout'>Logout</button></form></div>");

                html.append("<div class='wrapper'>");

                html.append("<div class='card' style='border-left: 5px solid #f59e0b;'><h3>⭐ My Saved Favorites List</h3>");
                html.append("<table><tr><th>Asset ID</th><th>Resource Title</th><th>Status</th><th>Operation</th></tr>");
                int favoriteItemsCount = 0;
                try {
                    Connection conn = ensureDatabaseConnected();
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT b.id, b.title, b.status FROM favorites f JOIN books b ON f.book_id = b.id WHERE f.username = ? ORDER BY b.id ASC")) {
                        ps.setString(1, sessionUser.trim());
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                favoriteItemsCount++;
                                int id = rs.getInt("id");
                                String title = rs.getString("title");
                                String status = rs.getString("status");
                                html.append("<tr><td>").append(id).append("</td>");
                                html.append("<td>").append(title).append("</td>");
                                html.append("<td>").append("Available".equals(status) ? "Available" : "<span class='badge-borrowed'>Borrowed Out</span>").append("</td>");
                                html.append("<td><form action='/removeFavorite' method='POST' style='margin:0;'>");
                                html.append("<input type='hidden' name='assetId' value='").append(id).append("'/>");
                                html.append("<button type='submit' class='btn btn-action btn-fav-remove'>Unfavorite</button>");
                                html.append("</form></td></tr>");
                            }
                        }
                    }
                } catch (Exception e) {}
                if (favoriteItemsCount == 0) {
                    html.append("<tr><td colspan='4' style='color:grey; text-align:center;'>Your favorites shelf is currently empty. Click 'Favorite' below to pin books here!</td></tr>");
                }
                html.append("</table></div>");

                html.append("<div class='card'><h3>My Active Borrowed Log</h3>");
                html.append("<table><tr><th>Asset ID</th><th>Resource Title</th><th>Due Date (3-Day Limit)</th><th>Action</th></tr>");
                int borrowedLogSize = 0;
                try {
                    Connection conn = ensureDatabaseConnected();
                    try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM books WHERE borrower=? ORDER BY id ASC")) {
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
                                html.append("<button type='submit' class='btn btn-action' style='background-color:#22c55e; color:white;'>Return Resource</button>");
                                html.append("</form></td></tr>");
                            }
                        }
                    }
                } catch (Exception e) {}
                if (borrowedLogSize == 0) {
                    html.append("<tr><td colspan='4' style='color:grey; text-align:center;'>You have no active borrowed items.</td></tr>");
                }
                html.append("</table></div>");

                html.append("<div class='card'><h3>Global Inventory Index Catalog</h3>");
                html.append("<div class='search-container'>");
                html.append("<input type='text' id='searchInput' class='search-input' placeholder='Type title keyword to filter index list directly...' value='").append(searchQuery).append("'/>");
                html.append("<button class='btn' onclick='runSearch()'>Search Catalog</button>");
                html.append("</div>");
                html.append("<script>function runSearch(){ var val = document.getElementById('searchInput').value; window.location.href = '/?search=' + encodeURIComponent(val); }</script>");

                html.append("<table><tr><th>Asset ID</th><th>Resource Title</th><th>Status</th><th>Operations</th></tr>");
                String selectQuery = "SELECT * FROM books ORDER BY id ASC";
                if (!searchQuery.isEmpty()) {
                    selectQuery = "SELECT * FROM books WHERE title LIKE ? ORDER BY id ASC";
                }

                Set<Integer> favoritedIds = new HashSet<>();
                try {
                    Connection conn = ensureDatabaseConnected();
                    try (PreparedStatement ps = conn.prepareStatement("SELECT book_id FROM favorites WHERE username=?")) {
                        ps.setString(1, sessionUser.trim());
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) favoritedIds.add(rs.getInt("book_id"));
                        }
                    }
                } catch (Exception e) {}

                try {
                    Connection conn = ensureDatabaseConnected();
                    try (PreparedStatement searchPs = conn.prepareStatement(selectQuery)) {
                        if (!searchQuery.isEmpty()) {
                            searchPs.setString(1, "%" + searchQuery + "%");
                        }
                        try (ResultSet rs = searchPs.executeQuery()) {
                            while (rs.next()) {
                                int id = rs.getInt("id");
                                String title = rs.getString("title");
                                String status = rs.getString("status");

                                html.append("<tr><td>").append(id).append("</td>");
                                html.append("<td>").append(title).append("</td>");
                                html.append("<td>").append("Available".equals(status) ? "Available" : "<span class='badge-borrowed'>Borrowed Out</span>").append("</td>");
                                html.append("<td><div style='display:flex; gap:5px;'>");

                                if ("Available".equals(status)) {
                                    html.append("<form action='/borrow' method='POST' style='margin:0;'>");
                                    html.append("<input type='hidden' name='assetId' value='").append(id).append("'/>");
                                    html.append("<button type='submit' class='btn btn-action'>Borrow</button>");
                                    html.append("</form>");
                                } else {
                                    html.append("<button class='btn btn-action btn-disabled' disabled>Unavailable</button>");
                                }

                                if (favoritedIds.contains(id)) {
                                    html.append("<form action='/removeFavorite' method='POST' style='margin:0;'>");
                                    html.append("<input type='hidden' name='assetId' value='").append(id).append("'/>");
                                    html.append("<button type='submit' class='btn btn-action btn-fav-remove'>★ Unfav</button>");
                                    html.append("</form>");
                                } else {
                                    html.append("<form action='/addFavorite' method='POST' style='margin:0;'>");
                                    html.append("<input type='hidden' name='assetId' value='").append(id).append("'/>");
                                    html.append("<button type='submit' class='btn btn-action btn-fav'>⭐ Favorite</button>");
                                    html.append("</form>");
                                }
                                html.append("</div></td></tr>");
                            }
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

        // RENDERING HELPER: Generates a validation error landing template screen
        private void displayValidationError(HttpExchange exchange, String errorMessage) throws IOException {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Validation Error</title>");
            html.append("<style>body{font-family:'Segoe UI',sans-serif; background-color:#f4f7f9; text-align:center; padding-top:100px;}");
            html.append(".error-box{max-width:500px; margin:0 auto; background:white; padding:40px; border-radius:8px; border-top:5px solid #ef4444; box-shadow:0 4px 10px rgba(0,0,0,0.05);}");
            html.append(".btn{background-color:#005abe; color:white; padding:10px 20px; border:none; border-radius:4px; text-decoration:none; font-weight:600; cursor:pointer;}");
            html.append("</style></head><body><div class='error-box'>");
            html.append("<h2 style='color:#ef4444;'>Validation Error</h2>");
            html.append("<p style='color:#475569; margin-bottom:25px; line-height:1.5;'>").append(errorMessage).append("</p>");
            html.append("<a href='/' class='btn'>Return to Sign In</a>");
            html.append("</div></body></html>");

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