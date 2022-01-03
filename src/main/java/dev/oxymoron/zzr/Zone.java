package dev.oxymoron.zzr;

import java.util.*;
import java.io.File;
import java.nio.file.Paths;
import java.sql.*;

public class Zone {
    public Coord input1, input2;
    public List<Coord> bounds;

    public void Print() {
        System.out.print("\tZone: (" + input1.x + ", " + input1.y + ") - (" + input2.x + ", " + input2.y + ")");
    }

    public void ClearMapFiles(String directory) {
        File path = new File(directory);
        String[] contents = path.list();
        for (String p : contents) {
            if (!p.startsWith("map_"))
                continue;
            String[] parsed = p.replace(".bin", "").split("_");
            int x, y;
            try {
                x = Integer.parseInt(parsed[1]);
                y = Integer.parseInt(parsed[2]);
            } catch (NumberFormatException e) {
                continue;
            }
            boolean block = bounds.stream().filter(c -> c.x == x && c.y == y).count() > 0;
            if (!block)
                continue;
            System.out.println("\t\t"+p+" deleted.");
            File file = Paths.get(directory, p).toFile();
            deleteFile(file);
        }
    }

    public String BuildSqlStatement() {
        return "DELETE FROM vehicles WHERE wx BETWEEN "+input1.x+" AND "+input2.x+" AND wy BETWEEN "+input1.y+" AND "+input2.y+";";
    }

    public void ClearVehicles(Connection c) throws SQLException {
        String sql = "DELETE FROM vehicles WHERE wx BETWEEN ? AND ? AND wy BETWEEN ? AND ?;";
        PreparedStatement statement = c.prepareStatement(sql);
        statement.setQueryTimeout(30);
        statement.setInt(1, input1.x);
        statement.setInt(2, input2.x);
        statement.setInt(3, input1.y);
        statement.setInt(4, input2.y);
        statement.execute();
    }

    private static final java.util.concurrent.ExecutorService DELETE_SERVICE = java.util.concurrent.Executors.newSingleThreadExecutor();
    public static void deleteFile(final File file) {
        if (file != null) {
            DELETE_SERVICE.submit(new Runnable() {
                @Override
                public void run() {
                    file.delete();
                }
            });
        }
    }
}