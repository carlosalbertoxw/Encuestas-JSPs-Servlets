package encuestas.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import encuestas.infrastructure.MigrationRunner;
import encuestas.model.Answer;
import encuestas.model.Poll;
import encuestas.model.UserProfile;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Pruebas de integración de los repositorios contra un MySQL 8.4 real y efímero
 * (Testcontainers), con el esquema aplicado por las mismas migraciones que usa la
 * aplicación. Requieren Docker; sin él se omiten.
 */
@Testcontainers(disabledWithoutDocker = true)
class RepositoryIntegrationTest {

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("encuestas")
            .withUsername("encuestas")
            .withPassword("encuestas");

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private static Database database;
    private static UserRepository users;
    private static PollRepository polls;
    private static AnswerRepository answers;

    @BeforeAll
    static void setUp() {
        database = new Database(MYSQL.getHost(), MYSQL.getFirstMappedPort(),
                "encuestas", "encuestas", "encuestas");
        MigrationRunner.run(database.getDataSource());
        users = new UserRepository(database.getDataSource());
        polls = new PollRepository(database.getDataSource());
        answers = new AnswerRepository(database.getDataSource());
    }

    @AfterAll
    static void tearDown() {
        database.close();
    }

    private static UserProfile newUser() {
        String email = "usuario" + SEQUENCE.incrementAndGet() + "@pruebas.dev";
        assertEquals(RepositoryResult.SUCCESS,
                users.createUser(email, "hash", UUID.randomUUID().toString()));
        UserProfile profile = users.getProfileByEmail(email);
        assertNotNull(profile);
        return profile;
    }

    private static Poll newPoll(int userId) {
        Poll poll = new Poll();
        poll.setTitle("Título");
        poll.setDescription("Descripción");
        poll.setPosition(1);
        poll.setUserId(userId);
        assertTrue(polls.addPoll(poll));
        return polls.getPolls(userId).get(polls.getPolls(userId).size() - 1);
    }

    @Test
    void createUserGeneratesProfileAndRejectsDuplicateEmail() {
        UserProfile profile = newUser();
        assertEquals("usuario" + profile.getUser().getId(), profile.getUserName());
        assertFalse(profile.getUser().isEmailConfirmed());
        assertEquals(RepositoryResult.DUPLICATE,
                users.createUser(profile.getUser().getEmail(), "otro", UUID.randomUUID().toString()));
    }

    @Test
    void confirmEmailIsPersisted() {
        UserProfile profile = newUser();
        assertEquals(RepositoryResult.SUCCESS, users.confirmEmail(profile.getUser().getId()));
        assertTrue(users.getUser(profile.getUser().getId()).isEmailConfirmed());
    }

    @Test
    void updateUserNameDetectsDuplicates() {
        UserProfile first = newUser();
        UserProfile second = newUser();
        assertEquals(RepositoryResult.SUCCESS,
                users.updateUserName(first.getUser().getId(), "nombre-unico-" + first.getUser().getId()));
        assertEquals(RepositoryResult.DUPLICATE,
                users.updateUserName(second.getUser().getId(), "nombre-unico-" + first.getUser().getId()));
        assertNotNull(users.getProfileByUserName("nombre-unico-" + first.getUser().getId()));
    }

    @Test
    void changePasswordRotatesSecurityStamp() {
        UserProfile profile = newUser();
        String before = users.getSecurityStamp(profile.getUser().getId());
        assertEquals(RepositoryResult.SUCCESS,
                users.changePassword(profile.getUser().getId(), "nuevo-hash", UUID.randomUUID().toString()));
        assertNotEquals(before, users.getSecurityStamp(profile.getUser().getId()));
    }

    @Test
    void pollWritesAreScopedToOwner() {
        UserProfile owner = newUser();
        UserProfile intruder = newUser();
        Poll poll = newPoll(owner.getUser().getId());

        assertNull(polls.getPoll(intruder.getUser().getId(), poll.getId()));
        poll.setUserId(intruder.getUser().getId());
        assertFalse(polls.updatePoll(poll));
        assertFalse(polls.deletePoll(intruder.getUser().getId(), poll.getId()));

        poll.setUserId(owner.getUser().getId());
        poll.setTitle("Actualizado");
        assertTrue(polls.updatePoll(poll));
        assertEquals("Actualizado", polls.getPollById(poll.getId()).getTitle());
        assertTrue(polls.deletePoll(owner.getUser().getId(), poll.getId()));
        assertNull(polls.getPollById(poll.getId()));
    }

    @Test
    void answersAreUniquePerUserAndPaginated() {
        UserProfile owner = newUser();
        Poll poll = newPoll(owner.getUser().getId());

        // 12 usuarios responden una vez cada uno; el segundo intento del primero es duplicado.
        UserProfile first = null;
        for (int i = 0; i < 12; i++) {
            UserProfile responder = newUser();
            if (first == null) {
                first = responder;
            }
            Answer answer = new Answer();
            answer.setStars(1 + i % 5);
            answer.setComment("Comentario " + i);
            answer.setPollId(poll.getId());
            answer.setUserId(responder.getUser().getId());
            assertEquals(RepositoryResult.SUCCESS, answers.addAnswer(answer));
        }
        Answer duplicate = new Answer();
        duplicate.setStars(5);
        duplicate.setComment("");
        duplicate.setPollId(poll.getId());
        duplicate.setUserId(first.getUser().getId());
        assertEquals(RepositoryResult.DUPLICATE, answers.addAnswer(duplicate));

        PagedResult<Answer> page1 = answers.getAnswersForPoll(poll.getId(), 1, 10);
        assertEquals(12, page1.getTotalCount());
        assertEquals(10, page1.getItems().size());
        assertEquals(2, page1.getTotalPages());
        assertFalse(page1.isHasPrevious());
        assertTrue(page1.isHasNext());
        assertNotNull(page1.getItems().get(0).getUserName());

        PagedResult<Answer> page2 = answers.getAnswersForPoll(poll.getId(), 2, 10);
        assertEquals(2, page2.getItems().size());
        assertTrue(page2.isHasPrevious());
        assertFalse(page2.isHasNext());
    }

    @Test
    void deleteAccountCascadesToPollsAndAnswers() {
        UserProfile owner = newUser();
        UserProfile responder = newUser();
        Poll poll = newPoll(owner.getUser().getId());
        Answer answer = new Answer();
        answer.setStars(4);
        answer.setComment("Se borrará en cascada");
        answer.setPollId(poll.getId());
        answer.setUserId(responder.getUser().getId());
        assertEquals(RepositoryResult.SUCCESS, answers.addAnswer(answer));

        assertEquals(RepositoryResult.SUCCESS, users.deleteAccount(owner.getUser().getId()));
        assertNull(users.getUser(owner.getUser().getId()));
        assertNull(polls.getPollById(poll.getId()));
        assertEquals(0, answers.getAnswersForPoll(poll.getId(), 1, 10).getTotalCount());
    }
}
