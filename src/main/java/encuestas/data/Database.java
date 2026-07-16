package encuestas.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

/**
 * Pool de conexiones (HikariCP) compartido por toda la aplicación. Sustituye a la
 * antigua apertura de una conexión por consulta con credenciales en el código.
 */
public final class Database {

    private final HikariDataSource dataSource;

    public Database(String host, int port, String name, String user, String password) {
        HikariConfig config = new HikariConfig();
        // Clase del driver explícita: dentro de Tomcat el DriverManager no ve los drivers
        // cargados por el classloader de la aplicación web.
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + name
                + "?useUnicode=true&characterEncoding=utf8&connectionTimeZone=UTC");
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setPoolName("encuestas-pool");
        dataSource = new HikariDataSource(config);
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void close() {
        dataSource.close();
    }
}
