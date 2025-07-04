package gui;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StartupAppPanel extends JPanel {

    private JTable table;
    private StartupTableModel tableModel;

    public StartupAppPanel() {
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        JLabel titleLabel = new JLabel("  Startup Apps");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 17));
        titleLabel.setOpaque(true);
        titleLabel.setBackground(Color.WHITE);
        titleLabel.setForeground(Color.BLACK);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(10, 2, 10, 10));

        // Create header panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(Color.WHITE);
        headerPanel.add(titleLabel, BorderLayout.NORTH);
        
        // Add separator line
        JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
        separator.setForeground(new Color(230, 230, 230));
        separator.setBackground(new Color(230, 230, 230));
        headerPanel.add(separator, BorderLayout.SOUTH);

        tableModel = new StartupTableModel();
        table = new JTable(tableModel);
        table.setRowHeight(32);
        table.setFont(new Font("SansSerif", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("SansSerif", Font.PLAIN, 14));
        table.setGridColor(new Color(230, 230, 230));
        table.setShowVerticalLines(true);
        table.setShowHorizontalLines(false);
        table.getColumnModel().getColumn(0).setCellRenderer(new IconRenderer());

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBackground(Color.white);
        scrollPane.getViewport().setBackground(Color.white);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        add(titleLabel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        fetchStartupApps();
    }

    private void fetchStartupApps() {
        fetchFromRegistry("HKCU", "User");
        fetchFromRegistry("HKLM", "Machine");
        fetchFromStartupFolder(System.getenv("APPDATA") + "\\\\Microsoft\\\\Windows\\\\Start Menu\\\\Programs\\\\Startup", "Startup Folder - User");
        fetchFromStartupFolder(System.getenv("ProgramData") + "\\\\Microsoft\\\\Windows\\\\Start Menu\\\\Programs\\\\Startup", "Startup Folder - All Users");
    }

    private void fetchFromRegistry(String hive, String source) {
        try {
            String command = String.format("powershell.exe -Command \"Get-ItemProperty %s:\\\\Software\\\\Microsoft\\\\Windows\\\\CurrentVersion\\\\Run\"", hive);

            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", command);
            builder.redirectErrorStream(true);
            Process process = builder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        String name = parts[0].trim();
                        String cmd = parts[1].trim();
                        Icon icon = getExecutableIconFromCommand(cmd);
                        tableModel.addStartupApp(new Object[]{icon, name, cmd, source});
                    }
                }
            }

            process.waitFor();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Registry error: " + e.getMessage());
        }
    }

    private void fetchFromStartupFolder(String folderPath, String source) {
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) return;

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".lnk"));
        if (files == null) return;

        for (File file : files) {
            try {
                String resolved = resolveShortcut(file.getAbsolutePath());
                if (resolved != null) {
                    Icon icon = getExecutableIconFromCommand(resolved);
                    tableModel.addStartupApp(new Object[]{icon, file.getName(), resolved, source});
                }
            } catch (Exception ignored) {}
        }
    }

    private String resolveShortcut(String lnkPath) {
        try {
            String script = "$sh = New-Object -ComObject WScript.Shell; " +
                            "$sc = $sh.CreateShortcut('" + lnkPath.replace("\\", "\\\\") + "'); " +
                            "$sc.TargetPath";
            ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-Command", script);
            builder.redirectErrorStream(true);
            Process process = builder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            return reader.readLine();
        } catch (Exception e) {
            return null;
        }
    }

    private Icon getExecutableIconFromCommand(String command) {
        try {
            Matcher m = Pattern.compile("(?:(?:\\\")?([a-zA-Z]:\\\\[^\\s\\\"]+\\.exe))").matcher(command);
            if (m.find()) {
                String exePath = m.group(1);
                File exeFile = new File(exePath);
                if (exeFile.exists()) {
                    return FileSystemView.getFileSystemView().getSystemIcon(exeFile);
                }
            }
        } catch (Exception ignored) {}
        return UIManager.getIcon("FileView.fileIcon");
    }

    static class StartupTableModel extends AbstractTableModel {
        private final String[] columns = {"Icon", "Name", "Command", "Source"};
        private final ArrayList<Object[]> data = new ArrayList<>();

        public void addStartupApp(Object[] row) {
            data.add(row);
            fireTableRowsInserted(data.size() - 1, data.size() - 1);
        }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int col) { return columns[col]; }
        @Override public Object getValueAt(int row, int col) { return data.get(row)[col]; }
        @Override public Class<?> getColumnClass(int col) {
            return col == 0 ? Icon.class : String.class;
        }
    }

    static class IconRenderer extends DefaultTableCellRenderer{
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int col) {
            setIcon((Icon) value);
            setText("");
            setHorizontalAlignment(JLabel.CENTER);
            return this;
        }
    }
}
