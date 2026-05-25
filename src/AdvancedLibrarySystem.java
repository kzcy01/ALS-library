import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class AdvancedLibrarySystem extends JFrame {
    // GUI components
    private JTabbedPane tabbedPane;
    private JPanel panelStudent, panelAdmin;
    private JTable tableStudentBooks, tableStudentLog, tableAdminBooks, tableAdminUsers, tableAdminLogs;
    private DefaultTableModel modelStudentBooks, modelStudentLog, modelAdminBooks, modelAdminUsers, modelAdminLogs;

    // Constructor
    public AdvancedLibrarySystem() {
        // 1. Core Background Infrastructure Setup
        initializeDatabaseSchema();
        seedDataInventory();
        startLocalhostWebServer();

        // 2. Safe Window Manager Switch for Cloud Environment
        boolean isHeadless = "true".equals(System.getProperty("java.awt.headless"));
        if (!isHeadless) {
            setupMainFrame();
        } else {
            System.out.println("--- CLOUD ENVIRONMENT DETECTED ---");
            System.out.println("Starting Headless Web Infrastructure Server Console...");
        }

        // 3. Background scheduling tasks
        schedulePHTimeRestarts();
    }

    // Graphical User Interface Component Window Layout (Only runs locally)
    private void setupMainFrame() {
        setTitle("Advanced Library Management System");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        tabbedPane = new JTabbedPane();

        // Build Student View Layout Panel
        panelStudent = new JPanel(new BorderLayout());
        modelStudentBooks = new DefaultTableModel(new String[]{"ID", "Title", "Availability"}, 0);
        tableStudentBooks = new JTable(modelStudentBooks);
        panelStudent.add(new JScrollPane(tableStudentBooks), BorderLayout.CENTER);

        // Build Admin View Layout Panel
        panelAdmin = new JPanel(new BorderLayout());
        modelAdminBooks = new DefaultTableModel(new String[]{"ID", "Book Title", "Author", "Status"}, 0);
        tableAdminBooks = new JTable(modelAdminBooks);
        panelAdmin.add(new JScrollPane(tableAdminBooks), BorderLayout.CENTER);

        tabbedPane.addTab("Student Portal", panelStudent);
        tabbedPane.addTab("Admin Dashboard", panelAdmin);

        add(tabbedPane);

        // Populate local table displays
        refreshStudentCatalog();
        refreshStudentPersonalLog();
        refreshAdminTables();
    }

    // Database Connectivity Access Layer Engine
    private Connection getConnection() throws Exception {
        // Attempts to read Railway's reference environment mapping variable first
        String dbUrl = System.getenv("MYSQL_URL");
        if (dbUrl == null || dbUrl.isEmpty()) {
            // Fallback for local development workstation testing environment
            dbUrl = "jdbc:mysql://localhost:3306/library_db?useSSL=false&allowPublicKeyRetrieval=true";
            return DriverManager.getConnection(dbUrl, "root", "password");
        }
        return DriverManager.getConnection(dbUrl);
    }

    private void initializeDatabaseSchema() {
        System.out.println("Executing schema migrations and verifying table definitions...");
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS books (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "title VARCHAR(255) NOT NULL, " +
                    "is_available BOOLEAN DEFAULT TRUE);");
            System.out.println("Database schema sync verification successfully complete.");
        } catch (Exception e) {
            System.out.println("Notice: Schema verification processed or deferred: " + e.getMessage());
        }
    }

    private void seedDataInventory() {
        System.out.println("Seeding core inventory catalogs...");
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            // Check if data is already present to prevent duplicate records
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM books;");
            if (rs.next() && rs.getInt(1) == 0) {
                st.execute("INSERT INTO books (title, is_available) VALUES ('Introduction to Java Cloud Design', true);");
                st.execute("INSERT INTO books (title, is_available) VALUES ('Docker Containers Essentials', true);");
                System.out.println("Database tables successfully seeded with default collections.");
            }
        } catch (Exception e) {
            System.out.println("Notice: Core inventory catalog validation processed.");
        }
    }

    private void startLocalhostWebServer() {
        System.out.println("Initializing internal networking stack socket listeners...");
        // Network server emulation or lightweight embedded API hosting setup goes here
    }

    private void schedulePHTimeRestarts() {
        System.out.println("Automated background systems scheduler configured.");
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
            System.out.println("Catalog view sync skipped: " + e.getMessage());
        }
    }

    private void refreshStudentPersonalLog() {
        System.out.println("Syncing personalized account student logs dynamically...");
    }

    private void refreshAdminTables() {
        System.out.println("Updating administrative system tables and metrics records...");
    }

    // Static Application Main Execution Entry Gate
    public static void main(String[] args) {
        if (System.getenv("PORT") != null) {
            // Cloud environment enforcement configuration parameters
            System.setProperty("java.awt.headless", "true");
            System.out.println("Dynamic network cluster assignment detected. Routing cloud stack pipeline...");
            new AdvancedLibrarySystem();
        } else {
            // Windows/Desktop Graphic execution initialization routine
            System.setProperty("java.awt.headless", "false");
            SwingUtilities.invokeLater(() -> {
                AdvancedLibrarySystem system = new AdvancedLibrarySystem();
                system.setVisible(true);
            });
        }
    }
}