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

    private static final List<String> operationsLog = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, String> activeSessions = Collections.synchronizedMap(new HashMap<>());

    public AdvancedLibrarySystem() {
        logSystemEvent("Advanced Core Management System Initializing...");

        // Ensure database table integrity before booting the interfaces
        initializeAndRepairDatabaseSchema();
        seedDataInventory();

        startLocalhostWebServer();

        boolean isHeadless = "true".equals(System.getProperty("java.awt.headless"));
        if (!isHeadless) {
            setupLocalDesktopFrame();
        } else {
            System.out.println("[SYSTEM RUNTIME] Running headlessly in cloud database environment.");
        }
    }

    private static void logSystemEvent(String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String logLine = "[" + timestamp + "] " + message;
        operationsLog.add(0, logLine);
        System.out.println(logLine);
    }

    private Connection getConnection() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        String dbUrl = System.getenv("MYSQL_URL");
        if (dbUrl == null || dbUrl.isEmpty()) {
            dbUrl = "jdbc:mysql://localhost:3306/library_db?useSSL=false&allowPublicKeyRetrieval=true";
            return DriverManager.getConnection(dbUrl, "root", "password");
        }
        if (dbUrl.startsWith("mysql://")) {
            dbUrl = "jdbc:" + dbUrl;
        }
        return DriverManager.getConnection(dbUrl);
    }

    private void initializeAndRepairDatabaseSchema() {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            // Verify base table constraints
            st.execute("CREATE TABLE IF NOT EXISTS books (" +
                    "id INT PRIMARY KEY, " +
                    "title VARCHAR(255) NOT NULL, " +
                    "status VARCHAR(50) DEFAULT 'Available', " +
                    "borrower VARCHAR(100) DEFAULT NULL, " +
                    "due_date VARCHAR(50) DEFAULT NULL);");

            // DYNAMIC HOTFIX: Validate structural existence of the 'status' column directly in database metadata
            DatabaseMetaData md = conn.getMetaData();
            try (ResultSet rs = md.getColumns(null, null, "books", "status")) {
                if (!rs.next()) {
                    logSystemEvent("CRITICAL ALERT: 'status' column missing in live DB instance. Patching table structural layout...");
                    st.execute("ALTER TABLE books ADD COLUMN status VARCHAR(50) DEFAULT 'Available';");
                }
            }

            st.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "username VARCHAR(100) PRIMARY KEY, " +
                    "role VARCHAR(50) DEFAULT 'Standard Student');");

            logSystemEvent("Database structural schema validated and fully synchronized.");
        } catch (Exception e) {
            System.err.println("Database Structural Initialization Failure: " + e.getMessage());
        }
    }

    private void seedDataInventory() {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.execute("INSERT IGNORE INTO users (username, role) VALUES ('admin', 'Administrator');");
            st.execute("INSERT IGNORE INTO users (username, role) VALUES ('2025-200491', 'Standard Student');");

            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM books;");
            if (rs.next() && rs.getInt(1) < 100) {
                logSystemEvent("Inventory under target capacity. Seeding up to 100 genuine textbook titles...");

                String[] genuineTitles = {
                    "The Great Gatsby", "To Kill a Mockingbird", "1984", "Pride and Prejudice", "The Catcher in the Rye",
                    "The Hobbit", "Fahrenheit 451", "Jane Eyre", "Animal Farm", "The Lord of the Rings",
                    "Brave New World", "Lord of the Flies", "The Grapes of Wrath", "Macbeth", "Hamlet",
                    "Frankenstein", "The Odyssey", "A Tale of Two Cities", "Crime and Punishment", "The Old Man and the Sea",
                    "Great Expectations", "One Hundred Years of Solitude", "Moby-Dick", "The Iliad", "Wuthering Heights",
                    "The Divine Comedy", "The Brothers Karamazov", "Madame Bovary", "The Adventures of Huckleberry Finn", "Alice in Wonderland",
                    "The Picture of Dorian Gray", "Dracula", "Les Misérables", "The Count of Monte Cristo", "Don Quixote",
                    "War and Peace", "Heart of Darkness", "The Stranger", "The Trial", "The Metamorphosis",
                    "Catch-22", "The Sun Also Rises", "For Whom the Bell Tolls", "A Farewell to Arms", "The Sound and the Fury",
                    "As I Lay Dying", "The Scarlet Letter", "The Crucible", "Death of a Salesman", "The Giver",
                    "Of Mice and Men", "Ficciones", "The Aleph", "Invisible Man", "Native Son",
                    "The Color Purple", "Beloved", "Song of Solomon", "Catching Fire", "The Hunger Games",
                    "Mockingjay", "Dune", "Neuromancer", "Foundation", "I Robot",
                    "The Time Machine", "The War of the Worlds", "The Invisible Man", "The Island of Doctor Moreau", "A Game of Thrones",
                    "A Clash of Kings", "A Storm of Swords", "A Feast for Crows", "A Dance with Dragons", "The Fellowship of the Ring",
                    "The Two Towers", "The Return of the King", "The Silmarillion", "The Shining", "It",
                    "The Stand", "Misery", "Carrie", "Salem's Lot", "The Dark Tower",
                    "Good Omens", "American Gods", "Neverwhere", "The Ocean at the End of the Lane", "Stardust",
                    "The Road", "No Country for Old Men", "Blood Meridian", "The Border Trilogy", "Suttree",
                    "The Book Thief", "Life of Pi", "The Kite Runner", "A Thousand Splendid Suns", "And the Mountains Echoed"
                };

                conn.setAutoCommit(false);
                String queryTemplate = "INSERT IGNORE INTO books (id, title, status) VALUES (?, ?, 'Available');";
                try (PreparedStatement ps = conn.prepareStatement(queryTemplate)) {
                    int idCounter = 1001;
                    for (String title : genuineTitles) {
                        ps.setInt(1, idCounter);
                        ps.setString(2, title);
                        ps.addBatch();
                        idCounter++;
                    }
                    ps.executeBatch();
                    conn.commit();
                    logSystemEvent("Database catalog initialized successfully with " + genuineTitles.length + " entries.");
                } catch (Exception ex) {
                    conn.rollback();
                    throw ex;
                } finally {
                    conn.setAutoCommit(true);
                }
            }
        } catch (Exception e) {
            System.err.println("Inventory tracking injection failed: " + e.getMessage());
        }
    }

    private void setupLocalDesktopFrame() {
        mainFrame = new JFrame("Advanced Server Gateway Controller");
        mainFrame.setSize(400, 200);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        localModel = new DefaultTableModel(new String[]{"Service Engine Status"}, 0);
        JTable statusTable = new JTable(localModel);
        localModel.addRow(new Object[]{"HTTP Core Server Running Live..."});
        mainFrame.add(new JScrollPane(statusTable), BorderLayout.CENTER);
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setVisible(true);
    }

    private void startLocalhostWebServer() {
        try {
            String portEnv = System.getenv("PORT");
            int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;
            webServer = HttpServer.create(new InetSocketAddress(port), 0);
            webServer.createContext("/", new ApplicationRouterHandler());
            webServer.setExecutor(null);
            webServer.start();
            logSystemEvent("Library Portal live on web gateway port: " + port);
        } catch (IOException e) {
            System.err.println("Failed to bind web socket channel: " + e.getMessage());
        }
    }

    private class ApplicationRouterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();
            String method = exchange.getRequestMethod();
            Map<String, String> params = parseFormBody(exchange);

            String ipAddress = exchange.getRemoteAddress().getAddress().getHostAddress();
            String sessionUser = activeSessions.get(ipAddress);

            String searchQuery = "";
            if (query != null && query.contains("search=")) {
                try {
                    String[] queryParts = query.split("search=");
                    if (queryParts.length > 1) {
                        searchQuery = URLDecoder.decode(queryParts[1], StandardCharsets.UTF_8.name()).trim();
                    }
                } catch (Exception e) {}
            }

            // POST Actions Controller Router (Security Parameterized Fixes)
            if ("POST".equalsIgnoreCase(method)) {
                if ("/login".equals(path)) {
                    String uid = params.getOrDefault("userId", "").trim();
                    if (!uid.isEmpty()) {
                        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT role FROM users WHERE username=?")) {
                            ps.setString(1, uid);
                            ResultSet rs = ps.executeQuery();
                            if (!rs.next()) {
                                try (PreparedStatement insertPs = conn.prepareStatement("INSERT INTO users (username, role) VALUES (?, 'Standard Student')")) {
                                    insertPs.setString(1, uid);
                                    insertPs.executeUpdate();
                                }
                            }
                            activeSessions.put(ipAddress, uid);
                            logSystemEvent("Web User logged in: " + uid);
                        } catch (Exception e) {
                            logSystemEvent("Authentication error: " + e.getMessage());
                        }
                    }
                    redirect(exchange, "/");
                    return;
                }
                else if ("/logout".equals(path)) {
                    if (sessionUser != null) {
                        logSystemEvent("Web session disconnected: " + sessionUser);
                        activeSessions.remove(ipAddress);
                    }
                    redirect(exchange, "/");
                    return;
                }
                else if ("/borrow".equals(path)) {
                    String assetId = params.get("assetId");
                    if (sessionUser != null && assetId != null) {
                        // Enforce exactly a 3-day borrow return limit rule
                        Calendar cal = Calendar.getInstance();
                        cal.add(Calendar.DAY_OF_MONTH, 3);
                        String dueDate = new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());

                        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(
                                "UPDATE books SET status='Borrowed Out', borrower=?, due_date=? WHERE id=? AND status='Available'")) {
                            ps.setString(1, sessionUser);
                            ps.setString(2, dueDate);
                            ps.setInt(3, Integer.parseInt(assetId));
                            int updated = ps.executeUpdate();
                            if (updated > 0) {
                                logSystemEvent("User " + sessionUser + " successfully borrowed Asset ID: " + assetId + " (Due Date: " + dueDate + ")");
                            }
                        } catch (Exception e) {
                            logSystemEvent("Borrow process failed error: " + e.getMessage());
                        }
                    }
                    redirect(exchange, "/");
                    return;
                }
                else if ("/return".equals(path)) {
                    String assetId = params.get("assetId");
                    if (sessionUser != null && assetId != null) {
                        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(
                                "UPDATE books SET status='Available', borrower=NULL, due_date=NULL WHERE id=?")) {
                            ps.setInt(1, Integer.parseInt(assetId));
                            ps.executeUpdate();
                            logSystemEvent("User " + sessionUser + " checked back in Asset ID: " + assetId);
                        } catch (Exception e) {
                            logSystemEvent("Return processing error: " + e.getMessage());
                        }
                    }
                    redirect(exchange, "/");
                    return;
                }
                else if ("/addBook".equals(path)) {
                    String title = params.getOrDefault("title", "").trim();
                    if ("admin".equals(sessionUser) && !title.isEmpty()) {
                        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
                            ResultSet rs = st.executeQuery("SELECT MAX(id) FROM books");
                            int nextId = (rs.next() && rs.getInt(1) >= 1001) ? rs.getInt(1) + 1 : 1001;

                            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO books (id, title, status) VALUES (?, ?, 'Available')")) {
                                ps.setInt(1, nextId);
                                ps.setString(2, title);
                                ps.executeUpdate();
                                logSystemEvent("Admin added book: \"" + title + "\" [Assigned ID: " + nextId + "]");
                            }
                        } catch (Exception e) {
                            logSystemEvent("Add Asset operation failed: " + e.getMessage());
                        }
                    }
                    redirect(exchange, "/");
                    return;
                }
                else if ("/deleteBook".equals(path)) {
                    String assetId = params.get("assetId");
                    if ("admin".equals(sessionUser) && assetId != null) {
                        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement("DELETE FROM books WHERE id=?")) {
                            ps.setInt(1, Integer.parseInt(assetId));
                            ps.executeUpdate();
                            logSystemEvent("Admin removed asset record entry: " + assetId);
                        } catch (Exception e) {
                            logSystemEvent("Purge operation dropped: " + e.getMessage());
                        }
                    }
                    redirect(exchange, "/");
                    return;
                }
            }

            // HTML Web Response Engine
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
            html.append("<title>Advanced Cloud Library System</title>");
            html.append("<style>");
            html.append("body{font-family:'Segoe UI',Arial,sans-serif;margin:0;padding:0;background-color:#f4f7f9;color:#333;}");
            html.append(".navbar{background-color:#005abe;color:white;padding:15px 30px;display:flex;justify-content:space-between;align-items:center;box-shadow:0 2px 5px rgba(0,0,0,0.1);}");
            html.append(".navbar h2{margin:0;font-size:20px;font-weight:500;} .btn-logout{background:#ef4444;color:white;border:none;padding:8px 16px;font-size:13px;border-radius:4px;cursor:pointer;float:right;} .btn-logout:hover{background:#dc2626;}");
            html.append(".wrapper{max-width:1200px;margin:30px auto;padding:0 20px;}");
            html.append(".card{background:#fff;padding:25px;border-radius:6px;box-shadow:0 2px 4px rgba(0,0,0,0.04);margin-bottom:25px;}");
            html.append("table{width:100%;border-collapse:collapse;margin-top:10px;} th,td{padding:12px 15px;text-align:left;border-bottom:1px solid #e2e8f0;font-size:14px;}");
            html.append("th{background-color:#f8fafc;color:#475569;font-weight:600;text-transform:uppercase;font-size:12px;}");
            html.append(".btn{background-color:#005abe;color:white;border:none;padding:10px 20px;border-radius:4px;cursor:pointer;font-weight:600;} .btn:hover{background-color:#004494;}");
            html.append(".btn-action{padding:6px 14px;font-size:12px;} .btn-delete{background-color:#ef4444;} .btn-delete:hover{background-color:#dc2626;}");
            html.append(".btn-disabled{background-color:#cbd5e1;color:#94a3b8;cursor:not-allowed;}");
            html.append(".log-box{background-color:#f8fafc;border:1px solid #e2e8f0;padding:15px;height:180px;overflow-y:auto;border-radius:4px;font-family:monospace;font-size:12px;color:#334155;}");
            html.append(".form-inline{display:flex;gap:10px;margin-bottom:15px;} .form-inline input[type='text']{padding:8px 12px;border:1px solid #cbd5e1;border-radius:4px;width:300px;}");
            html.append(".badge-borrowed{color:#ef4444;font-weight:500;} .search-container{margin-bottom:20px; display:flex; gap:10px;} .search-input{padding:10px; width:70%; border:1px solid #ccc; border-radius:4px;}");
            html.append(".login-container{width:400px;margin:100px auto;background:#fff;padding:30px;border-radius:8px;box-shadow:0 4px 15px rgba(0,0,0,0.08);text-align:center;border-top:5px solid #005abe;}");
            html.append(".login-container input[type='text']{width:100%;padding:12px;margin:15px 0;box-sizing:border-box;border:1px solid #ccc;border-radius:4px;}");
            html.append("</style></head><body>");

            if (sessionUser == null) {
                // View 1: Authentication Form Panel
                html.append("<div class='login-container'><h2>Library Web Access</h2>");
                html.append("<form action='/login' method='POST'>");
                html.append("<input type='text' name='userId' placeholder='Identity Token (e.g., admin or 2025-200491)' required autocomplete='off'/>");
                html.append("<button type='submit' class='btn' style='width:100%; margin-bottom:8px;'>Login Session</button>");
                html.append("<button type='submit' class='btn' style='width:100%; background-color:#e2e8f0; color:#333;'>Register ID Account</button>");
                html.append("</form></div>");
            }
            else if ("admin".equalsIgnoreCase(sessionUser)) {
                // View 2: Administrative Desk UI Dashboard view
                html.append("<div class='navbar'><h2>Hello, admin! (ADMIN PANEL)</h2>");
                html.append("<form action='/logout' method='POST'><button type='submit' class='btn btn-logout'>Logout</button></form></div>");

                html.append("<div class='wrapper'>");
                html.append("<div class='card'><h3>Add New Book Asset</h3>");
                html.append("<form action='/addBook' method='POST' class='form-inline'>");
                html.append("<input type='text' name='title' placeholder='Book Title String' required autocomplete='off'/>");
                html.append("<button type='submit' class='btn'>Add Book</button>");
                html.append("</form></div>");

                html.append("<div class='card'><h3>Physical Catalog Inventory</h3>");
                html.append("<table><tr><th>Asset ID</th><th>Resource Title</th><th>Status</th><th>Operation</th></tr>");

                try (Connection conn = getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM books ORDER BY id ASC")) {
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
                } catch (Exception e) {
                    html.append("<tr><td colspan='4' style='color:red;'>Inventory rendering layer crash: ").append(e.getMessage()).append("</td></tr>");
                }
                html.append("</table></div>");

                html.append("<div class='card'><h3>Real-Time Operations Log</h3>");
                html.append("<div class='log-box'>");
                synchronized (operationsLog) {
                    for (String logLine : operationsLog) {
                        html.append(logLine).append("<br/>");
                    }
                }
                html.append("</div></div></div>");
            }
            else {
                // View 3: Student Core Account Dashboard Interface
                html.append("<div class='navbar'><h2>Hello, ").append(sessionUser).append("! | Student Desk</h2>");
                html.append("<form action='/logout' method='POST'><button type='submit' class='btn btn-logout'>Logout</button></form></div>");

                html.append("<div class='wrapper'>");
                html.append("<div class='card'><h3>My Active Borrowed Log</h3>");
                html.append("<table><tr><th>Asset ID</th><th>Resource Title</th><th>Due Date (3-Day Limit)</th><th>Action</th></tr>");

                int borrowedLogSize = 0;
                try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT * FROM books WHERE borrower=? ORDER BY id ASC")) {
                    ps.setString(1, sessionUser);
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
                } catch (Exception e) {}

                if (borrowedLogSize == 0) {
                    html.append("<tr><td colspan='4' style='color:grey; text-align:center;'>You have no active borrowed items.</td></tr>");
                }
                html.append("</table></div>");

                html.append("<div class='card'><h3>Global Inventory Index Catalog</h3>");
                html.append("<div class='search-container'>");
                html.append("<input type='text' id='searchInput' class='search-input' placeholder='Type keyword to search immediately...' value='").append(searchQuery).append("'/>");
                html.append("<button class='btn' onclick='runSearch()'>Search Index</button>");
                html.append("</div>");
                html.append("<script>function runSearch(){ var val = document.getElementById('searchInput').value; window.location.href = '/?search=' + encodeURIComponent(val); }</script>");

                html.append("<table><tr><th>Asset ID</th><th>Resource Title</th><th>Status</th><th>Operation</th></tr>");

                String selectQuery = "SELECT * FROM books ORDER BY id ASC";
                if (!searchQuery.isEmpty()) {
                    selectQuery = "SELECT * FROM books WHERE title LIKE ? ORDER BY id ASC";
                }

                try (Connection conn = getConnection(); PreparedStatement searchPs = conn.prepareStatement(selectQuery)) {
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
                            html.append("<td>");
                            if ("Available".equals(status)) {
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
                } catch (Exception e) {
                    html.append("<tr><td colspan='4' style='color:red;'>Error fetching data index rows: ").append(e.getMessage()).append("</td></tr>");
                }
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
        if (System.getenv("PORT") != null) {
            System.setProperty("java.awt.headless", "true");
            new AdvancedLibrarySystem();
        } else {
            System.setProperty("java.awt.headless", "false");
            SwingUtilities.invokeLater(() -> new AdvancedLibrarySystem());
        }
    }
}