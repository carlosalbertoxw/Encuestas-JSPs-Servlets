package encuestas.data;

import encuestas.model.Poll;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Acceso a datos de encuestas. Las escrituras filtran también por el dueño para que
 * nadie pueda editar o borrar encuestas ajenas manipulando el id.
 */
public class PollRepository {

    private static final Logger LOGGER = Logger.getLogger(PollRepository.class.getName());

    private static final String COLUMNS = "p_key, p_title, p_description, p_position, p_user_key";

    private final DataSource dataSource;

    public PollRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Poll> getPolls(int userId) {
        List<Poll> polls = new ArrayList<>();
        String sql = "SELECT " + COLUMNS + " FROM a_polls WHERE p_user_key=? ORDER BY p_position ASC";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    polls.add(mapPoll(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error al consultar las encuestas", e);
        }
        return polls;
    }

    /** Encuesta por id restringida a su dueño (para editar/borrar/ver respuestas). */
    public Poll getPoll(int userId, int pollId) {
        String sql = "SELECT " + COLUMNS + " FROM a_polls WHERE p_user_key=? AND p_key=?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setInt(2, pollId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapPoll(rs) : null;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error al consultar la encuesta", e);
            return null;
        }
    }

    /** Encuesta por id sin restricción de dueño (para responderla). */
    public Poll getPollById(int pollId) {
        String sql = "SELECT " + COLUMNS + " FROM a_polls WHERE p_key=?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, pollId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapPoll(rs) : null;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error al consultar la encuesta", e);
            return null;
        }
    }

    public boolean addPoll(Poll poll) {
        String sql = "INSERT INTO a_polls(p_title, p_description, p_position, p_user_key) VALUES(?,?,?,?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, poll.getTitle());
            statement.setString(2, poll.getDescription());
            statement.setInt(3, poll.getPosition());
            statement.setInt(4, poll.getUserId());
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error al guardar la encuesta", e);
            return false;
        }
    }

    public boolean updatePoll(Poll poll) {
        String sql = "UPDATE a_polls SET p_title=?, p_description=?, p_position=? WHERE p_key=? AND p_user_key=?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, poll.getTitle());
            statement.setString(2, poll.getDescription());
            statement.setInt(3, poll.getPosition());
            statement.setInt(4, poll.getId());
            statement.setInt(5, poll.getUserId());
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error al actualizar la encuesta", e);
            return false;
        }
    }

    public boolean deletePoll(int userId, int pollId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM a_polls WHERE p_key=? AND p_user_key=?")) {
            statement.setInt(1, pollId);
            statement.setInt(2, userId);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error al borrar la encuesta", e);
            return false;
        }
    }

    private static Poll mapPoll(ResultSet rs) throws SQLException {
        Poll poll = new Poll();
        poll.setId(rs.getInt("p_key"));
        poll.setTitle(rs.getString("p_title"));
        poll.setDescription(rs.getString("p_description"));
        poll.setPosition(rs.getInt("p_position"));
        poll.setUserId(rs.getInt("p_user_key"));
        return poll;
    }
}
