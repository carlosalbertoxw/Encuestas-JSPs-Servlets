package encuestas.web;

import encuestas.data.PagedResult;
import encuestas.infrastructure.App;
import encuestas.model.Answer;
import encuestas.model.Poll;
import encuestas.service.AuthService;
import encuestas.service.Messages;
import encuestas.service.Validation;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Respuestas a las encuestas ({@code /Answer/...}): responder y ver las recibidas. */
public class AnswerServlet extends BaseServlet {

    public AnswerServlet(App app) {
        super(app);
        get("Add", this::addForm);
        post("Add", this::add);
        get("Answers", this::answers);
    }

    /** Formulario para responder una encuesta (propia o de otro usuario). */
    private void addForm(HttpServletRequest request, HttpServletResponse response, int id)
            throws ServletException, IOException {
        Poll poll = id < 1 ? null : app.getPolls().getPollById(id);
        if (poll == null) {
            notFound(response);
            return;
        }
        request.setAttribute("poll", poll);
        forward(request, response, "session/answerForm.jsp", "Responder encuesta");
    }

    private void add(HttpServletRequest request, HttpServletResponse response, int id) throws IOException {
        if (id < 1 || app.getPolls().getPollById(id) == null) {
            notFound(response);
            return;
        }
        Integer stars = Validation.parseInt(request.getParameter("stars"));
        String comment = request.getParameter("comment");
        if (!Validation.isStars(stars) || !Validation.isComment(comment)) {
            setFlash(request, Messages.VALIDATION_ERROR);
            redirect(request, response, "/Answer/Add/" + id);
            return;
        }
        Answer answer = new Answer();
        answer.setStars(stars);
        answer.setComment(comment == null ? "" : comment);
        answer.setPollId(id);
        answer.setUserId(AuthService.currentUserId(request));
        switch (app.getAnswers().addAnswer(answer)) {
            case SUCCESS -> setFlash(request, Messages.ANSWER_SAVED);
            case DUPLICATE -> setFlash(request, Messages.ANSWER_DUPLICATE);
            default -> setFlash(request, Messages.ANSWER_ERROR);
        }
        redirect(request, response, "/Poll/Index");
    }

    /** Respuestas recibidas por una encuesta; solo visibles para su propietario. */
    private void answers(HttpServletRequest request, HttpServletResponse response, int id)
            throws ServletException, IOException {
        Poll poll = id < 1 ? null : app.getPolls().getPoll(AuthService.currentUserId(request), id);
        if (poll == null) {
            notFound(response);
            return;
        }
        request.setAttribute("pollId", id);
        request.setAttribute("result",
                app.getAnswers().getAnswersForPoll(id, pageNumber(request), PagedResult.DEFAULT_PAGE_SIZE));
        forward(request, response, "session/answers.jsp", "Respuestas: " + poll.getTitle());
    }
}
