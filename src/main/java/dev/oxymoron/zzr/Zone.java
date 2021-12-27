package dev.oxymoron.zzr;

import java.util.*;
import java.io.File;
import java.nio.file.Paths;
import java.sql.*;

public class Zone {
    public Coord input1, input2;
    public List<Coord> bounds;

    public void Print() {
        System.out.println("(" + input1.x + ", " + input1.y + ") - (" + input2.x + ", " + input2.y + ")");
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

            File file = Paths.get(directory, p).toFile();
            file.delete();
        }
    }

    public void ClearVehicles(String directory) throws SQLException
    {
        Connection c = null;
        try
        {
            c = getConnection(directory);
            if(c.isValid(50)) {
                //System.out.println("Connected");
                // 1 & 3 - 2 & 4
                String sql = "DELETE FROM vehicles WHERE wx BETWEEN ? AND ? AND wy BETWEEN ? AND ?;";
                PreparedStatement statement = c.prepareStatement(sql);
                statement.setQueryTimeout(30);
                statement.setInt(1, input1.x);
                statement.setInt(2, input2.x);
                statement.setInt(3, input1.y);
                statement.setInt(4, input2.y);
                statement.execute();
            } else {
                System.out.println("No Connection");
            }
        }
        catch(SQLException e)
        {
            System.err.println(e.getMessage());
        }
        finally
        {
            try
            {
                if(c != null) {
                    c.close();
                }
            }
            catch (SQLException e)
            {
                System.err.println(e);
            }
        }
    }

    private Connection getConnection(String directory) {
        Connection c = null;
        File path = Paths.get(directory, "vehicles.db").toFile();

        try {
            Class.forName("org.sqlite.JDBC");
            c = DriverManager.getConnection("jdbc:sqlite:" + path);
        } catch (Exception e) {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());
            System.exit(0);
        }

        return c;
    }
}