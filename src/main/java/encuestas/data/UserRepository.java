package encuestas.data;

import encuestas.model.User;
import encuestas.model.UserProfile;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Acceso a datos de cuentas y perfiles con sentencias preparadas sobre el pool.
 */
public class UserRepository {

    private static final Logger LOGGER = Logger.getLogger(UserRepository.class.getName());

    private static final String USER_COLUMNS =
            "u.u_key, u.u_email, u.u_email_confirmed, u.u_password, u.u_security_stamp";

    private final DataSource dataSource;

    public UserRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Crea la cuenta y su perfil en una sola transacción; el perfil inicial recibe
     * un usuario y nombre generados a partir del id.
     */
    public RepositoryResult createUser(String email, String passwordHash, String securityStamp) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long userId;
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO a_users(u_email, u_password, u_security_stamp) VALUES(?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, email);
                    statement.setString(2, passwordHash);
                    statement.setString(3, securityStamp);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        keys.next();
                        userId = keys.getLong(1);
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO a_users_profiles(u_p_key, u_p_user_name, u_p_name) VALUES(?,?,?)")) {
                    statement.setLong(1, userId);
                    statement.setString(2, "usuario" + userId);
                    statement.setString(3, "Usuario" + userId);
                    statement.executeUpdate();
                }
                connection.commit();
                return RepositoryResult.SUCCESS;
            } catch (SQLIntegrityConstraintViolationException e) {
                connection.rollback();
                return RepositoryResult.DUPLICATE;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error al crear la cuenta", e);
            return RepositoryResult.ERROR;
        }
    }

    public User getUser(int id) {
        String sql = "SELECT " + USER_COLUMNS + " FROM a_users AS u WHERE u.u_key=?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapUser(rs) : null;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error al consultar la cuenta", e);
            return null;
        }
    }

    public String getSecurityStamp(int id) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT u_security_stamp FROM a_users WHERE u_key=?")) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error al consultar el sello de seguridad", e);
            return null;
        }
    }

    public RepositoryResult confirmEmail(int id) {
        return executeUpdate("UPDATE a_users SET u_email_confirmed=1 WHERE u_key=?", id);
    }

    public UserProfile getProfileByEmail(String email) {
        return queryProfile("WHERE u.u_email=?", email);
    }

    public UserProfile getProfileByUserName(String userName) {
        return queryProfile("WHERE up.u_p_user_name=?", userName);
    }

    public RepositoryResult updateName(int id, String name) {
        return executeUpdate("UPDATE a_users_profiles SET u_p_name=? WHERE u_p_key=?", id, name);
    }

    public RepositoryResult updateUserName(int id, String userName) {
        return executeUpdate("UPDATE a_users_profiles SET u_p_user_name=? WHERE u_p_key=?", id, userName);
    }

    public RepositoryResult updateEmail(int id, String email) {
        return executeUpdate("UPDATE a_users SET u_email=? WHERE u_key=?", id, email);
    }

    /** Actualiza solo el hash (rehash transparente); no rota el sello de seguridad. */
    public RepositoryResult updatePassword(int id, String passwordHash) {
        return executeUpdate("UPDATE a_users SET u_password=? WHERE u_key=?", id, passwordHash);
    }

    /** Cambia la contraseña y rota el sello para invalidar las demás sesiones. */
    public RepositoryResult changePassword(int id, String passwordHash, String newSecurityStamp) {
        return executeUpdate("UPDATE a_users SET u_password=?, u_security_stamp=? WHERE u_key=?",
                id, passwordHash, newSecurityStamp);
    }

    /** El esquema borra en cascada el perfil, las encuestas y las respuestas. */
    public RepositoryResult deleteAccount(int id) {
        return executeUpdate("DELETE FROM a_users WHERE u_key=?", id);
    }

    private UserProfile queryProfile(String where, String value) {
        String sql = "SELECT " + USER_COLUMNS + ", up.u_p_user_name, up.u_p_name "
                + "FROM a_users AS u JOIN a_users_profiles AS up ON u.u_key=up.u_p_key " + where;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapProfile(rs) : null;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error al consultar el perfil", e);
            return null;
        }
    }

    /**
     * Ejecuta una actualización cuyo último parámetro de la consulta es el id cuando los
     * valores van primero; acepta (sql, id) o (sql, id, valores...) colocando el id al final.
     */
    private RepositoryResult executeUpdate(String sql, int id, String... values) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (String value : values) {
                statement.setString(index++, value);
            }
            statement.setInt(index, id);
            return statement.executeUpdate() == 1 ? RepositoryResult.SUCCESS : RepositoryResult.NOT_FOUND;
        } catch (SQLIntegrityConstraintViolationException e) {
            return RepositoryResult.DUPLICATE;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error al actualizar los datos de la cuenta", e);
            return RepositoryResult.ERROR;
        }
    }

    private static User mapUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("u_key"));
        user.setEmail(rs.getString("u_email"));
        user.setEmailConfirmed(rs.getBoolean("u_email_confirmed"));
        user.setPasswordHash(rs.getString("u_password"));
        user.setSecurityStamp(rs.getString("u_security_stamp"));
        return user;
    }

    private static UserProfile mapProfile(ResultSet rs) throws SQLException {
        UserProfile profile = new UserProfile();
        profile.setUser(mapUser(rs));
        profile.setUserName(rs.getString("u_p_user_name"));
        profile.setName(rs.getString("u_p_name"));
        return profile;
    }
}
