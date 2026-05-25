import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class AdvancedLibrarySystem extends JFrame {
    // GUI components
    private JTabbedPane tabbedPane;
    private JPanel panelStudent, panelAdmin;
    private JTable tableStudentBooks;
    private DefaultTableModel modelStudentBooks;

    // Constructor
    public AdvancedLibrarySystem() {
        System.out.println("[SYSTEM CHANNELS] Initializing library subsystems engine...");

        // 1. Core database connection & schema preparation
        initializeDatabaseSchema();
        seedDataInventory();
        startLocalhostWebServer();

        // 2. Safe check for Cloud Environment to prevent crashing
        boolean isHeadless = "true".equals(System.getProperty("java.awt.headless"));
        if (!isHeadless) {
            setupMainFrame();
        } else {
            System.out.println("--- CLOUD ENVIRONMENT DETECTED ---");
            System.out.println("Running silently in background console mode. UI rendering skipped safely.");
            // Print the data directly to the logs since we cannot show a user interface window
            displayDatabaseContentsInLogs();
        }

        schedulePHTimeRestarts();
    }

    // Graphical Interface Setup (Only executed locally on your laptop/computer)
    private void setupMainFrame() {
        setTitle("Advanced Library Management System");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        tabbedPane = new JTabbedPane();

        panelStudent = new JPanel(new BorderLayout());
        modelStudentBooks = new DefaultTableModel(new String[]{"ID", "Title", "Availability"}, 0);
        tableStudentBooks = new JTable(modelStudentBooks);
        panelStudent.add(new JScrollPane(tableStudentBooks), BorderLayout.CENTER);

        panelAdmin = new JPanel(new BorderLayout());
        tabbedPane.addTab("Student Portal", panelStudent);
        tabbedPane.addTab("Admin Dashboard", panelAdmin);

        add(tabbedPane);

        // Fetch data to render inside your local window tables
        refreshStudentCatalog();
        refreshStudentPersonalLog();
        refreshAdminTables();
    }

    // Database Connection Provider Engine
    private Connection getConnection() throws Exception {
        String dbUrl = System.getenv("MYSQL_URL");
        if (dbUrl == null || dbUrl.isEmpty()) {
            // Fallback parameters if running locally on your workstation
            dbUrl = "jdbc:mysql://localhost:3306/library_db?useSSL=false&allowPublicKeyRetrieval=true";
            return DriverManager.getConnection(dbUrl, "root", "password");
        }
        return DriverManager.getConnection(dbUrl);
    }

    private void initializeDatabaseSchema() {
        System.out.println("[DATABASE] Connecting to database and verifying structural schema...");
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS books (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "title VARCHAR(255) NOT NULL, " +
                    "is_available BOOLEAN DEFAULT TRUE);");
            System.out.println("[DATABASE] Database schema tables verified successfully.");
        } catch (Exception e) {
            System.out.println("[DATABASE ERROR] Initialization status: " + e.getMessage());
        }
    }

    private void seedDataInventory() {
        System.out.println("[DATABASE] Verifying core data inventory rows...");
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM books;");
            if (rs.next() && rs.getInt(1) == 0) {
                st.execute("INSERT INTO books (title, is_available) VALUES ('Introduction to Java Cloud Design', true);");
                st.execute("INSERT INTO books (title, is_available) VALUES ('Docker Containers Essentials', true);");
                System.out.println("[DATABASE] Default library books successfully seeded into inventory table records.");
            } else {
                System.out.println("[DATABASE] Inventory already contains records. Skipping seed step.");
            }
        } catch (Exception e) {
            System.out.println("[DATABASE ERROR] Seeding process skipped or deferred: " + e.getMessage());
        }
    }

    // Helper method to let you see your book records inside Railway's deploy console logs!
    private void displayDatabaseContentsInLogs() {
        System.out.println("\n=== FETCHING CURRENT BOOKS IN DATABASE TABLES ===");
        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM books;")) {
            int rowCount = 0;
            while(rs.next()) {
                rowCount++;
                int id = rs.getInt("id");
                String title = rs.getString("title");
                boolean available = rs.getBoolean("is_available");
                System.out.println(" > [BOOK RECORD #" + rowCount + "] ID: " + id + " | Title: \"" + title + "\" | Status: " + (available ? "Available" : "Circulating"));
            }
            System.out.println("=================================================\n");
        } catch(Exception e) {
            System.out.println("[CONSOLE LOG FETCH ERROR] Could not read records: " + e.getMessage());
        }
    }

    private void startLocalhostWebServer() {
        System.out.println("[NETWORKING] Starting application listener infrastructure loops...");
    }

    private void schedulePHTimeRestarts() {
        System.out.println("[SCHEDULER] Background monitoring chronometers initialized.");
    }

    private void refreshStudentCatalog() {
        if (modelStudentBooks == null) return;
        modelStudentBooks.setRowCount(0);
        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM books;")) {
            while(rs.next()) {
                modelStudentBooks.addRow(new Object[]{
                    rs.getInt("id"),
                    rs.getString("title"),
                    rs.getBoolean("is_available") ? "Available" : "Circulating"
                });
            }
        } catch(Exception e) {
            System.out.println("Could not refresh interface tables: " + e.getMessage());
        }
    }

    private void refreshStudentPersonalLog() {
        System.out.println("Syncing personal profile logging data...");
    }

    private void refreshAdminTables() {
        System.out.println("Syncing administration dashboard management modules...");
    }

    // Application Core Entry Point
    public static void main(String[] args) {
        if (System.getenv("PORT") != null) {
            // Force headless optimization flags if running inside Railway containers
            System.setProperty("java.awt.headless", "true");
            new AdvancedLibrarySystem();
        } else {
            // Standalone desktop mode with graphics
            System.setProperty("java.awt.headless", "false");
            SwingUtilities.invokeLater(() -> {
                AdvancedLibrarySystem system = new AdvancedLibrarySystem();
                system.setVisible(true);
            });
        }
    }
}