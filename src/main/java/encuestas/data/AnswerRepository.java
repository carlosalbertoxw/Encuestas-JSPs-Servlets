package encuestas.data;

import encuestas.model.Answer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Acceso a datos de respuestas, con listado paginado para el dueño de la encuesta.
 */
public class AnswerRepository {

    private static final Logger LOGGER = Logger.getLogger(AnswerRepository.class.getName());

    private final DataSource dataSource;

    public AnswerRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public PagedResult<Answer> getAnswersForPoll(int pollId, int page, int pageSize) {
        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1) {
            pageSize = 10;
        }
        List<Answer> answers = new ArrayList<>();
        long total = 0;
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM a_answers WHERE a_poll_key=?")) {
                statement.setInt(1, pollId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        total = rs.getLong(1);
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT a.a_key, a.a_stars, a.a_comment, a.a_poll_key, a.a_user_key, up.u_p_user_name "
                            + "FROM a_answers AS a JOIN a_users_profiles AS up ON a.a_user_key=up.u_p_key "
                            + "WHERE a.a_poll_key=? ORDER BY a.a_key DESC LIMIT ? OFFSET ?")) {
                statement.setInt(1, pollId);
                statement.setInt(2, pageSize);
                statement.setInt(3, (page - 1) * pageSize);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        Answer answer = new Answer();
                        answer.setId(rs.getInt("a_key"));
                        answer.setStars(rs.getInt("a_stars"));
                        answer.setComment(rs.getString("a_comment"));
                        answer.setPollId(rs.getInt("a_poll_key"));
                        answer.setUserId(rs.getInt("a_user_key"));
                        answer.setUserName(rs.getString("u_p_user_name"));
                        answers.add(answer);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error al consultar las respuestas", e);
        }
        return new PagedResult<>(answers, page, pageSize, total);
    }

    /** La clave única (encuesta, usuario) hace que el segundo intento devuelva DUPLICATE. */
    public RepositoryResult addAnswer(Answer answer) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO a_answers(a_stars, a_comment, a_poll_key, a_user_key) VALUES(?,?,?,?)")) {
            statement.setInt(1, answer.getStars());
            statement.setString(2, answer.getComment());
            statement.setInt(3, answer.getPollId());
            statement.setInt(4, answer.getUserId());
            return statement.executeUpdate() == 1 ? RepositoryResult.SUCCESS : RepositoryResult.ERROR;
        } catch (SQLIntegrityConstraintViolationException e) {
            return RepositoryResult.DUPLICATE;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error al guardar la respuesta", e);
            return RepositoryResult.ERROR;
        }
    }
}
