import java.sql.*;

public class MySQLManager {

    private static final String DATABASE_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String DATABASE_URL = "jdbc:mysql://localhost:3306/haberler";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "123456";

    // connection object
    private Connection connection;

    // connect database
    public Connection connect() {
        if (connection == null) {
            try {
                Class.forName(DATABASE_DRIVER).newInstance();
                connection = DriverManager.getConnection(DATABASE_URL, USERNAME, PASSWORD );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return connection;
    }

    // disconnect database
    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
                connection = null;
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

}
