package servidor;

import java.sql.Connection;
import java.sql.DriverManager;

public class Conexion {
    private static final String URL = "jdbc:sqlserver://localhost:1433;databaseName=restaurante_db;encrypt=true;trustServerCertificate=true;";
    private static final String USER = "sa"; 
    private static final String PASS = "123456"; // Contraseña del comando SQL

    public static Connection getConnection() {
        Connection con = null;
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            con = DriverManager.getConnection(URL, USER, PASS);
            System.out.println("[SQL Server] Conexión establecida con éxito usando usuario SA.");
        } catch (Exception e) {
            System.out.println("[ERROR SQL] No se pudo conectar: " + e.getMessage());
        }
        return con;
    }
}