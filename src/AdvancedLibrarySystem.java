import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class AdvancedLibrarySystem extends JFrame {

    // --- RELATIONAL SCHEMAS & TRANSLATION ENGINE ---
    private static final Pattern STUDENT_ID_PATTERN = Pattern.compile("^\\d{4}-\\d{5,7}$");
    private static final int DEFAULT_PORT = 8080;
    private HttpServer server;
    private static String dbUrlConnectionToken;

    // --- UI DESIGN STYLING ARCHITECTURE ---
    private final Color COLOR_BG = Color.WHITE;
    private final Color COLOR_PRIMARY = new Color(0, 90, 180);
    private final Color COLOR_ACCENT_BG = new Color(240, 246, 255);
    private final Color COLOR_BTN_GREY = new Color(230, 232, 235);
    private final Color COLOR_TEXT = Color.BLACK;

    private CardLayout cardLayout;
    private JPanel containerPanel;
    private JLabel lblWelcomeStatus, lblStudentHeader, lblAdminHeader, lblLiveClock;
    private DefaultTableModel modelStudentBooks, modelMyHistory, modelAdminBooks, modelAdminUsers, modelAdminLogs;
    private JTable tableStudentBooks, tableMyHistory, tableAdminBooks, tableAdminUsers, tableAdminLogs;

    private String loggedInUserKey = null;
    private boolean loggedInUserIsAdmin = false;
    private static final Map<String, String> webSessions = new HashMap<>();

    static {
        // Capture Railway native MySQL string parameters or fallback to local development environments
        String environmentUrl = System.getenv("MYSQL_URL");
        if (environmentUrl != null) {
            // Transform connection protocol formatting to standard Java JDBC sub-protocols
            dbUrlConnectionToken = environmentUrl.replace("mysql://", "jdbc:mysql://");
        } else {
            dbUrlConnectionToken = "jdbc:mysql://localhost:3306/library_db?user=root&password=password";
        }
    }

    public AdvancedLibrarySystem() {
        initializeDatabaseSchema();
        seedDataInventory();
        startLocalhostWebServer();

        boolean isHeadless = "true".equals(System.getProperty("java.awt.headless"));
        if (!isHeadless) {
            setupMainFrame();
        }
        schedulePHTimeRestarts();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrlConnectionToken);
    }

    private void initializeDatabaseSchema() {
        System.out.println("Connecting to database host: " + dbUrlConnectionToken);
        String sqlUsers = "CREATE TABLE IF NOT EXISTS users (" +
                          "school_id VARCHAR(50) PRIMARY KEY, " +
                          "is_admin BOOLEAN DEFAULT FALSE);";

        String sqlBooks = "CREATE TABLE IF NOT EXISTS books (" +
                          "id INT AUTO_INCREMENT PRIMARY KEY, " +
                          "title VARCHAR(255) NOT NULL, " +
                          "is_available BOOLEAN DEFAULT TRUE) AUTO_INCREMENT=1001;";

        String sqlRecords = "CREATE TABLE IF NOT EXISTS borrow_records (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "book_id INT, " +
                            "school_id VARCHAR(50), " +
                            "borrow_date DATE, " +
                            "due_date DATE, " +
                            "is_returned BOOLEAN DEFAULT FALSE);";

        String sqlLogs = "CREATE TABLE IF NOT EXISTS audit_logs (" +
                         "id INT AUTO_INCREMENT PRIMARY KEY, " +
                         "timestamp VARCHAR(30), " +
                         "category VARCHAR(50), " +
                         "message TEXT);";

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sqlUsers);
            stmt.execute(sqlBooks);
            stmt.execute(sqlRecords);
            stmt.execute(sqlLogs);
            System.out.println(">>> Relational database constraints and schema initialization verified successfully.");
        } catch (SQLException e) {
            System.err.println("Critical error building database architecture maps: " + e.getMessage());
        }
    }

    private void writeLog(String category, String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String sql = "INSERT INTO audit_logs (timestamp, category, message) VALUES (?, ?, ?);";

        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, timestamp);
            pstmt.setString(2, category);
            pstmt.setString(3, message);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed syncing audit transaction string to SQL server: " + e.getMessage());
        }

        if (!"true".equals(System.getProperty("java.awt.headless"))) {
            SwingUtilities.invokeLater(() -> { if (modelAdminLogs != null) refreshAdminTables(); });
        }
    }

    private void seedDataInventory() {
        try (Connection conn = getConnection()) {
            // Default Administrator Generation
            String checkAdmin = "SELECT COUNT(*) FROM users WHERE school_id = 'admin';";
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(checkAdmin)) {
                if (rs.next() && rs.getInt(1) == 0) {
                    st.executeUpdate("INSERT INTO users (school_id, is_admin) VALUES ('admin', true);");
                }
            }
            // Seed inventory assets if catalog tables evaluate to empty
            String checkBooks = "SELECT COUNT(*) FROM books;";
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(checkBooks)) {
                if (rs.next() && rs.getInt(1) == 0) {
                    String[] sampleTitles = {
                        "The Great Gatsby", "To Kill a Mockingbird", "1984", "Pride and Prejudice",
                        "The Catcher in the Rye", "The Hobbit", "Fahrenheit 451", "Jane Eyre"
                    };
                    String insertBook = "INSERT INTO books (title, is_available) VALUES (?, true);";
                    try (PreparedStatement pstmt = conn.prepareStatement(insertBook)) {
                        for (String title : sampleTitles) {
                            pstmt.setString(1, title);
                            pstmt.executeUpdate();
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Inventory tracking injection failed: " + e.getMessage());
        }
    }

    private void startLocalhostWebServer() {
        try {
            String portEnv = System.getenv("PORT");
            int port = (portEnv != null) ? Integer.parseInt(portEnv) : DEFAULT_PORT;
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new WebDashboardHandler());
            server.setExecutor(null);
            server.start();
            System.out.println(">>> Dynamic SQL Web Infrastructure Engine Live at Port Gateway: " + port);
        } catch (IOException e) {
            System.err.println("Web server configuration panic: " + e.getMessage());
        }
    }

    private void schedulePHTimeRestarts() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        ZoneId phZone = ZoneId.of("Asia/Manila");
        Runnable restartTask = () -> {
            if (server != null) {
                server.stop(1);
                startLocalhostWebServer();
            }
        };
        long delayTo6AM = calculateDelayToNextTargetTime(6, 0, phZone);
        scheduler.scheduleAtFixedRate(restartTask, delayTo6AM, 24 * 60, TimeUnit.MINUTES);
    }

    private long calculateDelayToNextTargetTime(int hour, int minute, ZoneId zone) {
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime nextTarget = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
        if (now.compareTo(nextTarget) > 0) nextTarget = nextTarget.plusDays(1);
        return Duration.between(now, nextTarget).toMinutes();
    }

    // --- WEB ARCHITECTURE CONTROLLER INTERFACES ---
    private class WebDashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            String username = getSessionUser(cookieHeader);

            if ("POST".equalsIgnoreCase(method)) {
                Map<String, String> postData = parsePostData(exchange);
                String action = postData.get("action");

                try (Connection conn = getConnection()) {
                    if ("login".equals(action)) {
                        String uid = postData.get("schoolId");
                        String sql = "SELECT is_admin FROM users WHERE school_id = ?;";
                        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                            pstmt.setString(1, uid);
                            try (ResultSet rs = pstmt.executeQuery()) {
                                if (rs.next()) {
                                    String sessionId = UUID.randomUUID().toString();
                                    webSessions.put(sessionId, uid);
                                    exchange.getResponseHeaders().add("Set-Cookie", "auth_session=" + sessionId + "; Path=/; HttpOnly");
                                    writeLog("logins", "Web User session authenticated: " + uid);
                                    redirect(exchange, "/");
                                    return;
                                } else {
                                    sendResponse(exchange, renderLoginPage("Error: Profile authentication missing."));
                                    return;
                                }
                            }
                        }
                    } else if ("register".equals(action)) {
                        String uid = postData.get("schoolId");
                        if (!STUDENT_ID_PATTERN.matcher(uid).matches() && !uid.equalsIgnoreCase("admin")) {
                            sendResponse(exchange, renderLoginPage("Error: Alignment identity format template rejected. Use YYYY-XXXXXX"));
                            return;
                        }
                        String sql = "INSERT INTO users (school_id, is_admin) VALUES (?, false);";
                        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                            pstmt.setString(1, uid);
                            pstmt.executeUpdate();
                            writeLog("logins", "Web registration entry cataloged: " + uid);
                        } catch (SQLException ex) {
                            sendResponse(exchange, renderLoginPage("Error: Duplicate identity catalog collision entry."));
                            return;
                        }
                        sendResponse(exchange, renderLoginPage("Success! Registration accepted. Launch session now."));
                        return;
                    } else if ("logout".equals(action)) {
                        if (username != null) writeLog("log out", "Session context flushed: " + username);
                        exchange.getResponseHeaders().add("Set-Cookie", "auth_session=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT");
                        redirect(exchange, "/");
                        return;
                    }

                    // Protected operations pipeline
                    if (username != null) {
                        boolean isAdmin = false;
                        try (PreparedStatement p = conn.prepareStatement("SELECT is_admin FROM users WHERE school_id=?;")) {
                            p.setString(1, username);
                            try (ResultSet r = p.executeQuery()) { if (r.next()) isAdmin = r.getBoolean("is_admin"); }
                        }

                        if (isAdmin && "addBook".equals(action)) {
                            String title = postData.get("bookTitle");
                            try (PreparedStatement p = conn.prepareStatement("INSERT INTO books (title, is_available) VALUES (?, true);")) {
                                p.setString(1, title); p.executeUpdate();
                                writeLog("book added", "Admin injected asset into system directories: " + title);
                            }
                            redirect(exchange, "/"); return;
                        } else if (isAdmin && "deleteBook".equals(action)) {
                            int bid = Integer.parseInt(postData.get("bookId"));
                            try (PreparedStatement p = conn.prepareStatement("DELETE FROM books WHERE id=?;")) {
                                p.setInt(1, bid); p.executeUpdate();
                                writeLog("remove books", "Admin removed asset reference token: " + bid);
                            }
                            redirect(exchange, "/"); return;
                        }

                        if (!isAdmin && "borrow".equals(action)) {
                            int bid = Integer.parseInt(postData.get("bookId"));
                            try (PreparedStatement p1 = conn.prepareStatement("UPDATE books SET is_available=false WHERE id=? AND is_available=true;")) {
                                p1.setInt(1, bid);
                                if (p1.executeUpdate() > 0) {
                                    try (PreparedStatement p2 = conn.prepareStatement("INSERT INTO borrow_records (book_id, school_id, borrow_date, due_date, is_returned) VALUES (?, ?, CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), false);")) {
                                        p2.setInt(1, bid); p2.setString(2, username); p2.executeUpdate();
                                    }
                                    writeLog("borrowed", "User " + username + " systematically extracted asset ID: " + bid);
                                }
                            }
                            redirect(exchange, "/"); return;
                        } else if (!isAdmin && "return".equals(action)) {
                            int bid = Integer.parseInt(postData.get("bookId"));
                            try (PreparedStatement p1 = conn.prepareStatement("UPDATE borrow_records SET is_returned=true WHERE book_id=? AND school_id=? AND is_returned=false;")) {
                                p1.setInt(1, bid); p1.setString(2, username);
                                if (p1.executeUpdate() > 0) {
                                    try (PreparedStatement p2 = conn.prepareStatement("UPDATE books SET is_available=true WHERE id=?;")) {
                                        p2.setInt(1, bid); p2.executeUpdate();
                                    }
                                    writeLog("returned", "User " + username + " check-in cycle closing complete for Asset ID: " + bid);
                                }
                            }
                            redirect(exchange, "/"); return;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("System runtime thread error inside pipeline handler: " + e.getMessage());
                }
            }

            if (username == null) {
                sendResponse(exchange, renderLoginPage(""));
            } else {
                try (Connection conn = getConnection(); PreparedStatement p = conn.prepareStatement("SELECT is_admin FROM users WHERE school_id=?;")) {
                    p.setString(1, username);
                    try (ResultSet r = p.executeQuery()) {
                        if (r.next() && r.getBoolean("is_admin")) sendResponse(exchange, renderAdminDashboardPage(username));
                        else sendResponse(exchange, renderDashboardPage(username));
                    }
                } catch (SQLException e) {
                    sendResponse(exchange, "Internal relational node architecture communication breakdown.");
                }
            }
        }

        private String getSessionUser(String cookieHeader) {
            if (cookieHeader == null) return null;
            for (String cookie : cookieHeader.split(";")) {
                String[] parts = cookie.trim().split("=");
                if (parts.length == 2 && "auth_session".equals(parts[0])) return webSessions.get(parts[1]);
            }
            return null;
        }

        private Map<String, String> parsePostData(HttpExchange exchange) throws IOException {
            Map<String, String> result = new HashMap<>();
            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr);
            String value = br.readLine();
            if (value != null) {
                for (String pair : value.split("&")) {
                    String[] idx = pair.split("=");
                    if (idx.length == 2) result.put(URLDecoder.decode(idx[0], StandardCharsets.UTF_8), URLDecoder.decode(idx[1], StandardCharsets.UTF_8));
                }
            }
            return result;
        }

        private void redirect(HttpExchange exchange, String target) throws IOException {
            exchange.getResponseHeaders().set("Location", target);
            exchange.sendResponseHeaders(303, -1);
        }

        private void sendResponse(HttpExchange exchange, String html) throws IOException {
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        }

        private String renderLoginPage(String msg) {
            return "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>SQL System Entrance</title><style>body{font-family:'Segoe UI',sans-serif;background:#f0f4f8;display:flex;justify-content:center;align-items:center;height:100vh;margin:0;}.card{background:white;padding:40px;border-radius:8px;box-shadow:0 4px 15px rgba(0,0,0,0.1);width:320px;border-top:5px solid #005abe;}h2{margin-top:0;color:#333;text-align:center;}input[type=text]{width:100%;padding:10px;margin:10px 0;box-sizing:border-box;border:1px solid #ccc;border-radius:4px;}button{width:100%;padding:10px;margin:5px 0;border:none;border-radius:4px;font-weight:bold;cursor:pointer;}.btn-login{background:#005abe;color:white;}.btn-reg{background:#e6e8eb;color:#333;}.alert{color:#d32f2f;background:#ffebee;padding:8px;border-radius:4px;font-size:13px;margin-bottom:10px;text-align:center;}</style></head><body><div class='card'><h2>Database Portal Core</h2>" + (msg.isEmpty() ? "" : "<div class='alert'>" + msg + "</div>") + "<form method='POST'><label style='font-size:13px;color:#666;'>Identity Token Security Key:</label><input type='text' name='schoolId' placeholder='e.g., admin or 2026-98765' required><button type='submit' name='action' value='login' class='btn-login'>Open Connection Dashboard</button><button type='submit' name='action' value='register' class='btn-reg'>Register New Index Token</button></form></div></body></html>";
        }

        private String renderAdminDashboardPage(String user) {
            StringBuilder html = new StringBuilder("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Cloud Console</title><style>body{font-family:'Segoe UI',sans-serif;margin:30px;background:#f8f9fa;color:#333;}.header{display:flex;justify-content:between;background:#b71c1c;color:white;padding:15px;border-radius:6px;align-items:center;margin-bottom:20px;}table{width:100%;border-collapse:collapse;background:white;margin-bottom:25px;}th,td{padding:10px;border:1px solid #dee2e6;text-align:left;}th{background:#ffebee;}.btn-del{background:#d32f2f;color:white;border:none;padding:5px 10px;border-radius:4px;cursor:pointer;}.btn-logout{background:#333;color:white;border:none;padding:8px 15px;border-radius:4px;cursor:pointer;margin-left:auto;}.add-box{background:white;padding:15px;border:1px solid #dee2e6;border-radius:6px;margin-bottom:25px;}input[type=text]{padding:6px;width:250px;margin-right:10px;}</style></head><body>");
            html.append("<div class='header'><h2>Hello, ").append(user).append("! (ADMIN MODE)</h2><form method='POST' style='margin-left:auto;'><button type='submit' name='action' value='logout' class='btn-logout'>Disconnect</button></form></div>");
            html.append("<div class='add-box'><h3>Inject New Book Resource</h3><form method='POST'><input type='text' name='bookTitle' placeholder='Asset Identifier String' required><button type='submit' name='action' value='addBook' style='padding:6px 15px;background:#4caf50;color:white;border:none;border-radius:4px;cursor:pointer;'>Commit Resource</button></form></div>");

            html.append("<h3>Physical Inventory Ledger (SQL Linked)</h3><table><tr><th>ID</th><th>Resource Title</th><th>Inventory State</th><th>Controls</th></tr>");
            try (Connection conn = getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM books;")) {
                while(rs.next()){
                    html.append("<tr><td>").append(rs.getInt("id")).append("</td><td>").append(rs.getString("title")).append("</td><td>").append(rs.getBoolean("is_available") ? "In Vault" : "Circulating Out").append("</td><td><form method='POST'><input type='hidden' name='bookId' value='").append(rs.getInt("id")).append("'><button type='submit' name='action' value='deleteBook' class='btn-del'>Purge Asset Record</button></form></td></tr>");
                }
            } catch(Exception e){}
            html.append("</table>");
            return html.append("</body></html>").toString();
        }

        private String renderDashboardPage(String user) {
            StringBuilder html = new StringBuilder("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>User Deck</title><style>body{font-family:'Segoe UI',sans-serif;margin:30px;background:#f8f9fa;color:#333;}.header{display:flex;justify-content:between;background:#005abe;color:white;padding:15px;border-radius:6px;align-items:center;margin-bottom:20px;}table{width:100%;border-collapse:collapse;background:white;margin-bottom:25px;}th,td{padding:10px;border:1px solid #dee2e6;text-align:left;}th{background:#f0f6ff;}.btn-action{background:#005abe;color:white;border:none;padding:5px 10px;border-radius:4px;cursor:pointer;}.btn-logout{background:#f44336;color:white;border:none;padding:8px 15px;border-radius:4px;cursor:pointer;margin-left:auto;}</style></head><body>");
            html.append("<div class='header'><h2>Welcome, ").append(user).append(" | Student Deck</h2><form method='POST' style='margin-left:auto;'><button type='submit' name='action' value='logout' class='btn-logout'>Exit</button></form></div>");

            html.append("<h3>Active Catalog Indexes</h3><table><tr><th>ID</th><th>Book Title</th><th>Status</th><th>Operation Target</th></tr>");
            try (Connection conn = getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM books;")) {
                while(rs.next()){
                    html.append("<tr><td>").append(rs.getInt("id")).append("</td><td>").append(rs.getString("title")).append("</td><td>").append(rs.getBoolean("is_available") ? "Available" : "Checked Out").append("</td><td>");
                    if (rs.getBoolean("is_available")) {
                        html.append("<form method='POST'><input type='hidden' name='bookId' value='").append(rs.getInt("id")).append("'><button type='submit' name='action' value='borrow' class='btn-action'>Borrow Book</button></form>");
                    } else { html.append("<span style='color:grey;'>Locked</span>"); }
                    html.append("</td></tr>");
                }
            } catch(Exception e){}
            html.append("</table><h3>My Active Borrow Vault Transactions</h3><table><tr><th>Book ID</th><th>Title</th><th>Due Date</th><th>Action Trigger</th></tr>");

            try (Connection conn = getConnection(); PreparedStatement p = conn.prepareStatement("SELECT r.book_id, b.title, r.due_date FROM borrow_records r JOIN books b ON r.book_id = b.id WHERE r.school_id=? AND r.is_returned=false;")) {
                p.setString(1, user);
                try (ResultSet rs = p.executeQuery()) {
                    while(rs.next()){
                        html.append("<tr><td>").append(rs.getInt("book_id")).append("</td><td>").append(rs.getString("title")).append("</td><td>").append(rs.getDate("due_date")).append("</td><td><form method='POST'><input type='hidden' name='bookId' value='").append(rs.getInt("book_id")).append("'><button type='submit' name='action' value='return' class='btn-action' style='background:#4caf50;'>Return To Desk</button></form></td></tr>");
                    }
                }
            } catch(Exception e){}
            return html.append("</table></body></html>").toString();
        }
    }

    // --- SWING LOCAL DESKTOP COMPONENT GRAPHICS LAYER ---
    private void setupMainFrame() {
        setTitle("SQL Enterprise Management Network Console");
        setSize(950, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        containerPanel = new JPanel(cardLayout);
        containerPanel.setBackground(COLOR_BG);

        containerPanel.add(buildLoginPanel(), "ScreenLogin");
        containerPanel.add(buildStudentPanel(), "ScreenStudent");
        containerPanel.add(buildAdminPanel(), "ScreenAdmin");

        JPanel frameLayout = new JPanel(new BorderLayout());
        frameLayout.add(containerPanel, BorderLayout.CENTER);
        frameLayout.add(buildLiveStatusBar(), BorderLayout.SOUTH);

        add(frameLayout);
        cardLayout.show(containerPanel, "ScreenLogin");
    }

    private JPanel buildLiveStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(COLOR_ACCENT_BG);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, COLOR_PRIMARY), new EmptyBorder(5, 15, 5, 15)));
        lblWelcomeStatus = new JLabel("SQL Core Active. Authenticate credentials framework context.");
        lblLiveClock = new JLabel();
        String portEnv = System.getenv("PORT");
        int port = (portEnv != null) ? Integer.parseInt(portEnv) : DEFAULT_PORT;

        new javax.swing.Timer(1000, e -> {
            lblLiveClock.setText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " | Web Gateway Port: " + port);
        }).start();

        bar.add(lblWelcomeStatus, BorderLayout.WEST); bar.add(lblLiveClock, BorderLayout.EAST);
        return bar;
    }

    private void styleButton(JButton btn) {
        btn.setBackground(COLOR_BTN_GREY); btn.setForeground(COLOR_TEXT);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12)); btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(new LineBorder(COLOR_PRIMARY, 1), BorderFactory.createEmptyBorder(6, 16, 6, 16)));
    }

    private void styleTable(JTable tbl) {
        tbl.setBackground(COLOR_BG); tbl.setForeground(COLOR_TEXT);
        tbl.setSelectionBackground(COLOR_ACCENT_BG); tbl.setSelectionForeground(COLOR_TEXT);
        tbl.setRowHeight(26); tbl.setGridColor(COLOR_BTN_GREY);
    }

    private JPanel buildLoginPanel() {
        JPanel main = new JPanel(new GridBagLayout()); main.setBackground(COLOR_BG);
        JPanel card = new JPanel(); card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(COLOR_BG); card.setBorder(BorderFactory.createCompoundBorder(new LineBorder(COLOR_PRIMARY, 2), BorderFactory.createEmptyBorder(35, 45, 35, 45)));

        JLabel title = new JLabel("SQL Auth Engine Node"); title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        JTextField inputIdField = new JTextField(16); inputIdField.setMaximumSize(new Dimension(260, 32));
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10)); actionRow.setBackground(COLOR_BG);
        JButton btnLogin = new JButton("Login Matrix"); JButton btnRegister = new JButton("Register Node");
        styleButton(btnLogin); styleButton(btnRegister); actionRow.add(btnLogin); actionRow.add(btnRegister);

        card.add(title); card.add(Box.createVerticalStrut(25)); card.add(inputIdField); card.add(Box.createVerticalStrut(20)); card.add(actionRow); main.add(card);

        btnLogin.addActionListener(e -> {
            String uid = inputIdField.getText().trim();
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement("SELECT is_admin FROM users WHERE school_id=?;")) {
                pstmt.setString(1, uid);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        loggedInUserKey = uid; loggedInUserIsAdmin = rs.getBoolean("is_admin");
                        writeLog("logins", "Local desktop user workspace initialized context: " + uid);
                        if (loggedInUserIsAdmin) { refreshAdminTables(); cardLayout.show(containerPanel, "ScreenAdmin"); }
                        else { refreshStudentCatalog(); refreshStudentPersonalLog(); cardLayout.show(containerPanel, "ScreenStudent"); }
                    } else { JOptionPane.showMessageDialog(this, "Identity reference footprint missing from MySQL cluster registry."); }
                }
            } catch(Exception ex) {}
        });

        btnRegister.addActionListener(e -> {
            String uid = inputIdField.getText().trim();
            if (!STUDENT_ID_PATTERN.matcher(uid).matches() && !uid.equalsIgnoreCase("admin")) return;
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement("INSERT INTO users (school_id, is_admin) VALUES (?, false);")) {
                pstmt.setString(1, uid); pstmt.executeUpdate();
                writeLog("logins", "Injected identity schema entry token layout: " + uid);
                JOptionPane.showMessageDialog(this, "SQL Node Registered successfully.");
            } catch(Exception ex) { JOptionPane.showMessageDialog(this, "Collision detected."); }
        });
        return main;
    }

    private JPanel buildStudentPanel() {
        JPanel main = new JPanel(new BorderLayout()); main.setBackground(COLOR_BG);
        modelStudentBooks = new DefaultTableModel(new String[]{"Asset ID", "Resource Title", "Availability Status"}, 0);
        tableStudentBooks = new JTable(modelStudentBooks); styleTable(tableStudentBooks);
        main.add(new JScrollPane(tableStudentBooks), BorderLayout.CENTER);

        JButton btnBorrow = new JButton("Borrow Selected Book Asset"); styleButton(btnBorrow);
        JPanel p = new JPanel(); p.add(btnBorrow); main.add(p, BorderLayout.SOUTH);

        btnBorrow.addActionListener(e -> {
            int row = tableStudentBooks.getSelectedRow(); if (row == -1) return;
            int bid = (int) modelStudentBooks.getValueAt(row, 0);
            try (Connection conn = getConnection(); PreparedStatement p1 = conn.prepareStatement("UPDATE books SET is_available=false WHERE id=? AND is_available=true;")) {
                p1.setInt(1, bid);
                if (p1.executeUpdate() > 0) {
                    try (PreparedStatement p2 = conn.prepareStatement("INSERT INTO borrow_records (book_id, school_id, borrow_date, due_date) VALUES (?, ?, CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY));")) {
                        p2.setInt(1, bid); p2.setString(2, loggedInUserKey); p2.executeUpdate();
                    }
                    writeLog("borrowed", "Local User extraction successful for ID: " + bid);
                }
            } catch(Exception ex){}
            refreshStudentCatalog();
        });
        return main;
    }

    private JPanel buildAdminPanel() {
        JPanel main = new JPanel(new BorderLayout());
        modelAdminBooks = new DefaultTableModel(new String[]{"ID", "Title"}, 0);
        tableAdminBooks = new JTable(modelAdminBooks); styleTable(tableAdminBooks);
        main.add(new JScrollPane(tableAdminBooks), BorderLayout.CENTER);
        return main;
    }

    private void refreshStudentCatalog() {
        modelStudentBooks.setRowCount(0);
        try (Connection conn = getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM books;")) {
            while(rs.next()) modelStudentBooks.addRow(new Object[]{rs.getInt("id"), rs.getString("title"), rs.getBoolean("is_available") ? "Available" : "Circulating"});
        } catch(Exception e){}
    }

    private void refreshStudentPersonalLog() {}
    private void refreshAdminTables() {}

    public static void main(String[] args) {
        if (System.getenv("PORT") != null) {
            System.setProperty("java.awt.headless", "true");
            new AdvancedLibrarySystem();
        } else {
            SwingUtilities.invokeLater(() -> new AdvancedLibrarySystem().setVisible(true));
        }
    }
}