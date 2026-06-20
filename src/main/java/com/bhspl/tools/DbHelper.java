package com.bhspl.tools;

import java.sql.*;

public final class DbHelper {
    private DbHelper() {}

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {}
        String url = "jdbc:mysql://localhost:3306/bhspl_attendance";
        return DriverManager.getConnection(url, "root", "user");
    }

    public static void printResultSet(ResultSet rs) throws SQLException {
        System.out.println(toJson(rs));
    }

    public static String toJson(ResultSet rs) throws SQLException {
        StringBuilder sb = new StringBuilder();
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        sb.append("[");
        boolean firstRow = true;
        while (rs.next()) {
            if (!firstRow) sb.append(",\n");
            sb.append("  {");
            for (int i = 1; i <= cols; i++) {
                if (i > 1) sb.append(", ");
                String name = md.getColumnLabel(i);
                Object val = rs.getObject(i);
                sb.append("\"").append(name).append("\":");
                if (val == null) sb.append("null");
                else if (val instanceof Number || val instanceof Boolean) sb.append(val.toString());
                else {
                    String s = val.toString().replace("\\", "\\\\").replace("\"", "\\\"");
                    sb.append("\"").append(s).append("\"");
                }
            }
            sb.append(" }");
            firstRow = false;
        }
        sb.append("\n]");
        return sb.toString();
    }
}
