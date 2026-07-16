package encuestas.infrastructure;

import encuestas.data.AnswerRepository;
import encuestas.data.PollRepository;
import encuestas.data.UserRepository;
import encuestas.model.Answer;
import encuestas.model.Poll;
import encuestas.model.UserProfile;
import encuestas.service.PasswordService;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Inserta datos de demostración cuando la base de datos está vacía. Solo se invoca en el
 * entorno Development; los datos nunca deben llegar a producción. Las contraseñas se
 * hashean con PBKDF2 mediante {@link PasswordService}, igual que en el registro real.
 */
public final class DevDataSeeder {

    private static final Logger LOGGER = Logger.getLogger(DevDataSeeder.class.getName());

    private static final String DEMO_EMAIL = "demo@encuestas.dev";
    private static final String DEMO_PASSWORD = "demo1234";
    private static final String ANA_EMAIL = "ana@encuestas.dev";
    private static final String ANA_PASSWORD = "ana12345";

    public static void seed(App app) {
        UserRepository users = app.getUsers();
        PollRepository polls = app.getPolls();
        AnswerRepository answers = app.getAnswers();
        PasswordService passwords = app.getPasswords();

        if (users.getProfileByEmail(DEMO_EMAIL) != null) {
            return;
        }

        // Cuenta principal con una encuesta de ejemplo.
        users.createUser(DEMO_EMAIL, passwords.hash(DEMO_PASSWORD), UUID.randomUUID().toString());
        UserProfile demo = users.getProfileByEmail(DEMO_EMAIL);
        users.confirmEmail(demo.getUser().getId());
        users.updateUserName(demo.getUser().getId(), "demo");
        users.updateName(demo.getUser().getId(), "Usuario Demo");
        Poll poll = new Poll();
        poll.setTitle("¿Qué te parece la aplicación?");
        poll.setDescription("Cuéntanos tu experiencia usando Encuestas.");
        poll.setPosition(1);
        poll.setUserId(demo.getUser().getId());
        polls.addPoll(poll);

        // Segunda cuenta que responde la encuesta de la primera.
        users.createUser(ANA_EMAIL, passwords.hash(ANA_PASSWORD), UUID.randomUUID().toString());
        UserProfile ana = users.getProfileByEmail(ANA_EMAIL);
        users.confirmEmail(ana.getUser().getId());
        users.updateUserName(ana.getUser().getId(), "ana");
        users.updateName(ana.getUser().getId(), "Ana");
        List<Poll> demoPolls = polls.getPolls(demo.getUser().getId());
        Answer answer = new Answer();
        answer.setStars(5);
        answer.setComment("¡Muy fácil de usar!");
        answer.setPollId(demoPolls.get(0).getId());
        answer.setUserId(ana.getUser().getId());
        answers.addAnswer(answer);

        LOGGER.log(Level.INFO, "Datos de demostración insertados (solo Development).");
    }

    private DevDataSeeder() {
    }
}
