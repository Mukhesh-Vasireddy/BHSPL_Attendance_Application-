package com.bhspl.util;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Utility class for exporting JTable data to CSV files. 
 */
public class CSVExporter {

    /**
     * Exports the provided JTable's data to a CSV file.
     *
     * @param table    The JTable to export.
     * @param filePath The destination file path.
     * @throws IOException If an I/O error occurs.
     */
    public static void exportTable(JTable table, String filePath) throws IOException {
        TableModel model = table.getModel();
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            // Write Header Row
            for (int i = 0; i < model.getColumnCount(); i++) {
                pw.print(escapeCSV(model.getColumnName(i)));
                if (i < model.getColumnCount() - 1) pw.print(",");
            }
            pw.println();

            // Write Data Rows
            for (int row = 0; row < model.getRowCount(); row++) {
                for (int col = 0; col < model.getColumnCount(); col++) {
                    Object val = model.getValueAt(row, col);
                    pw.print(escapeCSV(val == null ? "" : val.toString()));
                    if (col < model.getColumnCount() - 1) pw.print(",");
                }
                pw.println();
            }
        }
    }

    /**
     * Escapes special characters for CSV formatting.
     */
    private static String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
