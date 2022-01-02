package dev.oxymoron.zzr;

import java.util.*;
import java.util.stream.Collectors;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;

public class App 
{
    public static void main( String[] args )
    {
        try{
            System.out.println("Loading zones...");
            List<Zone> zones = LoadZones(args);
            Path path = GetPath(args);
            Path save = Paths.get(path.toString(), "Saves", "Multiplayer", "servertest");
            Connection c = null;
            try {
                System.out.println("Deleting map chunks...");
                c = getConnection(save.toString());
                Statement stmt = c.createStatement();
                for(Zone zone : zones) {
                    //zone.Print();
                    zone.ClearMapFiles(save.toString());
                    //stmt += zone.BuildSqlStatement();
                    //zone.ClearVehicles(c);
                    stmt.addBatch(zone.BuildSqlStatement());
                }
                System.out.println("Clearing vehicles...");
                stmt.executeBatch();
            } catch (SQLException e) {
                System.err.println(e.getMessage());
            } finally {
                try {
                    if (c != null) {
                        c.close();
                    }
                } catch (SQLException e) {
                    System.err.println(e);
                }
            }
        }
        catch(Exception ex)
        {
            System.err.println(ex.getClass().getName() + ": " + ex.getMessage());
            System.exit(0);
        }
        System.out.println("Reset Complete.");
    }

    private static String readFile(String path, Charset encoding) throws IOException
    {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding);
    }

    private static List<Coord> GetSequentialRow(List<Coord> coords) {
        List<Coord> bounds = new ArrayList<Coord>();
        for (Coord coord : coords)
        {
            int i = bounds.size() - 1;
            if(i < 0) {
                bounds.add(coord);
                continue;
            }
            Coord prev = bounds.get(i);
            if(coord.y - prev.y != 1) {
                break;
            }
            bounds.add(coord);
        }
        return bounds;
    }

    private static List<Zone> Zonerize(List<Coord> coords)
    {
        List<Zone> zones = new ArrayList<Zone>();

        while (coords.size() > 0) {
            Collections.sort(coords, new Comparator<Coord>(){
                public int compare(Coord c1, Coord c2) {
                    if(c1.x == c2.x) {
                        if(c1.y == c2.y) return 0;
                        return c1.y < c2.y ? -1 : 1;
                    };
                    return c1.x < c2.x ? -1 : 1;
                }
            });
            
            // get top left corner
            int topRow = coords.stream().min(Comparator.comparing(t -> t.x)).get().x;
            Coord topLeft = coords.stream().filter(t -> t.x == topRow).min(Comparator.comparing(t -> t.y)).get();
            List<Coord> bounds = new ArrayList<Coord>();
            // add top row ensuring sequential
            bounds.addAll(GetSequentialRow(coords.stream().filter(t -> t.x == topRow).collect(Collectors.toList())));
            // get top right corner
            Coord topRight = bounds.stream().filter(t -> t.x == topRow).max(Comparator.comparing(t -> t.y)).get();

            for(int i = topRow + 1; i <= Integer.MAX_VALUE; i++) {
                int xIndex = i;
                List<Coord> currentRow = GetSequentialRow(coords.stream().filter(t -> t.x == xIndex).collect(Collectors.toList()));
                if(currentRow.size() == 0) break;
                Coord min = currentRow.stream().min(Comparator.comparing(t -> t.y)).get();
                if(min.y != topLeft.y) break;
                Coord max = currentRow.stream().max(Comparator.comparing(t -> t.y)).get();
                if(max.y != topRight.y) break;
                bounds.addAll(currentRow);
            }
            
            int bottomRow = bounds.stream().max(Comparator.comparing(t -> t.x)).get().x;
            Coord bottomLeft = bounds.stream().filter(t -> t.x == bottomRow).min(Comparator.comparing(t -> t.y)).get();
            Coord bottomRight = bounds.stream().filter(t -> t.x == bottomRow).max(Comparator.comparing(t -> t.y)).get();

            coords.removeAll(bounds);
            Zone zone = new Zone();
            zone.input1 = topLeft;
            zone.input2 = bottomRight;
            zone.bounds = bounds;
            zones.add(zone);
            
            // System.out.println("Top \t(" + topLeft.x + ", " + topLeft.y + ")\t(" + topRight.x + ", " + topRight.y + ")");
            // System.out.println("Bottom \t(" + bottomLeft.x + ", " + bottomLeft.y + ")\t(" + bottomRight.x + ", " + bottomRight.y + ")");
            // System.out.println("New zone created with " + bounds.size() + " blocks");
            // System.out.println("------------------");
        }
        //System.out.println(zones.size() + " total zones");

        return zones;
    }

    private static Path GetPath(String args[]){
        int pathArg = findIndex(args, "-p");
        if(pathArg > -1) {
            return Paths.get(args[pathArg+1]);
        }
        return Paths.get(System.getProperty("user.home"), "Zomboid");
    }

    private static List<Zone> LoadtgodzFile(String path) throws IOException
    {
        String content = readFile(path, StandardCharsets.UTF_8);
        content = content.substring(content.lastIndexOf('{')+1, content.lastIndexOf('}'));
        String[] segments = content.split("=\"\",");
        List<Coord> coords = new ArrayList<Coord>();
        for (String a : segments) {
            String[] c = a.trim().split("r");
            if(c.length < 3) continue;
            Coord coord = new Coord();
            coord.x = Integer.parseInt(c[1]);
            coord.y = Integer.parseInt(c[2]);
            coords.add(coord);
        }

        return Zonerize(coords);
    }

    private static List<Zone> LoadZones(String args[]) throws Exception
    {
        int fileArg = findIndex(args, "-f");
        if(fileArg > -1) {
            String fileName = args[fileArg+1];
            return LoadtgodzFile(fileName);
        }
        
        int input1xArg = findIndex(args, "-input1x");
        int input1yArg = findIndex(args, "-input1y");
        int input2xArg = findIndex(args, "-input2x");
        int input2yArg = findIndex(args, "-input2y");
        if(input1xArg == -1 ||
            input1yArg == -1 ||
            input2xArg == -1 ||
            input2yArg == -1){
            throw new Exception("Either a -f flag is required or a compliment of input 1xy and 2xy.");
        }
        Zone zone = new Zone();
        Coord input1 = new Coord();
        input1.x = Integer.parseInt(args[input1xArg+1]);
        input1.y = Integer.parseInt(args[input1yArg+1]);
        zone.input1 = input1;
        Coord input2 = new Coord();
        input2.x = Integer.parseInt(args[input2xArg+1]);
        input2.y = Integer.parseInt(args[input2yArg+1]);
        zone.input2 = input2;
        List<Zone> zones = new ArrayList<Zone>();
        // TODO: build zone bounds
        zones.add(zone);
        return zones;
    }

    public static int findIndex(String arr[], String value)
    {
        if(arr == null) return -1;

        int len = arr.length;
        int i = 0;
        while (i < len) {
            if(value.equals(arr[i])) {
                return i;
            }
            i++;
        }
        return -1;
    }

    private static Connection getConnection(String directory) {
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
