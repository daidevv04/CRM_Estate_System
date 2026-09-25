import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

/**
 * Chay mot cau SELECT roi in ra bang. Cau hinh lay tu bien moi truong de khong
 * lo mat khau qua command line: DB_URL, DB_USER, DB_PASSWORD.
 *
 * java -cp <postgresql-driver.jar> tools/JdbcQuery.java "select ..."
 */
public class JdbcQuery {

    public static void main(String[] args) throws Exception {
        String url = System.getenv("DB_URL");
        String user = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");
        String sql = args[0];

        try (Connection connection = DriverManager.getConnection(url, user, password);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {

            ResultSetMetaData meta = resultSet.getMetaData();
            int columns = meta.getColumnCount();
            StringBuilder header = new StringBuilder();
            for (int i = 1; i <= columns; i++) {
                header.append(meta.getColumnLabel(i));
                if (i < columns) {
                    header.append(" | ");
                }
            }
            System.out.println(header);

            int rows = 0;
            while (resultSet.next()) {
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= columns; i++) {
                    row.append(resultSet.getString(i));
                    if (i < columns) {
                        row.append(" | ");
                    }
                }
                System.out.println(row);
                rows++;
            }
            System.out.println("(" + rows + " dong)");
        }
    }
}
