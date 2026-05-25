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
        logSystemEvent("Initializing Ultra-Advanced Wattpad-Style Library Management Engine...");
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
                        "due_date VARCHAR(50) DEFAULT NULL," +
                        "chapters INT DEFAULT 12," +
                        "read_time_mins INT DEFAULT 45);");

                st.execute("CREATE TABLE IF NOT EXISTS users (" +
                        "username VARCHAR(100) PRIMARY KEY, " +
                        "role VARCHAR(50) DEFAULT 'Standard Student');");

                try { st.execute("ALTER TABLE users ADD COLUMN password VARCHAR(255) NOT NULL DEFAULT '123456';"); } catch (SQLException e){}
                try { st.execute("ALTER TABLE books ADD COLUMN chapters INT DEFAULT 12;"); } catch (SQLException e){}
                try { st.execute("ALTER TABLE books ADD COLUMN read_time_mins INT DEFAULT 45;"); } catch (SQLException e){}

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

                st.execute("CREATE TABLE IF NOT EXISTS comments (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "book_id INT, " +
                        "username VARCHAR(100), " +
                        "comment_text TEXT, " +
                        "timestamp VARCHAR(50));");

                logSystemEvent("Schema architecture synced securely with advanced social interaction engines.");
            }
        } catch (Exception e) {
            System.err.println("Schema configuration mismatch: " + e.getMessage());
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
            System.err.println("Failed to log activity: " + e.getMessage());
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
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM books;");
                if (rs.next() && rs.getInt(1) < 5) {
                    st.execute("INSERT IGNORE INTO books (id, title, status, chapters, read_time_mins) VALUES (1001, 'CEO Alpha Billionaire Obsession', 'Available', 48, 180);");
                    st.execute("INSERT IGNORE INTO books (id, title, status, chapters, read_time_mins) VALUES (1002, 'The Hybrid Queen Lost Kingdom', 'Available', 32, 110);");
                    st.execute("INSERT IGNORE INTO books (id, title, status, chapters, read_time_mins) VALUES (1003, '1984 Cyber Dystopia', 'Available', 15, 95);");
                }
            }
        } catch (Exception e) {}
    }

    private void setupLocalDesktopFrame() {
        try {
            mainFrame = new JFrame("Advanced Core Controller Engine");
            mainFrame.setSize(400, 200);
            mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            localModel = new DefaultTableModel(new String[]{"Engine Status Matrix"}, 0);
            JTable statusTable = new JTable(localModel);
            localModel.addRow(new Object[]{"Web Engine operating live..."});
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
            logSystemEvent("Framework live on system gateway channel: " + port);
        } catch (IOException e) {
            System.err.println("Web core failed initialization: " + e.getMessage());
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
                URL url = new URL("https://openlibrary.org/search.json?title=" + cleanQuery + "&limit=3");
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
                System.err.println("OpenLibrary API fetching alert: " + e.getMessage());
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

            if (query != null) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    String[] idx = pair.split("=");
                    if (idx.length > 1) {
                        if (idx[0].equals("search")) {
                            localSearchQuery = URLDecoder.decode(idx[1], StandardCharsets.UTF_8.name()).trim();
                        } else if (idx[0].equals("apiSearch")) {
                            apiSearchQuery = URLDecoder.decode(idx[1], StandardCharsets.UTF_8.name()).trim();
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
                            displayValidationError(exchange, "Identity error parameters empty.");
                            return;
                        }
                        try (PreparedStatement ps = conn.prepareStatement("SELECT password FROM users WHERE username=?")) {
                            ps.setString(1, uid);
                            ResultSet rs = ps.executeQuery();
                            if (rs.next() && rs.getString("password").equals(pass)) {
                                String token = UUID.randomUUID().toString();
                                tokenToUserMap.put(token, uid);
                                exchange.getResponseHeaders().add("Set-Cookie", "LIBRARY_USER_SESSION=" + token + "; Path=/; HttpOnly");
                                recordUserActivity(uid, "Logged In", "Session initialized successfully.");
                                redirect(exchange, "/");
                                return;
                            }
                        }
                        displayValidationError(exchange, "Invalid credentials. Try standard 'admin' / 'admin123'.");
                        return;
                    }
                    else if ("/register".equals(path)) {
                        String uid = params.getOrDefault("userId", "").trim();
                        String pass = params.getOrDefault("password", "").trim();
                        if (!STUDENT_ID_PATTERN.matcher(uid).matches() || "admin".equalsIgnoreCase(uid)) {
                            displayValidationError(exchange, "Invalid user identifier code format rule constraint.");
                            return;
                        }
                        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO users (username, password, role) VALUES (?, ?, 'Standard Student')")) {
                            ps.setString(1, uid);
                            ps.setString(2, pass);
                            ps.executeUpdate();
                            recordUserActivity(uid, "Registered", "Account initialized into schema profiles.");
                        }
                        redirect(exchange, "/");
                        return;
                    }
                    else if ("/logout".equals(path)) {
                        if (sessionUser != null) recordUserActivity(sessionUser, "Logged Out", "Session clean up ran.");
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
                                recordUserActivity(sessionUser, "Borrowed Book", "Asset Tracking code referenced: " + assetId);
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
                                recordUserActivity(sessionUser, "Returned Book", "Asset Tracking back inside database.");
                            }
                        }
                        redirect(exchange, "/");
                        return;
                    }
                    else if ("/addBook".equals(path)) {
                        String title = params.getOrDefault("title", "").trim();
                        if ("admin".equals(sessionUser) && !title.isEmpty()) {
                            int nextId = 1001;
                            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT MAX(id) FROM books")) {
                                if (rs.next() && rs.getInt(1) >= 1001) nextId = rs.getInt(1) + 1;
                            }
                            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO books (id, title, status, chapters, read_time_mins) VALUES (?, ?, 'Available', 14, 65)")) {
                                ps.setInt(1, nextId);
                                ps.setString(2, title);
                                ps.executeUpdate();
                                recordUserActivity("admin", "Added Book", "Dynamic creation inside inventory: " + title);
                            }
                        }
                        redirect(exchange, "/");
                        return;
                    }
                    else if ("/addComment".equals(path)) {
                        String bid = params.get("bookId");
                        String text = params.getOrDefault("commentText", "").trim();
                        if (sessionUser != null && bid != null && !text.isEmpty()) {
                            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());
                            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO comments (book_id, username, comment_text, timestamp) VALUES (?, ?, ?, ?)")) {
                                ps.setInt(1, Integer.parseInt(bid));
                                ps.setString(2, sessionUser);
                                ps.setString(3, text);
                                ps.setString(4, time);
                                ps.executeUpdate();
                            }
                        }
                        redirect(exchange, "/?tab=books");
                        return;
                    }
                    else if ("/importApiBook".equals(path)) {
                        String title = params.getOrDefault("title", "Unknown API Book").trim();
                        if ("admin".equals(sessionUser)) {
                            int nextId = 2001;
                            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT MAX(id) FROM books")) {
                                if (rs.next() && rs.getInt(1) >= 1001) nextId = rs.getInt(1) + 1;
                            }
                            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO books (id, title, status, chapters, read_time_mins) VALUES (?, ?, 'Available', 24, 120)")) {
                                ps.setInt(1, nextId);
                                ps.setString(2, title);
                                ps.executeUpdate();
                                recordUserActivity("admin", "API Import", "Imported via Open Library Gateway: " + title);
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
                            }
                        }
                        redirect(exchange, "/");
                        return;
                    }
                    else if ("/addFavorite".equals(path)) {
                        String assetId = params.get("assetId");
                        if (sessionUser != null && assetId != null) {
                            try (PreparedStatement ps = conn.prepareStatement("INSERT IGNORE INTO favorites (username, book_id) VALUES (?, ?)")) {
                                ps.setString(1, sessionUser);
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
                                ps.setString(1, sessionUser);
                                ps.setInt(2, Integer.parseInt(assetId));
                                ps.executeUpdate();
                            }
                        }
                        redirect(exchange, "/");
                        return;
                    }
                } catch (Exception e) {
                    logSystemEvent("Operational alert engine trace: " + e.getMessage());
                }
            }

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
            html.append("<title>Wattpad Infinite Library Network Suite</title>");
            html.append("<style>");
            html.append(":root { --bg: #f4f7f9; --card-bg: #ffffff; --text: #333333; --primary: #ff4500; --nav-bg: #1a1a1a; --border: #e2e8f0; }");
            html.append("body.dark-mode { --bg: #0f0f12; --card-bg: #181820; --text: #f1f5f9; --nav-bg: #050507; --border: #2d2d3d; }");
            html.append("body { background-color: var(--bg); color: var(--text); font-family:'Segoe UI',sans-serif; margin:0; padding:0; transition: background 0.3s, color 0.3s; }");
            html.append(".navbar { background-color: var(--nav-bg); color: white; padding:15px 30px; display:flex; justify-content:space-between; align-items:center; box-shadow:0 4px 12px rgba(0,0,0,0.15); }");
            html.append(".navbar h2 { margin:0; font-weight:600; font-size:22px; color: #ff4500; display:flex; align-items:center; gap:8px; }");
            html.append(".btn { background-color: #ff4500; color:white; border:none; padding:10px 18px; border-radius:30px; cursor:pointer; font-weight:600; font-size:13px; transition: 0.2s transform; }");
            html.append(".btn:hover { transform: scale(1.03); } .btn-secondary { background-color: #4a5568; }");
            html.append(".wrapper { max-width:1250px; margin:30px auto; padding:0 20px; }");
            html.append(".tabs-header { display:flex; gap:10px; margin-bottom:20px; border-bottom:2px solid var(--border); padding-bottom:10px; }");
            html.append(".tab-link { padding:10px 20px; font-weight:600; cursor:pointer; background:none; border:none; color: var(--text); border-radius:5px; text-decoration:none; }");
            html.append(".tab-link.active { background-color: #ff4500; color:white; }");
            html.append(".card { background: var(--card-bg); padding:25px; border-radius:12px; box-shadow:0 4px 6px rgba(0,0,0,0.02); margin-bottom:25px; border: 1px solid var(--border); }");
            html.append("table { width:100%; border-collapse:collapse; margin-top:10px; } th,td { padding:14px; text-align:left; border-bottom:1px solid var(--border); font-size:14px; }");
            html.append("th { background-color: rgba(0,0,0,0.02); color: var(--text); font-weight:600; }");
            html.append(".wattpad-shelf { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 20px; margin-top: 15px; }");
            html.append(".book-card { background: var(--card-bg); border: 1px solid var(--border); padding: 20px; border-radius: 12px; position:relative; box-shadow: 0 4px 10px rgba(0,0,0,0.01); }");
            html.append(".book-meta { display:flex; gap:15px; font-size:12px; color: gray; margin: 10px 0; }");
            html.append(".metric-badge { background: rgba(255, 69, 0, 0.1); color: #ff4500; padding: 3px 8px; border-radius: 20px; font-weight: 700; }");
            html.append(".login-container { width:400px; margin:120px auto; background: var(--card-bg); padding:35px; border-radius:12px; box-shadow:0 10px 30px rgba(0,0,0,0.1); border-top: 6px solid #ff4500; }");
            html.append(".login-container input { width:100%; padding:12px; margin:10px 0 18px 0; border:1px solid var(--border); border-radius:6px; box-sizing:border-box; background: var(--bg); color: var(--text); }");
            html.append(".comment-section { margin-top:15px; background: rgba(0,0,0,0.01); padding:10px; border-radius:8px; }");
            html.append(".comment-input { width:75%; padding:8px; border-radius:4px; border:1px solid var(--border); background: var(--bg); color: var(--text); }");
            html.append(".reader-overlay { display:none; position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(0,0,0,0.85); z-index:1000; justify-content:center; align-items:center; }");
            html.append(".reader-box { background: #fffae9; color:#111; width:70%; height:80%; padding:40px; border-radius:12px; overflow-y:auto; font-family:'Georgia', serif; line-height:1.8; font-size:18px; position:relative; }");
            html.append("</style>");
            html.append("<script>");
            html.append("function toggleDarkMode() { document.body.classList.toggle('dark-mode'); localStorage.setItem('theme', document.body.classList.contains('dark-mode') ? 'dark' : 'light'); }");
            html.append("function loadTheme() { if(localStorage.getItem('theme')==='dark') document.body.classList.add('dark-mode'); }");
            html.append("function openStoryReader(title) { document.getElementById('storyTitle').innerText = title; document.getElementById('readerPopup').style.display='flex'; }");
            html.append("function closeStoryReader() { document.getElementById('readerPopup').style.display='none'; }");
            html.append("</script>");
            html.append("</head><body onload='loadTheme()'>");

            if (sessionUser == null) {
                html.append("<div class='login-container'><h2 style='text-align:center;'>🧡 wattpad cloud log</h2>");
                html.append("<form method='POST'>");
                html.append("<label style='font-size:12px; font-weight:600;'>Profile Account Code String:</label>");
                html.append("<input type='text' name='userId' placeholder='admin or student sequence (XXXX-XXXXXX)' required pattern='admin|\\d{4}-\\d{6}'/>");
                html.append("<label style='font-size:12px; font-weight:600;'>Security Access Cipher Key:</label>");
                html.append("<input type='password' name='password' placeholder='Enter database passphrase mapping token' required/>");
                html.append("<button type='submit' formaction='/login' class='btn' style='width:100%; margin-bottom:10px;'>Authorize Session Portal</button>");
                html.append("<button type='submit' formaction='/register' class='btn btn-secondary' style='width:100%;'>Register Identity Record</button>");
                html.append("</form></div>");
            }
            else {
                String activeTab = "books";
                if (query != null && query.contains("tab=")) {
                    if (query.contains("tab=logs")) activeTab = "logs";
                    else if (query.contains("tab=api")) activeTab = "api";
                    else if (query.contains("tab=add")) activeTab = "add";
                }

                html.append("<div class='navbar'><h2>📚 Wattpad Infinite Library Network <sub>[" + sessionUser + "]</sub></h2>");
                html.append("<div style='display:flex; gap:10px; align-items:center;'>");
                html.append("<button class='btn btn-secondary' onclick='toggleDarkMode()' style='padding:6px 12px;'>🌗 Mode</button>");
                html.append("<form action='/logout' method='POST' style='margin:0;'><button type='submit' class='btn' style='background:#ef4444;'>Logout Account</button></form>");
                html.append("</div></div>");

                html.append("<div class='wrapper'>");

                // Navigation Tabs Switcher Matrix
                html.append("<div class='tabs-header'>");
                html.append("<a class='tab-link ").append(activeTab.equals("books")?"active":"").append("' href='/?tab=books'>📖 Story Shelf Catalogue</a>");
                html.append("<a class='tab-link ").append(activeTab.equals("logs")?"active":"").append("' href='/?tab=logs'>📑 Transaction System Logs</a>");
                html.append("<a class='tab-link ").append(activeTab.equals("api")?"active":"").append("' href='/?tab=api'>🌐 Global Open Library Index Gateway</a>");
                if ("admin".equals(sessionUser)) {
                    html.append("<a class='tab-link ").append(activeTab.equals("add")?"active":"").append("' href='/?tab=add'>➕ System Inventory Provisioning</a>");
                }
                html.append("</div>");

                if (activeTab.equals("books")) {
                    html.append("<div class='card'><h3>🔍 Catalogue Master Index Filtering Engine</h3>");
                    html.append("<div style='display:flex; gap:10px; margin-bottom:20px;'>");
                    html.append("<input type='text' id='localSearchInput' style='padding:12px; width:75%; border-radius:6px; border:1px solid var(--border); background:var(--bg); color:var(--text);' placeholder='Filter story entries, asset codes, content variables natively...' value='").append(localSearchQuery).append("'/>");
                    html.append("<button class='btn' onclick='runLocalSearch()'>Execute Query</button>");
                    html.append("</div>");
                    html.append("<script>function runLocalSearch(){ var val=document.getElementById('localSearchInput').value; window.location.href='/?tab=books&search='+encodeURIComponent(val); }</script>");

                    html.append("<div class='wattpad-shelf'>");

                    try {
                        Connection conn = ensureDatabaseConnected();
                        String baseSql = "SELECT b.*, (SELECT COUNT(*) FROM favorites WHERE book_id=b.id) as fav_count, " +
                                         "(SELECT COUNT(*) FROM activity_logs WHERE action_type='Borrowed Book' AND details LIKE CONCAT('%', b.id, '%')) as borrow_count " +
                                         "FROM books b";
                        if (!localSearchQuery.isEmpty()) {
                            baseSql += " WHERE b.title LIKE ?";
                        }
                        baseSql += " ORDER BY b.id ASC";

                        try (PreparedStatement ps = conn.prepareStatement(baseSql)) {
                            if (!localSearchQuery.isEmpty()) ps.setString(1, "%" + localSearchQuery + "%");
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) {
                                    int bid = rs.getInt("id");
                                    String title = rs.getString("title");
                                    String status = rs.getString("status");
                                    int chapters = rs.getInt("chapters");
                                    int readTime = rs.getInt("read_time_mins");
                                    int favoritesCount = rs.getInt("fav_count");
                                    int borrowCount = rs.getInt("borrow_count");

                                    html.append("<div class='book-card'>");
                                    html.append("<h4 style='margin:0 0 5px 0; font-size:18px;'>").append(title).append("</h4>");
                                    html.append("<p style='font-size:11px; color:gray; margin:0;'>Resource ID: ").append(bid).append("</p>");

                                    html.append("<div class='book-meta'>");
                                    html.append("<span>📚 ").append(chapters).append(" Chapters</span>");
                                    html.append("<span>⏱️ ").append(readTime).append(" mins read</span>");
                                    html.append("</div>");

                                    html.append("<div class='book-meta' style='margin-top:-5px;'>");
                                    html.append("<span>⭐ Favorites: <b class='metric-badge'>").append(favoritesCount).append(" users</b></span>");
                                    html.append("<span>📖 Checkouts: <b class='metric-badge' style='color:#005abe; background:rgba(0,90,190,0.1);'>").append(borrowCount).append(" times</b></span>");
                                    html.append("</div>");

                                    html.append("<p style='font-size:13px;'>Availability Status: ").append("Available".equals(status)?"<b style='color:#22c55e;'>Live Registry</b>":"<b style='color:#ef4444;'>Checked Out</b>").append("</p>");

                                    html.append("<div style='display:flex; gap:8px; margin-top:15px;'>");
                                    html.append("<button class='btn' style='background:#6366f1;' onclick=\"openStoryReader('").append(title.replace("'", "\\'")).append("')\">👁️ Read Story</button>");

                                    if ("Available".equals(status)) {
                                        html.append("<form action='/borrow' method='POST' style='margin:0;'><input type='hidden' name='assetId' value='").append(bid).append("'/><button type='submit' class='btn'>Borrow</button></form>");
                                    } else if (sessionUser.equals(rs.getString("borrower"))) {
                                        html.append("<form action='/return' method='POST' style='margin:0;'><input type='hidden' name='assetId' value='").append(bid).append("'/><button type='submit' class='btn btn-secondary'>Return</button></form>");
                                    }

                                    html.append("<form action='/addFavorite' method='POST' style='margin:0;'><input type='hidden' name='assetId' value='").append(bid).append("'/><button type='submit' class='btn btn-secondary' style='background:#eab308;'>⭐ Pin</button></form>");

                                    if ("admin".equals(sessionUser)) {
                                        html.append("<form action='/deleteBook' method='POST' style='margin:0;'><input type='hidden' name='assetId' value='").append(bid).append("'/><button type='submit' class='btn' style='background:#ef4444;'>Wipe</button></form>");
                                    }
                                    html.append("</div>");

                                    // Display social review comments interface engine
                                    html.append("<div class='comment-section'><h5>Reader Community Feed Insights</h5>");
                                    try (PreparedStatement commPs = conn.prepareStatement("SELECT * FROM comments WHERE book_id=? ORDER BY id ASC")) {
                                        commPs.setInt(1, bid);
                                        try (ResultSet commRs = commPs.executeQuery()) {
                                            while (commRs.next()) {
                                                html.append("<p style='font-size:12px; margin:4px 0;'><b>").append(commRs.getString("username")).append("</b>: ").append(commRs.getString("comment_text")).append(" <i style='font-size:10px; color:gray;'>").append(commRs.getString("timestamp")).append("</i></p>");
                                            }
                                        }
                                    }
                                    html.append("<form action='/addComment' method='POST' style='margin-top:8px;'>");
                                    html.append("<input type='hidden' name='bookId' value='").append(bid).append("'/>");
                                    html.append("<input type='text' name='commentText' class='comment-input' placeholder='Post an inline reaction analysis review...' required/>");
                                    html.append("<button type='submit' class='btn' style='padding:4px 10px; font-size:11px; margin-left:4px;'>Review</button>");
                                    html.append("</form></div>");

                                    html.append("</div>");
                                }
                            }
                        }
                    } catch (Exception e) {}

                    html.append("</div></div>");
                }
                else if (activeTab.equals("logs")) {
                    html.append("<div class='card'><h3>📑 Live Pipeline Event Tracking Log Logs</h3>");
                    html.append("<table><thead><tr><th>System Log Time</th><th>Account Node</th><th>Action Class</th><th>Log Statement Manifest</th></tr></thead><tbody>");
                    try {
                        Connection conn = ensureDatabaseConnected();
                        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM activity_logs ORDER BY id DESC LIMIT 40")) {
                            while (rs.next()) {
                                html.append("<tr><td style='color:gray; font-size:12px;'>").append(rs.getString("timestamp")).append("</td>");
                                html.append("<td><b>").append(rs.getString("username")).append("</b></td>");
                                html.append("<td><span style='background:#ff4500; color:white; padding:2px 6px; font-size:11px; border-radius:4px;'>").append(rs.getString("action_type")).append("</span></td>");
                                html.append("<td>").append(rs.getString("details")).append("</td></tr>");
                            }
                        }
                    } catch (Exception e) {}
                    html.append("</tbody></table></div>");
                }
                else if (activeTab.equals("api")) {
                    html.append("<div class='card'><h3>🌐 Open Library JSON API Proxy Connection Portal</h3>");
                    html.append("<p style='font-size:13px; color:gray;'>Query metadata variables directly from external global open library registries and map records straight into local databases dynamically.</p>");
                    html.append("<div style='display:flex; gap:10px; margin-bottom:25px;'>");
                    html.append("<input type='text' id='apiSearchInput' style='padding:12px; width:75%; border-radius:6px; border:1px solid var(--border); background:var(--bg); color:var(--text);' placeholder='Query title fields global network registry space (e.g., Lord of the Rings)...' value='").append(apiSearchQuery).append("'/>");
                    html.append("<button class='btn' style='background:#0284c7;' onclick='runApiSearch()'>Execute Global Scan</button>");
                    html.append("</div>");
                    html.append("<script>function runApiSearch(){ var val=document.getElementById('apiSearchInput').value; window.location.href='/?tab=api&apiSearch='+encodeURIComponent(val); }</script>");

                    if (!apiSearchQuery.isEmpty()) {
                        html.append("<h4>Remote Cloud API Payload Results</h4><div style='display:grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap:15px;'>");
                        String jsonResponse = fetchOpenLibraryBooksJSON(apiSearchQuery);
                        if (!jsonResponse.isEmpty() && jsonResponse.contains("\"title\"")) {
                            int pointer = 0;
                            for (int k = 0; k < 3; k++) {
                                pointer = jsonResponse.indexOf("\"title\"", pointer);
                                if (pointer == -1) break;
                                int start = jsonResponse.indexOf(":", pointer) + 1;
                                while(jsonResponse.charAt(start) == ' ' || jsonResponse.charAt(start) == '"') start++;
                                int end = jsonResponse.indexOf("\"", start);
                                if (end == -1) break;
                                String cloudTitle = jsonResponse.substring(start, end);
                                pointer = end;

                                html.append("<div class='book-card' style='border:1px dashed #0284c7;'>");
                                html.append("<h5>").append(cloudTitle).append("</h5>");
                                html.append("<p style='font-size:11px; color:gray;'>Source Validation Context: Open Library API Cloud Metadata Record</p>");
                                if ("admin".equals(sessionUser)) {
                                    html.append("<form action='/importApiBook' method='POST'>");
                                    html.append("<input type='hidden' name='title' value='").append(cloudTitle.replace("'", "")).append("'/>");
                                    html.append("<button type='submit' class='btn' style='background:#0284c7; width:100%;'>Sync Dynamic Import</button>");
                                    html.append("</form>");
                                } else {
                                    html.append("<p style='font-size:11px; color:red;'>Administrator clearing token authority needed to synchronize database.</p>");
                                }
                                html.append("</div>");
                            }
                        } else {
                            html.append("<p style='color:gray;'>No data items returned inside cloud streaming array.</p>");
                        }
                        html.append("</div>");
                    }
                    html.append("</div>");
                }
                else if (activeTab.equals("add") && "admin".equals(sessionUser)) {
                    html.append("<div class='card'><h3>➕ Admin Management - Provision New Asset Records</h3>");
                    html.append("<form action='/addBook' method='POST' style='display:flex; flex-direction:column; gap:12px; max-width:400px;'>");
                    html.append("<label style='font-size:12px; font-weight:600;'>Book Resource Title Name:</label>");
                    html.append("<input type='text' name='title' style='padding:10px; border-radius:6px; border:1px solid var(--border); background:var(--bg); color:var(--text);' placeholder='Insert title string entry...' required autocomplete='off'/>");
                    html.append("<button type='submit' class='btn'>Commit Node to DB Catalog</button>");
                    html.append("</form></div>");
                }

                html.append("</div>"); // wrapper end
            }

            // High Fidelity Wattpad-Style Inline Content Reader Layer Frame
            html.append("<div id='readerPopup' class='reader-overlay' onclick='closeStoryReader()'>");
            html.append("<div class='reader-box' onclick='event.stopPropagation()'>");
            html.append("<h2 id='storyTitle' style='color:#111; font-family:serif; text-align:center; border-bottom:1px solid #ddd; padding-bottom:15px;'></h2>");
            html.append("<p style='margin-top:20px;'><b>Chapter 1: The Incarnation of Destiny Manifested</b></p>");
            html.append("<p>The storm howled against the towering glass structures of the metropolitan financial district. Inside the executive penthouse suite, the air was dense, filled with unuttered expectations. The data matrices flickered in amber patterns across monitors, signaling a cascading shift across international network frameworks.</p>");
            html.append("<p>He turned around slowly, his piercing gray eyes cutting through the darkness of the dimly lit room. \"We are missing a core synchronization segment,\" he whispered, his voice smooth yet commanding. Everything hung in a delicate balance, awaiting the arrival of the final encryption sequence...</p>");
            html.append("<p style='text-align:center; color:gray; font-size:14px; margin-top:40px;'>[End of Preview Segment. Click outside to return to catalogue shelf]</p>");
            html.append("</div></div>");

            html.append("</body></html>");
            byte[] responseBytes = html.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(responseBytes); }
        }

        private void displayValidationError(HttpExchange exchange, String errorMessage) throws IOException {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Validation Exception Exception</title>");
            html.append("<style>body{font-family:'Segoe UI',sans-serif; background-color:#f4f7f9; text-align:center; padding-top:100px;}");
            html.append(".error-box{max-width:500px; margin:0 auto; background:white; padding:40px; border-radius:12px; border-top:5px solid #ef4444; box-shadow:0 4px 15px rgba(0,0,0,0.05);}");
            html.append(".btn{background-color:#ff4500; color:white; padding:10px 20px; border:none; border-radius:30px; text-decoration:none; font-weight:600; cursor:pointer;}</style></head>");
            html.append("<body><div class='error-box'><h3 style='color:#ef4444;'>Validation Constraint Warning</h3>");
            html.append("<p style='color:#4a5568; margin-bottom:25px;'>").append(errorMessage).append("</p>");
            html.append("<a href='/' class='btn'>Return to Main Application Hub</a></div></body></html>");
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