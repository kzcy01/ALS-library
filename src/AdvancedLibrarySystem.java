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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class AdvancedLibrarySystem extends JFrame {

    // --- DATA MODELS ---
    static class Book {
        int id;
        String title;
        boolean isAvailable;

        public Book(int id, String title) {
            this.id = id;
            this.title = title;
            this.isAvailable = true;
        }
    }

    static class User {
        String schoolId;
        boolean isAdmin;
        public User(String schoolId, boolean isAdmin) {
            this.schoolId = schoolId;
            this.isAdmin = isAdmin;
        }
    }

    static class BorrowRecord {
        int bookId;
        String bookTitle;
        String schoolId;
        LocalDate borrowDate;
        LocalDate dueDate;
        boolean isReturned;

        public BorrowRecord(int bookId, String bookTitle, String schoolId) {
            this.bookId = bookId;
            this.bookTitle = bookTitle;
            this.schoolId = schoolId;
            this.borrowDate = LocalDate.now();
            this.dueDate = this.borrowDate.plusDays(14);
            this.isReturned = false;
        }
    }

    // --- SYSTEM STORAGE REGISTRIES ---
    private static final List<Book> books = new ArrayList<>();
    private static final Map<String, User> users = new HashMap<>();
    private static final List<BorrowRecord> borrowRecords = new ArrayList<>();
    private static final List<String> globalActivityLogs = new ArrayList<>();
    private static final Map<String, String> webSessions = new HashMap<>();

    private User loggedInUser = null;
    private static final Pattern STUDENT_ID_PATTERN = Pattern.compile("^\\d{4}-\\d{5,7}$");
    private static final int DEFAULT_PORT = 8080;
    private HttpServer server;

    // --- DYNAMIC DATABASE STORAGE PATH CONFIGURATOR ---
    private static final String BASE_DATA_PATH;
    static {
        // If running on Railway with a persistent volume mounted, write directly to the persistent disk path
        if (System.getenv("RAILWAY_VOLUME_MOUNT_PATH") != null) {
            BASE_DATA_PATH = System.getenv("RAILWAY_VOLUME_MOUNT_PATH") + "/Data log/";
        } else {
            BASE_DATA_PATH = "Data log/";
        }
    }

    // --- UI DESIGN STYLING ARCHITECTURE ---
    private final Color COLOR_BG = Color.WHITE;
    private final Color COLOR_PRIMARY = new Color(0, 90, 180);
    private final Color COLOR_ACCENT_BG = new Color(240, 246, 255);
    private final Color COLOR_BTN_GREY = new Color(230, 232, 235);
    private final Color COLOR_TEXT = Color.BLACK;

    private CardLayout cardLayout;
    private JPanel containerPanel;

    private JLabel lblWelcomeStatus;
    private JLabel lblStudentHeader;
    private JLabel lblAdminHeader;
    private JLabel lblLiveClock;

    private DefaultTableModel modelStudentBooks, modelMyHistory;
    private DefaultTableModel modelAdminBooks, modelAdminUsers, modelAdminLogs;
    private JTable tableStudentBooks, tableMyHistory;
    private JTable tableAdminBooks, tableAdminUsers, tableAdminLogs;

    public AdvancedLibrarySystem() {
        initializeDirectoryStructure();
        seedDataInventory();
        startLocalhostWebServer();

        if (System.getProperty("java.awt.headless") == null || !System.getProperty("java.awt.headless").equals("true")) {
            setupMainFrame();
        }

        schedulePHTimeRestarts();
    }

    private void initializeDirectoryStructure() {
        String[] subFolders = {"logins", "borrowed", "returned", "log out", "book added", "remove books"};
        for (String sub : subFolders) {
            File folder = new File(BASE_DATA_PATH + sub);
            if (!folder.exists()) folder.mkdirs();
        }
    }

    private void writeLog(String subFolder, String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String entry = "[" + timestamp + "] " + message;

        synchronized (globalActivityLogs) {
            globalActivityLogs.add(entry);
        }

        try (FileWriter fw = new FileWriter(BASE_DATA_PATH + subFolder + "/audit.txt", true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println(entry);
        } catch (IOException e) {
            System.err.println("Error writing to persistent storage logs: " + e.getMessage());
        }

        if (System.getProperty("java.awt.headless") == null || !System.getProperty("java.awt.headless").equals("true")) {
            SwingUtilities.invokeLater(() -> {
                if (modelAdminLogs != null) {
                    refreshAdminTables();
                }
            });
        }
    }

    private void startLocalhostWebServer() {
        try {
            // Railway auto-assigns the system port dynamically
            String portEnv = System.getenv("PORT");
            int port = (portEnv != null) ? Integer.parseInt(portEnv) : DEFAULT_PORT;

            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new WebDashboardHandler());
            server.setExecutor(null);
            server.start();
            System.out.println(">>> Dynamic Data Sync Network Engine live at Gateway Port: " + port);
        } catch (IOException e) {
            System.err.println("Failed to start web server: " + e.getMessage());
        }
    }

    private void schedulePHTimeRestarts() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        ZoneId phZone = ZoneId.of("Asia/Manila");

        Runnable restartTask = () -> {
            System.out.println("[SYSTEM ALERT] Dynamic cycle initiated...");
            if (server != null) {
                server.stop(1);
                startLocalhostWebServer();
            }
        };

        long delayTo6AM = calculateDelayToNextTargetTime(6, 0, phZone);
        long delayTo6PM = calculateDelayToNextTargetTime(18, 0, phZone);

        scheduler.scheduleAtFixedRate(restartTask, delayTo6AM, 24 * 60, TimeUnit.MINUTES);
        scheduler.scheduleAtFixedRate(restartTask, delayTo6PM, 24 * 60, TimeUnit.MINUTES);
    }

    private long calculateDelayToNextTargetTime(int hour, int minute, ZoneId zone) {
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime nextTarget = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
        if (now.compareTo(nextTarget) > 0) {
            nextTarget = nextTarget.plusDays(1);
        }
        return Duration.between(now, nextTarget).toMinutes();
    }

    private class WebDashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            String username = getSessionUser(cookieHeader);

            boolean isHeadless = "true".equals(System.getProperty("java.awt.headless"));

            if ("POST".equalsIgnoreCase(method)) {
                Map<String, String> postData = parsePostData(exchange);
                String action = postData.get("action");

                if ("login".equals(action)) {
                    String uid = postData.get("schoolId");
                    if (users.containsKey(uid)) {
                        String sessionId = UUID.randomUUID().toString();
                        webSessions.put(sessionId, uid);
                        exchange.getResponseHeaders().add("Set-Cookie", "auth_session=" + sessionId + "; Path=/; HttpOnly");
                        writeLog("logins", "Web User logged in: " + uid);
                        redirect(exchange, "/");
                        return;
                    } else {
                        sendResponse(exchange, renderLoginPage("Error: User identity ID not found. Register first."));
                        return;
                    }
                } else if ("register".equals(action)) {
                    String uid = postData.get("schoolId");
                    if (!STUDENT_ID_PATTERN.matcher(uid).matches() && !uid.equalsIgnoreCase("admin")) {
                        sendResponse(exchange, renderLoginPage("Error: Invalid registration format. Must be YYYY-XXXXXX"));
                        return;
                    }
                    if (users.containsKey(uid)) {
                        sendResponse(exchange, renderLoginPage("Error: ID already registered."));
                        return;
                    } else {
                        users.put(uid, new User(uid, false));
                        writeLog("logins", "Web User registered account: " + uid);
                        if (!isHeadless) {
                            SwingUtilities.invokeLater(() -> refreshAdminTables());
                        }
                        sendResponse(exchange, renderLoginPage("Success! Account created. You can now log in."));
                        return;
                    }
                } else if ("logout".equals(action)) {
                    if (cookieHeader != null) {
                        writeLog("log out", "Web session disconnected: " + username);
                    }
                    exchange.getResponseHeaders().add("Set-Cookie", "auth_session=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT");
                    redirect(exchange, "/");
                    return;
                }

                if (username != null && users.get(username).isAdmin) {
                    if ("addBook".equals(action)) {
                        String title = postData.get("bookTitle");
                        if (title != null && !title.trim().isEmpty()) {
                            synchronized (books) {
                                int nextId = 1001 + books.size();
                                books.add(new Book(nextId, title.trim()));
                                writeLog("book added", "Web ADMIN added resource: ID " + nextId + " [" + title + "]");
                            }
                            if (!isHeadless) {
                                SwingUtilities.invokeLater(() -> { refreshStudentCatalog(); refreshAdminTables(); });
                            }
                        }
                        redirect(exchange, "/");
                        return;
                    } else if ("deleteBook".equals(action)) {
                        try {
                            int bid = Integer.parseInt(postData.get("bookId"));
                            synchronized (books) {
                                for (int i = 0; i < books.size(); i++) {
                                    if (books.get(i).id == bid) {
                                        writeLog("remove books", "Web ADMIN removed resource ID: " + bid + " [" + books.get(i).title + "]");
                                        books.remove(i);
                                        break;
                                    }
                                }
                            }
                            if (!isHeadless) {
                                SwingUtilities.invokeLater(() -> { refreshStudentCatalog(); refreshAdminTables(); });
                            }
                        } catch (Exception e) {}
                        redirect(exchange, "/");
                        return;
                    }
                }

                if (username != null && !users.get(username).isAdmin) {
                    if ("borrow".equals(action)) {
                        try {
                            int bid = Integer.parseInt(postData.get("bookId"));
                            synchronized (books) {
                                for (Book b : books) {
                                    if (b.id == bid && b.isAvailable) {
                                        b.isAvailable = false;
                                        borrowRecords.add(new BorrowRecord(b.id, b.title, username));
                                        writeLog("borrowed", "Web User " + username + " borrowed Asset: " + b.title);
                                        break;
                                    }
                                }
                            }
                            if (!isHeadless) {
                                SwingUtilities.invokeLater(() -> { refreshStudentCatalog(); refreshAdminTables(); });
                            }
                        } catch (Exception e) {}
                        redirect(exchange, "/");
                        return;
                    } else if ("return".equals(action)) {
                        try {
                            int bid = Integer.parseInt(postData.get("bookId"));
                            synchronized (borrowRecords) {
                                for (BorrowRecord r : borrowRecords) {
                                    if (r.bookId == bid && r.schoolId.equals(username) && !r.isReturned) {
                                        r.isReturned = true;
                                        break;
                                    }
                                }
                            }
                            synchronized (books) {
                                for (Book b : books) {
                                    if (b.id == bid) {
                                        b.isAvailable = true;
                                        writeLog("returned", "Web User " + username + " returned Asset ID: " + bid);
                                        break;
                                    }
                                }
                            }
                            if (!isHeadless) {
                                SwingUtilities.invokeLater(() -> { refreshStudentCatalog(); refreshAdminTables(); });
                            }
                        } catch (Exception e) {}
                        redirect(exchange, "/");
                        return;
                    }
                }
            }

            if (username == null) {
                sendResponse(exchange, renderLoginPage(""));
            } else {
                User userObj = users.get(username);
                if (userObj != null && userObj.isAdmin) {
                    sendResponse(exchange, renderAdminDashboardPage(username));
                } else {
                    sendResponse(exchange, renderDashboardPage(username));
                }
            }
        }

        private String getSessionUser(String cookieHeader) {
            if (cookieHeader == null) return null;
            for (String cookie : cookieHeader.split(";")) {
                String[] parts = cookie.trim().split("=");
                if (parts.length == 2 && "auth_session".equals(parts[0])) {
                    return webSessions.get(parts[1]);
                }
            }
            return null;
        }

        private Map<String, String> parsePostData(HttpExchange exchange) throws IOException {
            Map<String, String> result = new HashMap<>();
            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr);
            String value = br.readLine();
            if (value != null) {
                String[] pairs = value.split("&");
                for (String pair : pairs) {
                    String[] idx = pair.split("=");
                    if (idx.length == 2) {
                        result.put(URLDecoder.decode(idx[0], StandardCharsets.UTF_8), URLDecoder.decode(idx[1], StandardCharsets.UTF_8));
                    }
                }
            }
            return result;
        }

        private void redirect(HttpExchange exchange, String target) throws IOException {
            exchange.getResponseHeaders().set("Location", target);
            exchange.sendResponseHeaders(303, -1);
        }

        private void sendResponse(HttpExchange exchange, String html) throws IOException {
            byte[] responseBytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }

        private String renderLoginPage(String alertMsg) {
            return "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Library Portal Access</title>" +
                    "<style>body{font-family:'Segoe UI',sans-serif;background:#f0f4f8;display:flex;justify-content:center;align-items:center;height:100vh;margin:0;}" +
                    ".card{background:white;padding:40px;border-radius:8px;box-shadow:0 4px 15px rgba(0,0,0,0.1);width:320px;border-top:5px solid #005abe;}" +
                    "h2{margin-top:0;color:#333;text-align:center;}input[type=text]{width:100%;padding:10px;margin:10px 0;box-sizing:border-box;border:1px solid #ccc;border-radius:4px;}" +
                    "button{width:100%;padding:10px;margin:5px 0;border:none;border-radius:4px;font-weight:bold;cursor:pointer;}" +
                    ".btn-login{background:#005abe;color:white;}.btn-reg{background:#e6e8eb;color:#333;}" +
                    ".alert{color:#d32f2f;background:#ffebee;padding:8px;border-radius:4px;font-size:13px;margin-bottom:10px;text-align:center;}</style></head><body>" +
                    "<div class='card'><h2>Library Web Access</h2>" +
                    (alertMsg.isEmpty() ? "" : "<div class='alert'>" + alertMsg + "</div>") +
                    "<form method='POST'>" +
                    "<label style='font-size:13px;color:#666;'>Identity Token/ID Key:</label>" +
                    "<input type='text' name='schoolId' placeholder='e.g., admin or 2026-10234' required>" +
                    "<button type='submit' name='action' value='login' class='btn-login'>Login Session</button>" +
                    "<button type='submit' name='action' value='register' class='btn-reg'>Register ID Account</button>" +
                    "</form></div></body></html>";
        }

        private String renderAdminDashboardPage(String user) {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Admin Console</title>");
            html.append("<style>body{font-family:'Segoe UI',sans-serif;margin:30px;background:#f8f9fa;color:#333;}");
            html.append(".header{display:flex;justify-content:between;background:#b71c1c;color:white;padding:15px;border-radius:6px;align-items:center;margin-bottom:20px;}");
            html.append("table{width:100%;border-collapse:collapse;background:white;margin-bottom:25px;} th,td{padding:10px;border:1px solid #dee2e6;text-align:left;} th{background:#ffebee;}");
            html.append(".btn-del{background:#d32f2f;color:white;border:none;padding:5px 10px;border-radius:4px;cursor:pointer;} .btn-logout{background:#333;color:white;border:none;padding:8px 15px;border-radius:4px;cursor:pointer;margin-left:auto;}");
            html.append(".add-box{background:white;padding:15px;border:1px solid #dee2e6;border-radius:6px;margin-bottom:25px;} input[type=text]{padding:6px;width:250px;margin-right:10px;}</style></head><body>");

            html.append("<div class='header'>")
                .append("<h2 style='margin:0;'>Hello, ").append(user).append("! (ADMIN WEB DASHBOARD)</h2>")
                .append("<form method='POST' style='margin-left:auto;'><button type='submit' name='action' value='logout' class='btn-logout'>Logout</button></form>")
                .append("</div>");

            html.append("<div class='add-box'><h3>Add New Book Asset</h3>")
                .append("<form method='POST'><input type='text' name='bookTitle' placeholder='Resource Title' required>")
                .append("<button type='submit' name='action' value='addBook' style='padding:6px 15px;background:#4caf50;color:white;border:none;border-radius:4px;cursor:pointer;'>Add Asset</button></form>")
                .append("</div>");

            html.append("<h3>Physical Catalog Inventory</h3><table><tr><th>Asset ID</th><th>Resource Title</th><th>Status</th><th>Operation</th></tr>");
            synchronized (books) {
                for (Book b : books) {
                    html.append("<tr><td>").append(b.id).append("</td><td>").append(b.title).append("</td><td>")
                        .append(b.isAvailable ? "Available" : "Borrowed Out").append("</td><td>")
                        .append("<form method='POST' style='display:inline;'><input type='hidden' name='bookId' value='").append(b.id).append("'><button type='submit' name='action' value='deleteBook' class='btn-del'>Delete Book</button></form>")
                        .append("</td></tr>");
                }
            }
            html.append("</table>");

            html.append("<h3>Registered User System Directories</h3><table><tr><th>User ID Key</th><th>Privilege Status</th></tr>");
            synchronized (users) {
                for (User u : users.values()) {
                    html.append("<tr><td>").append(u.schoolId).append("</td><td>").append(u.isAdmin ? "Administrator" : "Standard Student").append("</td></tr>");
                }
            }
            html.append("</table>");

            html.append("<h3>Real-Time Operations Log (Newest First)</h3><div style='background:white;padding:15px;border:1px solid #dee2e6;height:200px;overflow-y:scroll;font-family:monospace;'>");
            synchronized (globalActivityLogs) {
                for (int i = globalActivityLogs.size() - 1; i >= 0; i--) {
                    html.append("<div>").append(globalActivityLogs.get(i)).append("</div>");
                }
            }
            html.append("</div></body></html>");

            return html.toString();
        }

        private String renderDashboardPage(String user) {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Dashboard</title>");
            html.append("<style>body{font-family:'Segoe UI',sans-serif;margin:30px;background:#f8f9fa;color:#333;}");
            html.append(".header{display:flex;justify-content:between;background:#005abe;color:white;padding:15px;border-radius:6px;align-items:center;margin-bottom:20px;}");
            html.append("table{width:100%;border-collapse:collapse;background:white;margin-bottom:25px;} th,td{padding:10px;border:1px solid #dee2e6;text-align:left;} th{background:#f0f6ff;}");
            html.append(".btn-action{background:#005abe;color:white;border:none;padding:5px 10px;border-radius:4px;cursor:pointer;} .btn-logout{background:#f44336;color:white;border:none;padding:8px 15px;border-radius:4px;cursor:pointer;margin-left:auto;}</style></head><body>");

            html.append("<div class='header'>")
                .append("<h2 style='margin:0;'>Hello, ").append(user).append("! | Student Portal</h2>")
                .append("<form method='POST' style='margin-left:auto;'><button type='submit' name='action' value='logout' class='btn-logout'>Logout</button></form>")
                .append("</div>");

            html.append("<h3>Global Inventory Index Catalog</h3><table><tr><th>Asset ID</th><th>Resource Title</th><th>Status</th><th>Operation</th></tr>");
            synchronized (books) {
                for (Book b : books) {
                    html.append("<tr><td>").append(b.id).append("</td><td>").append(b.title).append("</td><td>")
                        .append(b.isAvailable ? "Available" : "Borrowed Out").append("</td><td>");
                    if (b.isAvailable) {
                        html.append("<form method='POST' style='display:inline;'><input type='hidden' name='bookId' value='").append(b.id).append("'><button type='submit' name='action' value='borrow' class='btn-action'>Borrow</button></form>");
                    } else {
                        html.append("<span style='color:grey; font-size:12px;'>Unavailable</span>");
                    }
                    html.append("</td></tr>");
                }
            }
            html.append("</table>");

            html.append("<h3>My Active Borrowed Log</h3><table><tr><th>Asset ID</th><th>Resource Title</th><th>Due Date</th><th>Action</th></tr>");
            synchronized (borrowRecords) {
                boolean hasRecords = false;
                for (BorrowRecord r : borrowRecords) {
                    if (r.schoolId.equals(user) && !r.isReturned) {
                        hasRecords = true;
                        html.append("<tr><td>").append(r.bookId).append("</td><td>").append(r.bookTitle).append("</td><td>").append(r.dueDate).append("</td><td>")
                            .append("<form method='POST'><input type='hidden' name='bookId' value='").append(r.bookId).append("'><button type='submit' name='action' value='return' class='btn-action' style='background:#4caf50;'>Return Resource</button></form>")
                            .append("</td></tr>");
                    }
                }
                if (!hasRecords) html.append("<tr><td colspan='4' style='text-align:center;color:grey;'>No active borrowed assets.</td></tr>");
            }
            html.append("</table></body></html>");

            return html.toString();
        }
    }

    // --- SWING USER INTERFACE ARCHITECTURE ---
    private void setupMainFrame() {
        setTitle("Advanced Library Management Network Console");
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
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, COLOR_PRIMARY),
                new EmptyBorder(5, 15, 5, 15)
        ));

        lblWelcomeStatus = new JLabel("Ready. Please log in or register.");
        lblWelcomeStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblWelcomeStatus.setForeground(COLOR_TEXT);

        lblLiveClock = new JLabel();
        lblLiveClock.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblLiveClock.setForeground(COLOR_TEXT);

        String portEnv = System.getenv("PORT");
        int currentActivePort = (portEnv != null) ? Integer.parseInt(portEnv) : DEFAULT_PORT;

        javax.swing.Timer systemClockTimer = new javax.swing.Timer(1000, e -> {
            String currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            lblLiveClock.setText(currentDateTime + " | Web Port: " + currentActivePort);
        });
        systemClockTimer.start();

        bar.add(lblWelcomeStatus, BorderLayout.WEST);
        bar.add(lblLiveClock, BorderLayout.EAST);
        return bar;
    }

    private void styleButton(JButton btn) {
        btn.setBackground(COLOR_BTN_GREY);
        btn.setForeground(COLOR_TEXT);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_PRIMARY, 1),
                BorderFactory.createEmptyBorder(6, 16, 6, 16)
        ));
    }

    private void styleTable(JTable tbl) {
        tbl.setBackground(COLOR_BG);
        tbl.setForeground(COLOR_TEXT);
        tbl.setSelectionBackground(COLOR_ACCENT_BG);
        tbl.setSelectionForeground(COLOR_TEXT);
        tbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tbl.setRowHeight(26);
        tbl.setGridColor(COLOR_BTN_GREY);

        tbl.getTableHeader().setBackground(COLOR_ACCENT_BG);
        tbl.getTableHeader().setForeground(COLOR_TEXT);
        tbl.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        tbl.getTableHeader().setBorder(new LineBorder(COLOR_PRIMARY, 1));
    }

    private JPanel buildLoginPanel() {
        JPanel main = new JPanel(new GridBagLayout());
        main.setBackground(COLOR_BG);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(COLOR_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(COLOR_PRIMARY, 2),
                BorderFactory.createEmptyBorder(35, 45, 35, 45)
        ));

        JLabel title = new JLabel("Identity Access Portal");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(COLOR_TEXT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel labelFormatHint = new JLabel("Enter Student ID (Format: YYYY-XXXXXX):");
        labelFormatHint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        labelFormatHint.setForeground(COLOR_TEXT);
        labelFormatHint.setAlignmentX(Component.CENTER_ALIGNMENT);

        JTextField inputIdField = new JTextField(16);
        inputIdField.setMaximumSize(new Dimension(260, 32));
        inputIdField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        inputIdField.setForeground(COLOR_TEXT);
        inputIdField.setBorder(new LineBorder(COLOR_PRIMARY, 1));
        inputIdField.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10));
        actionRow.setBackground(COLOR_BG);

        JButton btnLogin = new JButton("Login Auth");
        JButton btnRegister = new JButton("Register ID");
        styleButton(btnLogin);
        styleButton(btnRegister);

        actionRow.add(btnLogin);
        actionRow.add(btnRegister);

        card.add(title);
        card.add(Box.createVerticalStrut(25));
        card.add(labelFormatHint);
        card.add(Box.createVerticalStrut(6));
        card.add(inputIdField);
        card.add(Box.createVerticalStrut(20));
        card.add(actionRow);
        main.add(card);

        btnLogin.addActionListener(e -> {
            String uid = inputIdField.getText().trim();
            if (users.containsKey(uid)) {
                loggedInUser = users.get(uid);
                writeLog("logins", "Desktop User opened session: " + uid);

                lblWelcomeStatus.setText("Hello, " + uid + "! Current Session Active.");

                if (loggedInUser.isAdmin) {
                    lblAdminHeader.setText("Hello, Admin! | System Administrative Operations Dashboard");
                    refreshAdminTables();
                    cardLayout.show(containerPanel, "ScreenAdmin");
                } else {
                    lblStudentHeader.setText("Hello, " + uid + "! | Digital Circulation Desk");
                    refreshStudentCatalog();
                    refreshStudentPersonalLog();
                    cardLayout.show(containerPanel, "ScreenStudent");
                }
                inputIdField.setText("");
            } else {
                JOptionPane.showMessageDialog(this, "Identity Key mismatch or entry not found.", "Auth Failure", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnRegister.addActionListener(e -> {
            String uid = inputIdField.getText().trim();
            if (!STUDENT_ID_PATTERN.matcher(uid).matches() && !uid.equalsIgnoreCase("admin")) {
                JOptionPane.showMessageDialog(this, "Invalid Format! Must use a hyphen: YYYY-XXXXXX", "Formatting Alert", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (users.containsKey(uid)) {
                JOptionPane.showMessageDialog(this, "This identity is already registered.", "Data Conflict", JOptionPane.WARNING_MESSAGE);
            } else {
                users.put(uid, new User(uid, false));
                writeLog("logins", "New local system profile created: " + uid);
                JOptionPane.showMessageDialog(this, "Identity Registered! You can now log in.", "Success Registry", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        return main;
    }

    private JPanel buildStudentPanel() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(COLOR_BG);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(COLOR_ACCENT_BG);
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, COLOR_PRIMARY));
        headerPanel.setBorder(BorderFactory.createCompoundBorder(headerPanel.getBorder(), new EmptyBorder(10,15,10,15)));

        lblStudentHeader = new JLabel("Digital Circulation Desk");
        lblStudentHeader.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblStudentHeader.setForeground(COLOR_TEXT);

        JButton btnLogout = new JButton("Terminate Session");
        styleButton(btnLogout);
        headerPanel.add(lblStudentHeader, BorderLayout.WEST);
        headerPanel.add(btnLogout, BorderLayout.EAST);

        JTabbedPane tabArea = new JTabbedPane();
        tabArea.setFont(new Font("Segoe UI", Font.BOLD, 12));
        tabArea.setBackground(COLOR_BG);
        tabArea.setForeground(COLOR_TEXT);

        JPanel tabCatalog = new JPanel(new BorderLayout());
        tabCatalog.setBackground(COLOR_BG);
        modelStudentBooks = new DefaultTableModel(new String[]{"Asset ID", "Resource Title", "Availability Status"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tableStudentBooks = new JTable(modelStudentBooks);
        styleTable(tableStudentBooks);
        tabCatalog.add(new JScrollPane(tableStudentBooks), BorderLayout.CENTER);

        JPanel catalogActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        catalogActions.setBackground(COLOR_BG);
        JButton btnBorrow = new JButton("Borrow Selected Book");
        styleButton(btnBorrow);
        catalogActions.add(btnBorrow);
        tabCatalog.add(catalogActions, BorderLayout.SOUTH);

        JPanel tabHistory = new JPanel(new BorderLayout());
        tabHistory.setBackground(COLOR_BG);
        modelMyHistory = new DefaultTableModel(new String[]{"Asset ID", "Resource Title", "Checkout Date", "Due Date", "Status"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tableMyHistory = new JTable(modelMyHistory);
        styleTable(tableMyHistory);
        tabHistory.add(new JScrollPane(tableMyHistory), BorderLayout.CENTER);

        JPanel historyActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        historyActions.setBackground(COLOR_BG);
        JButton btnReturn = new JButton("Return Selected Book");
        styleButton(btnReturn);
        historyActions.add(btnReturn);
        tabHistory.add(historyActions, BorderLayout.SOUTH);

        tabArea.addTab("Global Inventory Index Catalog", tabCatalog);
        tabArea.addTab("My Active Borrowed Log", tabHistory);

        main.add(headerPanel, BorderLayout.NORTH);
        main.add(tabArea, BorderLayout.CENTER);

        btnLogout.addActionListener(e -> triggerLogOutAction());

        btnBorrow.addActionListener(e -> {
            int row = tableStudentBooks.getSelectedRow();
            if (row == -1) return;
            int bid = (int) modelStudentBooks.getValueAt(row, 0);
            synchronized (books) {
                for (Book b : books) {
                    if (b.id == bid) {
                        if (!b.isAvailable) {
                            JOptionPane.showMessageDialog(this, "This book is already borrowed out.");
                            return;
                        }
                        b.isAvailable = false;
                        BorrowRecord rec = new BorrowRecord(b.id, b.title, loggedInUser.schoolId);
                        borrowRecords.add(rec);
                        writeLog("borrowed", "User " + loggedInUser.schoolId + " borrowed Asset ID " + bid + " ("+b.title+")");
                        break;
                    }
                }
            }
            refreshStudentCatalog();
            refreshStudentPersonalLog();
        });

        btnReturn.addActionListener(e -> {
            int row = tableMyHistory.getSelectedRow();
            if (row == -1) return;
            int bid = (int) modelMyHistory.getValueAt(row, 0);
            String status = (String) modelMyHistory.getValueAt(row, 4);
            if(status.contains("Returned")) {
                JOptionPane.showMessageDialog(this, "This book has already been returned.");
                return;
            }
            synchronized (borrowRecords) {
                for (BorrowRecord r : borrowRecords) {
                    if (r.bookId == bid && r.schoolId.equals(loggedInUser.schoolId) && !r.isReturned) {
                        r.isReturned = true;
                        break;
                    }
                }
            }
            synchronized (books) {
                for (Book b : books) {
                    if (b.id == bid) {
                        b.isAvailable = true;
                        writeLog("returned", "User " + loggedInUser.schoolId + " returned ID: " + bid);
                        break;
                    }
                }
            }
            refreshStudentCatalog();
            refreshStudentPersonalLog();
        });

        return main;
    }

    private JPanel buildAdminPanel() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(COLOR_BG);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(COLOR_ACCENT_BG);
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, COLOR_PRIMARY));
        headerPanel.setBorder(BorderFactory.createCompoundBorder(headerPanel.getBorder(), new EmptyBorder(10,15,10,15)));

        lblAdminHeader = new JLabel("System Administrative Operations Dashboard");
        lblAdminHeader.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblAdminHeader.setForeground(COLOR_TEXT);

        JButton btnLogout = new JButton("Exit Admin Context");
        styleButton(btnLogout);
        headerPanel.add(lblAdminHeader, BorderLayout.WEST);
        headerPanel.add(btnLogout, BorderLayout.EAST);

        JTabbedPane adminTabs = new JTabbedPane();
        adminTabs.setFont(new Font("Segoe UI", Font.BOLD, 12));
        adminTabs.setBackground(COLOR_BG);
        adminTabs.setForeground(COLOR_TEXT);

        JPanel panelBooks = new JPanel(new BorderLayout());
        panelBooks.setBackground(COLOR_BG);
        modelAdminBooks = new DefaultTableModel(new String[]{"Asset ID", "Resource Title", "Inventory Status"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tableAdminBooks = new JTable(modelAdminBooks);
        styleTable(tableAdminBooks);
        panelBooks.add(new JScrollPane(tableAdminBooks), BorderLayout.CENTER);

        JPanel adminDashboardControls = new JPanel(new GridBagLayout());
        adminDashboardControls.setBackground(COLOR_BG);
        adminDashboardControls.setBorder(new EmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel lblTitle = new JLabel("New Book Title:");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblTitle.setForeground(COLOR_TEXT);
        JTextField txtTitle = new JTextField(25);
        txtTitle.setForeground(COLOR_TEXT);
        txtTitle.setBorder(new LineBorder(COLOR_PRIMARY, 1));

        JButton btnAdd = new JButton("Add Book Resource");
        JButton btnRemove = new JButton("Remove Selected Book");
        styleButton(btnAdd);
        styleButton(btnRemove);

        gbc.gridx = 0; gbc.gridy = 0; adminDashboardControls.add(lblTitle, gbc);
        gbc.gridx = 1; adminDashboardControls.add(txtTitle, gbc);
        gbc.gridx = 0; gbc.gridy = 1; adminDashboardControls.add(btnAdd, gbc);
        gbc.gridx = 1; adminDashboardControls.add(btnRemove, gbc);
        panelBooks.add(adminDashboardControls, BorderLayout.SOUTH);

        JPanel panelUsers = new JPanel(new BorderLayout());
        panelUsers.setBackground(COLOR_BG);
        modelAdminUsers = new DefaultTableModel(new String[]{"Registered User ID", "Account Designation System Privilege"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tableAdminUsers = new JTable(modelAdminUsers);
        styleTable(tableAdminUsers);
        panelUsers.add(new JScrollPane(tableAdminUsers), BorderLayout.CENTER);

        JPanel panelLogs = new JPanel(new BorderLayout());
        panelLogs.setBackground(COLOR_BG);
        modelAdminLogs = new DefaultTableModel(new String[]{"Live System Audit Trail Track Records"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tableAdminLogs = new JTable(modelAdminLogs);
        styleTable(tableAdminLogs);
        panelLogs.add(new JScrollPane(tableAdminLogs), BorderLayout.CENTER);

        adminTabs.addTab("Physical Books Catalog Inventory", panelBooks);
        adminTabs.addTab("Registered System Profiles Directory", panelUsers);
        adminTabs.addTab("Real-Time Application Activity Logs", panelLogs);

        main.add(headerPanel, BorderLayout.NORTH);
        main.add(adminTabs, BorderLayout.CENTER);

        btnLogout.addActionListener(e -> triggerLogOutAction());

        btnAdd.addActionListener(e -> {
            String title = txtTitle.getText().trim();
            if (title.isEmpty()) return;
            synchronized (books) {
                int nextId = 1001 + books.size();
                books.add(new Book(nextId, title));
                writeLog("book added", "ADMIN added: Asset ID " + nextId + " [" + title + "]");
            }
            txtTitle.setText("");
            refreshAdminTables();
        });

        btnRemove.addActionListener(e -> {
            int row = tableAdminBooks.getSelectedRow();
            if (row == -1) return;
            int bid = (int) modelAdminBooks.getValueAt(row, 0);
            String bTitle = "";
            synchronized (books) {
                for (int i = 0; i < books.size(); i++) {
                    if (books.get(i).id == bid) {
                        bTitle = books.get(i).title;
                        books.remove(i);
                        writeLog("remove books", "ADMIN deleted ID: " + bid + " ["+bTitle+"]");
                        break;
                    }
                }
            }
            refreshAdminTables();
        });

        return main;
    }

    private void triggerLogOutAction() {
        if (loggedInUser != null) {
            writeLog("log out", "Desktop Session closed: " + loggedInUser.schoolId);
        }
        loggedInUser = null;

        lblWelcomeStatus.setText("Session closed safely. Please log in.");
        cardLayout.show(containerPanel, "ScreenLogin");
    }

    private void refreshStudentCatalog() {
        modelStudentBooks.setRowCount(0);
        synchronized (books) {
            for (Book b : books) {
                modelStudentBooks.addRow(new Object[]{b.id, b.title, b.isAvailable ? "Available" : "Borrowed Out"});
            }
        }
    }

    private void refreshStudentPersonalLog() {
        modelMyHistory.setRowCount(0);
        synchronized (borrowRecords) {
            for (BorrowRecord r : borrowRecords) {
                if (r.schoolId.equals(loggedInUser.schoolId)) {
                    modelMyHistory.addRow(new Object[]{
                            r.bookId, r.bookTitle, r.borrowDate.toString(), r.dueDate.toString(), r.isReturned ? "Returned" : "Active"
                    });
                }
            }
        }
    }

    private void refreshAdminTables() {
        modelAdminBooks.setRowCount(0);
        synchronized (books) {
            for (Book b : books) {
                modelAdminBooks.addRow(new Object[]{b.id, b.title, b.isAvailable ? "Available" : "Borrowed Out"});
            }
        }

        modelAdminUsers.setRowCount(0);
        synchronized (users) {
            for (String uid : users.keySet()) {
                User u = users.get(uid);
                modelAdminUsers.addRow(new Object[]{u.schoolId, u.isAdmin ? "System Administrator" : "Standard Registered Student"});
            }
        }

        modelAdminLogs.setRowCount(0);
        synchronized (globalActivityLogs) {
            for (int i = globalActivityLogs.size() - 1; i >= 0; i--) {
                modelAdminLogs.addRow(new Object[]{globalActivityLogs.get(i)});
            }
        }
    }

    private void seedDataInventory() {
        if (users.isEmpty()) {
            users.put("admin", new User("admin", true));
        }

        if (books.isEmpty()) {
            String[] sampleTitles = {
                "The Great Gatsby", "To Kill a Mockingbird", "1984", "Pride and Prejudice", "The Catcher in the Rye",
                "The Hobbit", "Fahrenheit 451", "Jane Eyre", "Animal Farm", "The Lord of the Rings",
                "Brave New World", "Lord of the Flies", "The Grapes of Wrath", "Macbeth", "Hamlet",
                "Romeo and Juliet", "Frankenstein", "The Odyssey", "A Tale of Two Cities", "Crime and Punishment"
            };
            for (int i = 0; i < sampleTitles.length; i++) {
                books.add(new Book(1001 + i, sampleTitles[i]));
            }
        }
    }

    public static void main(String[] args) {
        boolean isCloudEnvironment = System.getenv("PORT") != null;

        if (isCloudEnvironment) {
            System.out.println("--- CLOUD ENVIRONMENT DETECTED ---");
            System.setProperty("java.awt.headless", "true");
            new AdvancedLibrarySystem();
        } else {
            System.out.println("--- LOCAL DESKTOP COMPUTER DETECTED ---");
            SwingUtilities.invokeLater(() -> new AdvancedLibrarySystem().setVisible(true));
        }
    }
}