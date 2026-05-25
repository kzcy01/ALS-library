import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
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
    private static final Pattern STUDENT_ID_PATTERN = Pattern.compile("^(admin|\\d{4}-\\d{6})$");

    public AdvancedLibrarySystem() {
        logSystemEvent("Initializing Institutional Library Categorized Core Management Suite...");
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
                        "category VARCHAR(100) DEFAULT 'General Reference', " +
                        "status VARCHAR(50) DEFAULT 'Available', " +
                        "borrower VARCHAR(100) DEFAULT NULL, " +
                        "due_date VARCHAR(50) DEFAULT NULL," +
                        "chapters INT DEFAULT 10," +
                        "read_time_mins INT DEFAULT 180);");

                st.execute("CREATE TABLE IF NOT EXISTS users (" +
                        "username VARCHAR(100) PRIMARY KEY, " +
                        "role VARCHAR(50) DEFAULT 'Standard Student');");

                try { st.execute("ALTER TABLE users ADD COLUMN password VARCHAR(255) NOT NULL DEFAULT '123456';"); } catch (SQLException e){}
                try { st.execute("ALTER TABLE books ADD COLUMN category VARCHAR(100) DEFAULT 'General Reference';"); } catch (SQLException e){}
                try { st.execute("ALTER TABLE books ADD COLUMN chapters INT DEFAULT 10;"); } catch (SQLException e){}
                try { st.execute("ALTER TABLE books ADD COLUMN read_time_mins INT DEFAULT 180;"); } catch (SQLException e){}

                st.execute("CREATE TABLE IF NOT EXISTS favorites (" +
                        "username VARCHAR(100), " +
                        "book_id INT, " +
                        "PRIMARY KEY (username, book_id));");

                st.execute("CREATE TABLE IF NOT EXISTS activity_logs (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "username VARCHAR(100), " +
                        "action_type VARCHAR(100), " +
                        "details VARCHAR(255), " +
                        "timestamp VARCHAR(50));");

                logSystemEvent("Institutional database structure verified successfully.");
            }
        } catch (Exception e) {
            System.err.println("Schema adjustment exception: " + e.getMessage());
        }
    }

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
            System.err.println("Log append error: " + e.getMessage());
        }
    }

    private void seedDataInventory() {
        try {
            Connection conn = ensureDatabaseConnected();
            try (Statement st = conn.createStatement()) {
                st.execute("DELETE FROM users WHERE username='admin';");
            }
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO users (username, password, role) VALUES (?, ?, ?)")) {
                ps.setString(1, "admin");
                ps.setString(2, "admin123");
                ps.setString(3, "Administrator");
                ps.executeUpdate();
            }

            try (Statement st = conn.createStatement()) {
                st.execute("TRUNCATE TABLE books;");

                st.execute("INSERT INTO books (id, title, category, status, chapters, read_time_mins) VALUES (2001, 'Deep Reinforcement Learning Architectures & Neural Computing Networks', 'Computer Science & Engineering', 'Available', 16, 280);");
                st.execute("INSERT INTO books (id, title, category, status, chapters, read_time_mins) VALUES (2002, 'Cybersecurity Threat Mitigation: Zero-Trust Cryptographic Infrastructure', 'Computer Science & Engineering', 'Available', 12, 210);");
                st.execute("INSERT INTO books (id, title, category, status, chapters, read_time_mins) VALUES (2003, 'Distributed Microservices Architecture: Resilient Cloud System Deployments', 'Computer Science & Engineering', 'Available', 14, 245);");

                st.execute("INSERT INTO books (id, title, category, status, chapters, read_time_mins) VALUES (2004, 'Statistical Mechanics & Quantum Computing Framework Foundations', 'Theoretical & Applied Sciences', 'Available', 20, 340);");
                st.execute("INSERT INTO books (id, title, category, status, chapters, read_time_mins) VALUES (2005, 'Stochastic Differential Equations & Advanced Numerical Analysis Models', 'Theoretical & Applied Sciences', 'Available', 15, 260);");

                st.execute("INSERT INTO books (id, title, category, status, chapters, read_time_mins) VALUES (2006, 'Econometric Analytics: Predictive Vector Modeling in Global Securities Markets', 'Economics & Social Sciences', 'Available', 13, 225);");
                st.execute("INSERT INTO books (id, title, category, status, chapters, read_time_mins) VALUES (2007, 'Corporate Valuation Frameworks & Behavioral Financial Engineering', 'Economics & Social Sciences', 'Available', 11, 190);");

                logSystemEvent("Fresh institutional scholarly reference catalog injected successfully.");
            }
        } catch (Exception e) {
            System.err.println("Seeding exception caught: " + e.getMessage());
        }
    }

    private void setupLocalDesktopFrame() {
        try {
            mainFrame = new JFrame("Institutional Enterprise Core Gateway");
            mainFrame.setSize(400, 200);
            mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            localModel = new DefaultTableModel(new String[]{"Service Engine Operational Diagnostics"}, 0);
            JTable statusTable = new JTable(localModel);
            localModel.addRow(new Object[]{"Academic Core Framework Active..."});
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
            webServer.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(15));
            webServer.start();
            logSystemEvent("Institutional server listening on port: " + port);
        } catch (IOException e) {
            System.err.println("Web initialization error: " + e.getMessage());
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

        private String fetchOpenLibraryBooksJSON(String search) {
            try {
                String cleanQuery = search.replace(" ", "+");
                URL url = new URL("https://openlibrary.org/search.json?title=" + cleanQuery + "&limit=4");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    String inputLine;
                    StringBuilder response = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();
                    return response.toString();
                }
            } catch (Exception e) {
                System.err.println("Remote open-library query error: " + e.getMessage());
            }
            return "";
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();
            String method = exchange.getRequestMethod();
            Map<String, String> params = parseFormBody(exchange);

            String sessionUser = getSessionUser(exchange);
            String localSearchQuery = "";
            String apiSearchQuery = "";
            String logFilter = "all";

            if (query != null) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    String[] idx = pair.split("=");
                    if (idx.length > 1) {
                        if (idx[0].equals("search")) {
                            localSearchQuery = URLDecoder.decode(idx[1], StandardCharsets.UTF_8.name()).trim();
                        } else if (idx[0].equals("apiSearch")) {
                            apiSearchQuery = URLDecoder.decode(idx[1], StandardCharsets.UTF_8.name()).trim();
                        } else if (idx[0].equals("logFilter")) {
                            logFilter = URLDecoder.decode(idx[1], StandardCharsets.UTF_8.name()).trim();
                        }
                    }
                }
            }

            if ("POST".equalsIgnoreCase(method)) {
                try {
                    Connection conn = ensureDatabaseConnected();

                    if ("/login".equals(path)) {
                        String uid = params.getOrDefault("userId", "").trim();
                        String pass = params.getOrDefault("password", "").trim();
                        if (uid.isEmpty() || pass.isEmpty()) {
                            displayValidationError(exchange, "Identity error: values cannot be left empty.");
                            return;
                        }
                        try (PreparedStatement ps = conn.prepareStatement("SELECT password FROM users WHERE username=?")) {
                            ps.setString(1, uid);
                            ResultSet rs = ps.executeQuery();
                            if (rs.next() && rs.getString("password").equals(pass)) {
                                String token = UUID.randomUUID().toString();
                                tokenToUserMap.put(token, uid);
                                exchange.getResponseHeaders().add("Set-Cookie", "LIBRARY_USER_SESSION=" + token + "; Path=/; HttpOnly");
                                recordUserActivity(uid, "User Login", "Authorized application session initialization.");
                                redirect(exchange, "/");
                                return;
                            }
                        }
                        displayValidationError(exchange, "Authentication failed. Provide a correct security token profile.");
                        return;
                    }
                    else if ("/register".equals(path)) {
                        String uid = params.getOrDefault("userId", "").trim();
                        String pass = params.getOrDefault("password", "").trim();
                        if (!STUDENT_ID_PATTERN.matcher(uid).matches() || "admin".equalsIgnoreCase(uid)) {
                            displayValidationError(exchange, "Format error: Provided ID does not match institutional requirements.");
                            return;
                        }
                        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO users (username, password, role) VALUES (?, ?, 'Standard Student')")) {
                            ps.setString(1, uid);
                            ps.setString(2, pass);
                            ps.executeUpdate();
                            recordUserActivity(uid, "User Registration", "Appended database profile context framework.");
                        }
                        redirect(exchange, "/");
                        return;
                    }
                    else if ("/logout".equals(path)) {
                        if (sessionUser != null) recordUserActivity(sessionUser, "User Logout", "Terminated core application session token context.");
                        exchange.getResponseHeaders().add("Set-Cookie", "LIBRARY_USER_SESSION=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT");
                        redirect(exchange, "/");
                        return;
                    }
                    else if ("/borrow".equals(path)) {
                        String assetId = params.get("assetId");
                        if (sessionUser != null && assetId != null) {
                            String dueDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date(System.currentTimeMillis() + 259200000L));
                            try (PreparedStatement ps = conn.prepareStatement("UPDATE books SET status='Borrowed Out', borrower=?, due_date=? WHERE id=?")) {
                                ps.setString(1, sessionUser);
                                ps.setString(2, dueDate);
                                ps.setInt(3, Integer.parseInt(assetId));
                                ps.executeUpdate();
                                recordUserActivity(sessionUser, "Borrow Asset", "Assigned item registry control reference code: " + assetId);
                            }
                        }
                        redirect(exchange, "/");
                        return;
                    }
                    else if ("/return".equals(path)) {
                        String assetId = params.get("assetId");
                        if (sessionUser != null && assetId != null) {
                            try (PreparedStatement ps = conn.prepareStatement("UPDATE books SET status='Available', borrower=NULL, due_date=NULL WHERE id=?")) {
                                ps.setInt(1, Integer.parseInt(assetId));
                                ps.executeUpdate();
                                recordUserActivity(sessionUser, "Return Asset", "Re-indexed tracking status code: " + assetId);
                            }
                        }
                        redirect(exchange, "/");
                        return;
                    }
                    else if ("/addBook".equals(path)) {
                        String title = params.getOrDefault("title", "").trim();
                        String category = params.getOrDefault("category", "General Reference").trim();
                        if ("admin".equals(sessionUser) && !title.isEmpty()) {
                            int nextId = 2001;
                            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT MAX(id) FROM books")) {
                                if (rs.next() && rs.getInt(1) >= 2001) nextId = rs.getInt(1) + 1;
                            }
                            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO books (id, title, category, status, chapters, read_time_mins) VALUES (?, ?, ?, 'Available', 12, 180)")) {
                                ps.setInt(1, nextId);
                                ps.setString(2, title);
                                ps.setString(3, category);
                                ps.executeUpdate();
                                recordUserActivity("admin", "Catalog Append", "Manually cataloged system resource: " + title);
                            }
                        }
                        redirect(exchange, "/");
                        return;
                    }
                    else if ("/importApiBook".equals(path)) {
                        String title = params.getOrDefault("title", "Unknown Scholarly Work").trim();
                        if ("admin".equals(sessionUser)) {
                            int nextId = 2001;
                            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT MAX(id) FROM books")) {
                                if (rs.next() && rs.getInt(1) >= 2001) nextId = rs.getInt(1) + 1;
                            }
                            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO books (id, title, category, status, chapters, read_time_mins) VALUES (?, ?, 'External API Queries', 'Available', 15, 210)")) {
                                ps.setInt(1, nextId);
                                ps.setString(2, title);
                                ps.executeUpdate();
                                recordUserActivity("admin", "API Gateway Sync", "Imported title from OpenLibrary: " + title);
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
                                recordUserActivity("admin", "Catalog Deletion", "Removed database entry indexing code: " + assetId);
                            }
                        }
                        redirect(exchange, "/");
                        return;
                    }
                } catch (Exception e) {
                    logSystemEvent("Core operation processing exception: " + e.getMessage());
                }
            }

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
            html.append("<title>Institutional Resource Catalog & Management Platform</title>");
            html.append("<style>");
            html.append(":root { --bg: #f8fafc; --card-bg: #ffffff; --text: #0f172a; --primary: #0f172a; --nav-bg: #0f172a; --border: #cbd5e1; --accent: #1e40af; }");
            html.append("body.dark-mode { --bg: #090d16; --card-bg: #111827; --text: #f8fafc; --nav-bg: #030712; --border: #374151; --accent: #3b82f6; }");
            html.append("body { background-color: var(--bg); color: var(--text); font-family:'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin:0; padding:0; transition: background 0.2s, color 0.2s; }");
            html.append(".navbar { background-color: var(--nav-bg); color: #ffffff; padding:18px 40px; display:flex; justify-content:space-between; align-items:center; box-shadow:0 2px 8px rgba(0,0,0,0.08); }");
            html.append(".navbar h2 { margin:0; font-weight:600; font-size:20px; letter-spacing:-0.5px; display:flex; align-items:center; gap:10px; color: #ffffff; }");
            html.append(".navbar sub { font-size:12px; font-weight:400; color: #94a3b8; }");
            html.append(".btn { background-color: var(--accent); color:white; border:1px solid transparent; padding:8px 16px; border-radius:4px; cursor:pointer; font-weight:500; font-size:13px; transition: all 0.15s ease-in-out; }");
            html.append(".btn:hover { opacity: 0.9; } .btn-secondary { background-color: transparent; color: var(--text); border: 1px solid var(--border); }");
            html.append(".wrapper { max-width:1300px; margin:40px auto; padding:0 24px; }");
            html.append(".tabs-header { display:flex; gap:6px; margin-bottom:24px; border-bottom:1px solid var(--border); padding-bottom:1px; }");
            html.append(".tab-link { padding:10px 18px; font-weight:500; font-size:14px; cursor:pointer; background:none; border:none; color: var(--text); border-bottom:2px solid transparent; text-decoration:none; }");
            html.append(".tab-link.active { border-bottom:2px solid var(--accent); color: var(--accent); font-weight:600; }");
            html.append(".card { background: var(--card-bg); padding:30px; border-radius:6px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); margin-bottom:24px; border: 1px solid var(--border); }");
            html.append(".category-header { background: rgba(30, 64, 175, 0.08); padding: 12px 20px; font-size: 14px; font-weight: 700; border-left: 4px solid var(--accent); margin: 30px 0 15px 0; border-radius: 0 4px 4px 0; text-transform: uppercase; letter-spacing: 0.5px; }");
            html.append("table { width:100%; border-collapse:collapse; margin-top:10px; } th,td { padding:14px; text-align:left; border-bottom:1px solid var(--border); font-size:13px; }");
            html.append("th { background-color: rgba(0,0,0,0.02); color: var(--text); font-weight:600; text-transform: uppercase; font-size: 11px; letter-spacing: 0.5px; }");
            html.append(".catalog-grid { display: grid; grid-template-columns: 1fr; gap: 16px; }");
            html.append(".resource-row { background: var(--card-bg); border: 1px solid var(--border); padding: 20px; border-radius: 4px; display: flex; flex-direction: column; justify-content: space-between; transition: box-shadow 0.2s; }");
            html.append(".resource-row:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.03); }");
            html.append(".meta-container { display:flex; gap:20px; font-size:12px; color: #64748b; margin: 12px 0; align-items: center; }");
            html.append(".metric-box { background: rgba(30, 64, 175, 0.08); color: var(--accent); padding: 2px 8px; border-radius: 4px; font-weight: 600; font-size:11px; }");
            html.append(".login-container { width:380px; margin:140px auto; background: var(--card-bg); padding:35px; border-radius:6px; box-shadow:0 4px 20px rgba(0,0,0,0.06); border: 1px solid var(--border); }");
            html.append(".login-container input { width:100%; padding:10px; margin:10px 0 16px 0; border:1px solid var(--border); border-radius:4px; box-sizing:border-box; background: var(--bg); color: var(--text); }");
            html.append(".drawer-overlay { display:none; position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(15,23,42,0.6); z-index:1000; justify-content:center; align-items:center; backdrop-filter: blur(2px); }");
            html.append(".drawer-box { background: var(--card-bg); color: var(--text); width:640px; padding:35px; border-radius:6px; border:1px solid var(--border); position:relative; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1); }");
            html.append(".filter-bar { display: flex; gap: 8px; margin-bottom: 20px; align-items: center; background: rgba(0,0,0,0.02); padding: 10px; border-radius: 4px; border: 1px solid var(--border); }");
            html.append("</style>");
            html.append("<script>");
            html.append("function toggleDarkMode() { document.body.classList.toggle('dark-mode'); localStorage.setItem('theme', document.body.classList.contains('dark-mode') ? 'dark' : 'light'); }");
            html.append("function loadTheme() { if(localStorage.getItem('theme')==='dark') document.body.classList.add('dark-mode'); }");
            html.append("function viewAbstract(title, cat) { document.getElementById('drawerTitle').innerText = title; document.getElementById('drawerCategory').innerText = 'Classification Department: ' + cat; document.getElementById('abstractDrawer').style.display='flex'; }");
            html.append("function closeAbstract() { document.getElementById('abstractDrawer').style.display='none'; }");
            html.append("function filterLogs(val) { window.location.href = '/?tab=logs&logFilter=' + val; }");
            html.append("</script>");
            html.append("</head><body onload='loadTheme()'>");

            if (sessionUser == null) {
                html.append("<div class='login-container'><h3 style='margin-top:0; text-align:center; font-weight:600;'>Institutional Access Portal</h3>");
                html.append("<form method='POST'>");
                html.append("<label style='font-size:12px; font-weight:500; color:#64748b;'>Institutional Identity Token:</label>");
                html.append("<input type='text' name='userId' placeholder='e.g., admin or 2026-123456' required pattern='admin|\\d{4}-\\d{6}'/>");
                html.append("<label style='font-size:12px; font-weight:500; color:#64748b;'>Security Access Matrix Token:</label>");
                html.append("<input type='password' name='password' placeholder='••••••••' required/>");
                html.append("<button type='submit' formaction='/login' class='btn' style='width:100%; margin-bottom:10px;'>Secure Gateway Access</button>");
                html.append("<button type='submit' formaction='/register' class='btn btn-secondary' style='width:100%;'>Register Account Node</button>");
                html.append("</form></div>");
            }
            else {
                String activeTab = "books";
                if (query != null && query.contains("tab=")) {
                    if (query.contains("tab=logs") && "admin".equals(sessionUser)) activeTab = "logs";
                    else if (query.contains("tab=api")) activeTab = "api";
                    else if (query.contains("tab=add") && "admin".equals(sessionUser)) activeTab = "add";
                }

                html.append("<div class='navbar'><h2>Institutional Reference & Information Suite Sub-System Portal Node Architecture</h2>");
                html.append("<div style='display:flex; gap:12px; align-items:center;'>");
                html.append("<button class='btn btn-secondary' onclick='toggleDarkMode()' style='padding:6px 12px; font-size:12px;'>UI Theme</button>");
                html.append("<span style='font-size:13px; color:#94a3b8; font-weight:500;'>Operator Account: <b>").append(sessionUser).append("</b></span>");
                html.append("<form action='/logout' method='POST' style='margin:0;'><button type='submit' class='btn' style='background:#ef4444; padding:6px 12px; font-size:12px;'>Terminate Session</button></form>");
                html.append("</div></div>");

                html.append("<div class='wrapper'>");

                html.append("<div class='tabs-header'>");
                html.append("<a class='tab-link ").append(activeTab.equals("books")?"active":"").append("' href='/?tab=books'>Academic Categorized Index</a>");
                if ("admin".equals(sessionUser)) {
                    html.append("<a class='tab-link ").append(activeTab.equals("logs")?"active":"").append("' href='/?tab=logs'>Pipeline Audit Trails</a>");
                    html.append("<a class='tab-link ").append(activeTab.equals("add")?"active":"").append("' href='/?tab=add'>System Provisioning Workspace</a>");
                }
                html.append("<a class='tab-link ").append(activeTab.equals("api")?"active":"").append("' href='/?tab=api'>Open Library Cross-Reference</a>");
                html.append("</div>");

                if (activeTab.equals("books")) {
                    html.append("<div class='card'><h3>Search Local Catalog Inventories</h3>");
                    html.append("<div style='display:flex; gap:10px; margin-bottom:20px;'>");
                    html.append("<input type='text' id='localSearchInput' style='padding:10px; width:80%; border-radius:4px; border:1px solid var(--border); background:var(--card-bg); color:var(--text);' placeholder='Query title keywords, publication tags, schema values...' value='").append(localSearchQuery).append("'/>");
                    html.append("<button class='btn' onclick='runLocalSearch()'>Query Index</button>");
                    html.append("</div>");
                    html.append("<script>function runLocalSearch(){ var val=document.getElementById('localSearchInput').value; window.location.href='/?tab=books&search='+encodeURIComponent(val); }</script>");

                    try {
                        Connection conn = ensureDatabaseConnected();

                        List<String> dynamicCategories = new ArrayList<>();
                        try (Statement catSt = conn.createStatement(); ResultSet catRs = catSt.executeQuery("SELECT DISTINCT category FROM books ORDER BY category ASC")) {
                            while (catRs.next()) {
                                dynamicCategories.add(catRs.getString("category"));
                            }
                        }

                        if (dynamicCategories.isEmpty()) {
                            html.append("<p style='color:gray;'>No data entries are currently managed in the local data index context environment.</p>");
                        }

                        for (String domainCategory : dynamicCategories) {
                            String baseSql = "SELECT b.*, (SELECT COUNT(*) FROM favorites WHERE book_id=b.id) as fav_count, " +
                                             "(SELECT COUNT(*) FROM activity_logs WHERE action_type='Borrow Asset' AND details LIKE CONCAT('%', b.id, '%')) as borrow_count " +
                                             "FROM books b WHERE b.category = ?";

                            if (!localSearchQuery.isEmpty()) {
                                baseSql += " AND b.title LIKE ?";
                            }
                            baseSql += " ORDER BY b.id ASC";

                            try (PreparedStatement ps = conn.prepareStatement(baseSql)) {
                                ps.setString(1, domainCategory);
                                if (!localSearchQuery.isEmpty()) ps.setString(2, "%" + localSearchQuery + "%");

                                try (ResultSet rs = ps.executeQuery()) {
                                    boolean sectionHeaderPrinted = false;

                                    while (rs.next()) {
                                        if (!sectionHeaderPrinted) {
                                            html.append("<div class='category-header'>Category: ").append(domainCategory).append("</div>");
                                            html.append("<div class='catalog-grid'>");
                                            sectionHeaderPrinted = true;
                                        }

                                        int bid = rs.getInt("id");
                                        String title = rs.getString("title");
                                        String status = rs.getString("status");
                                        int chapters = rs.getInt("chapters");
                                        int readTime = rs.getInt("read_time_mins");
                                        int favoritesCount = rs.getInt("fav_count");
                                        int borrowCount = rs.getInt("borrow_count");

                                        html.append("<div class='resource-row'>");
                                        html.append("<div><h4 style='margin:0 0 4px 0; font-size:16px; font-weight:600;'>").append(title).append("</h4>");
                                        html.append("<p style='font-size:11px; color:#64748b; margin:0;'>Global Catalog Control Reference Registry: System-ID #").append(bid).append("</p></div>");

                                        html.append("<div class='meta-container'>");
                                        html.append("<span>Content Parts: ").append(chapters).append(" Sections</span>");
                                        html.append("<span>Processing Work-Time: ").append(readTime).append(" Mins</span>");
                                        html.append("<span>Institutional Favorites: <b class='metric-box'>").append(favoritesCount).append(" Profiles</b></span>");
                                        html.append("<span>Total Historical Access: <b class='metric-box' style='color:#10b981; background:rgba(16,185,129,0.08);'>").append(borrowCount).append(" Checkouts</b></span>");
                                        html.append("<span>Status: ").append("Available".equals(status)?"<span style='color:#22c55e; font-weight:600;'>In Inventory</span>":"<span style='color:#ef4444; font-weight:600;'>Leased Out</span>").append("</span>");
                                        html.append("</div>");

                                        html.append("<div style='display:flex; gap:8px; margin-top:5px;'>");
                                        html.append("<button class='btn btn-secondary' onclick=\"viewAbstract('").append(title.replace("'", "\\'")).append("', '").append(domainCategory.replace("'", "\\'")).append("')\">View Abstract / Metadata</button>");

                                        if ("Available".equals(status)) {
                                            html.append("<form action='/borrow' method='POST' style='margin:0;'><input type='hidden' name='assetId' value='").append(bid).append("'/><button type='submit' class='btn'>Request Allocation</button></form>");
                                        } else if (sessionUser.equals(rs.getString("borrower"))) {
                                            html.append("<form action='/return' method='POST' style='margin:0;'><input type='hidden' name='assetId' value='").append(bid).append("'/><button type='submit' class='btn btn-secondary'>Relinquish Allocation</button></form>");
                                        }

                                        html.append("<form action='/addFavorite' method='POST' style='margin:0;'><input type='hidden' name='assetId' value='").append(bid).append("'/><button type='submit' class='btn btn-secondary' style='color:var(--accent);'>Bookmark Entry</button></form>");

                                        if ("admin".equals(sessionUser)) {
                                            html.append("<form action='/deleteBook' method='POST' style='margin:0;'><input type='hidden' name='assetId' value='").append(bid).append("'/><button type='submit' class='btn' style='background:#ef4444;'>Purge Record</button></form>");
                                        }
                                        html.append("</div>");
                                        html.append("</div>");
                                    }

                                    if (sectionHeaderPrinted) {
                                        html.append("</div>");
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        html.append("<p style='color:red;'>Processing runtime query layout configuration exception: ").append(e.getMessage()).append("</p>");
                    }

                    html.append("</div>");
                }
                else if (activeTab.equals("logs") && "admin".equals(sessionUser)) {
                    html.append("<div class='card'><h3>System Pipeline Audit Trail Logs</h3>");

                    html.append("<div class='filter-bar'>");
                    html.append("<span style='font-size:12px; font-weight:600; color:#475569;'>Filter Audit Scope:</span>");
                    html.append("<select onchange='filterLogs(this.value)' style='padding:6px 12px; border-radius:4px; border:1px solid var(--border); background:var(--card-bg); color:var(--text); font-size:13px;'>");
                    html.append("<option value='all' ").append("all".equals(logFilter)?"selected":"").append(">All Transactions Matrix</option>");
                    html.append("<option value='login' ").append("login".equals(logFilter)?"selected":"").append(">User Logins Only</option>");
                    html.append("<option value='rented' ").append("rented".equals(logFilter)?"selected":"").append(">Asset Leases & Returns (Rented)</option>");
                    html.append("<option value='exited' ").append("exited".equals(logFilter)?"selected":"").append(">Session Terminations (Exited)</option>");
                    html.append("</select>");
                    html.append("</div>");

                    html.append("<table><thead><tr><th>Log Timestamp Target</th><th>Operator Identity Reference</th><th>Event Action Framework</th><th>Transaction Statement Metadata Context</th></tr></thead><tbody>");
                    try {
                        Connection conn = ensureDatabaseConnected();
                        String logSql = "SELECT * FROM activity_logs";

                        if ("login".equals(logFilter)) {
                            logSql += " WHERE action_type = 'User Login'";
                        } else if ("rented".equals(logFilter)) {
                            logSql += " WHERE action_type = 'Borrow Asset' OR action_type = 'Return Asset'";
                        } else if ("exited".equals(logFilter)) {
                            logSql += " WHERE action_type = 'User Logout'";
                        }
                        logSql += " ORDER BY id DESC LIMIT 50";

                        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(logSql)) {
                            while (rs.next()) {
                                html.append("<tr><td style='color:#64748b; font-size:12px;'>").append(rs.getString("timestamp")).append("</td>");
                                html.append("<td><code>").append(rs.getString("username")).append("</code></td>");
                                html.append("<td><span style='background:rgba(15,23,42,0.08); color:var(--text); padding:3px 6px; font-size:11px; border-radius:2px; font-weight:500;'>").append(rs.getString("action_type")).append("</span></td>");
                                html.append("<td style='color:#334155;'>").append(rs.getString("details")).append("</td></tr>");
                            }
                        }
                    } catch (Exception e) {}
                    html.append("</tbody></table></div>");
                }
                else if (activeTab.equals("api")) {
                    html.append("<div class='card'><h3>Open Library Academic Index Query Gateway</h3>");
                    html.append("<p style='font-size:13px; color:#64748b; margin-top:-5px;'>Query cross-network external dataset clusters via Open Library JSON streams API structures.</p>");
                    html.append("<div style='display:flex; gap:10px; margin-bottom:25px;'>");
                    html.append("<input type='text' id='apiSearchInput' style='padding:10px; width:80%; border-radius:4px; border:1px solid var(--border); background:var(--card-bg); color:var(--text);' placeholder='Search titles globally across network indexes (e.g., Quantum Physics)...' value='").append(apiSearchQuery).append("'/>");
                    html.append("<button class='btn' style='background:#1e40af;' onclick='runApiSearch()'>Execute Global API Fetch</button>");
                    html.append("</div>");
                    html.append("<script>function runApiSearch(){ var val=document.getElementById('apiSearchInput').value; window.location.href='/?tab=api&apiSearch='+encodeURIComponent(val); }</script>");

                    if (!apiSearchQuery.isEmpty()) {
                        html.append("<h4>Remote Cloud API Stream Results</h4><div style='display:grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap:15px;'>");
                        String jsonResponse = fetchOpenLibraryBooksJSON(apiSearchQuery);
                        if (!jsonResponse.isEmpty() && jsonResponse.contains("\"title\"")) {
                            int pointer = 0;
                            for (int k = 0; k < 4; k++) {
                                pointer = jsonResponse.indexOf("\"title\"", pointer);
                                if (pointer == -1) break;
                                int start = jsonResponse.indexOf(":", pointer) + 1;
                                while(jsonResponse.charAt(start) == ' ' || jsonResponse.charAt(start) == '"') start++;
                                int end = jsonResponse.indexOf("\"", start);
                                if (end == -1) break;
                                String cloudTitle = jsonResponse.substring(start, end);
                                pointer = end;

                                html.append("<div class='resource-row' style='border:1px dashed #1e40af;'>");
                                html.append("<h5 style='margin:0 0 8px 0;'>").append(cloudTitle).append("</h5>");
                                html.append("<p style='font-size:11px; color:#64748b; margin-bottom:15px;'>Source Context: Open Library Architectural Verification Package</p>");
                                if ("admin".equals(sessionUser)) {
                                    html.append("<form action='/importApiBook' method='POST'>");
                                    html.append("<input type='hidden' name='title' value='").append(cloudTitle.replace("'", "")).append("'/>");
                                    html.append("<button type='submit' class='btn' style='background:#1e40af; width:100%; border-radius:4px;'>Import Index Record</button>");
                                    html.append("</form>");
                                } else {
                                    html.append("<p style='font-size:11px; color:#ef4444; margin:0;'>Clearance level [Administrator] required to mount records into local storage arrays.</p>");
                                }
                                html.append("</div>");
                            }
                        } else {
                            html.append("<p style='color:gray; font-size:13px;'>No elements identified inside verification arrays.</p>");
                        }
                        html.append("</div>");
                    }
                    html.append("</div>");
                }
                else if (activeTab.equals("add") && "admin".equals(sessionUser)) {
                    html.append("<div class='card'><h3>Administrative Provisioning Workspace</h3>");
                    html.append("<form action='/addBook' method='POST' style='display:flex; flex-direction:column; gap:12px; max-width:450px;'>");
                    html.append("<label style='font-size:12px; font-weight:600;'>Resource Title Descriptor Name:</label>");
                    html.append("<input type='text' name='title' style='padding:10px; border-radius:4px; border:1px solid var(--border); background:var(--bg); color:var(--text);' placeholder='e.g., Introduction to Neural Network Topology Frameworks' required autocomplete='off'/>");

                    html.append("<label style='font-size:12px; font-weight:600;'>Classification Academic Department Category:</label>");
                    html.append("<select name='category' style='padding:10px; border-radius:4px; border:1px solid var(--border); background:var(--bg); color:var(--text);'>");
                    html.append("<option value='Computer Science & Engineering'>Computer Science & Engineering</option>");
                    html.append("<option value='Theoretical & Applied Sciences'>Theoretical & Applied Sciences</option>");
                    html.append("<option value='Economics & Social Sciences'>Economics & Social Sciences</option>");
                    html.append("<option value='General Reference'>General Reference</option>");
                    html.append("</select>");

                    html.append("<button type='submit' class='btn' style='margin-top:10px;'>Append Asset Framework Instance</button>");
                    html.append("</form></div>");
                }

                html.append("</div>");
            }

            html.append("<div id='abstractDrawer' class='drawer-overlay' onclick='closeAbstract()'>");
            html.append("<div class='drawer-box' onclick='event.stopPropagation()'>");
            html.append("<h3 id='drawerTitle' style='margin-top:0; border-bottom:1px solid var(--border); padding-bottom:12px; font-weight:600; font-size:18px; color:var(--accent);'></h3>");
            html.append("<div id='drawerCategory' style='font-size:11px; font-weight:bold; color:#64748b; background:rgba(0,0,0,0.04); padding:4px 8px; border-radius:2px; display:inline-block; margin-top:-5px;'></div>");
            html.append("<p style='font-size:12px; line-height:1.6; font-weight:600; text-transform:uppercase; letter-spacing:0.5px; color:#64748b; margin-top:25px;'>Archival Catalog Abstract Summary</p>");
            html.append("<p style='font-size:14px; line-height:1.7; text-align:justify; margin-bottom:0;'>This archival dataset documentation sets forth the foundational principles, structural architectures, and analytical methodologies associated with the chosen domain text. By compiling metadata values across historically verified operational vectors, the core chapters detail quantitative proofs, computing abstractions, and data transformations required to implement enterprise infrastructure scaling rules effectively. Access configurations require elevated institutional credentials to open or check out the entire publication entity from the secure main database clusters.</p>");
            html.append("<p style='text-align:right; margin-top:35px;'><button class='btn btn-secondary' onclick='closeAbstract()'>Dismiss Abstract Panel</button></p>");
            html.append("</div></div>");

            html.append("</body></html>");
            byte[] responseBytes = html.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(responseBytes); }
        }

        private void displayValidationError(HttpExchange exchange, String errorMessage) throws IOException {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Validation Constraint Failure</title>");
            html.append("<style>body{font-family:'Segoe UI',sans-serif; background-color:#f8fafc; text-align:center; padding-top:120px;}");
            html.append(".error-box{max-width:480px; margin:0 auto; background:white; padding:40px; border-radius:4px; border-top:4px solid #ef4444; box-shadow:0 4px 12px rgba(0,0,0,0.05); border-left:1px solid #cbd5e1; border-right:1px solid #cbd5e1; border-bottom:1px solid #cbd5e1;}");
            html.append(".btn{background-color:#0f172a; color:white; padding:10px 20px; border:none; border-radius:4px; text-decoration:none; font-weight:500; font-size:13px; cursor:pointer;}</style></head>");
            html.append("<body><div class='error-box'><h3 style='color:#ef4444; margin-top:0;'>Constraint Exception</h3>");
            html.append("<p style='color:#475569; margin-bottom:30px; font-size:14px; line-height:1.6;'>").append(errorMessage).append("</p>");
            html.append("<a href='/' class='btn'>Return to Main Portal</a></div></body></html>");
            byte[] responseBytes = html.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(responseBytes); }
        }

        private Map<String, String> parseFormBody(HttpExchange exchange) throws IOException {
            Map<String, String> result = new HashMap<>();
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) return result;
            InputStream is = exchange.getRequestBody();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) bos.write(buffer, 0, len);
            String formText = bos.toString(StandardCharsets.UTF_8.name());
            if (formText.isEmpty()) return result;
            String[] pairs = formText.split("&");
            for (String pair : pairs) {
                String[] idx = pair.split("=");
                if (idx.length == 2) {
                    result.put(URLDecoder.decode(idx[0], StandardCharsets.UTF_8.name()), URLDecoder.decode(idx[1], StandardCharsets.UTF_8.name()));
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