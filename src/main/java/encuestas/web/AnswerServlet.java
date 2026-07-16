package encuestas.web;

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

/**
 * Respuestas a encuestas: formulario para responder (una vez por usuario y encuesta) y
 * listado paginado de respuestas recibidas, visible solo para el dueño de la encuesta.
 */
public class AnswerServlet extends BaseServlet {

    private static final int PAGE_SIZE = 10;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!AuthService.isAuthenticated(request)) {
            redirect(request, response, "/");
            return;
        }
        switch (param(request, "page")) {
            case "add" -> addForm(request, response);
            case "answers" -> answers(request, response);
            default -> notFound(request, response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (!AuthService.isAuthenticated(request)) {
            redirect(request, response, "/");
            return;
        }
        if ("add".equals(param(request, "form"))) {
            add(request, response);
        } else {
            redirect(request, response, "/Poll?page=dashboard");
        }
    }

    /** Formulario para responder una encuesta (propia o de otro usuario). */
    private void addForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer id = Validation.parseInt(request.getParameter("id"));
        Poll poll = id == null ? null : App.get().getPolls().getPollById(id);
        if (poll == null) {
            notFound(request, response);
            return;
        }
        request.setAttribute("poll", poll);
        forward(request, response, "session/answerForm.jsp", "Responder encuesta");
    }

    private void add(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer id = Validation.parseInt(request.getParameter("id"));
        Poll poll = id == null ? null : App.get().getPolls().getPollById(id);
        if (poll == null) {
            notFound(request, response);
            return;
        }
        Integer stars = Validation.parseInt(request.getParameter("stars"));
        String comment = request.getParameter("comment");
        if (!Validation.isStars(stars) || !Validation.isComment(comment)) {
            setFlash(request, Messages.VALIDATION_ERROR);
            redirect(request, response, "/Answer?page=add&id=" + id);
            return;
        }
        Answer answer = new Answer();
        answer.setStars(stars);
        answer.setComment(comment == null ? "" : comment);
        answer.setPollId(id);
        answer.setUserId(AuthService.currentUserId(request));
        switch (App.get().getAnswers().addAnswer(answer)) {
            case SUCCESS -> setFlash(request, Messages.ANSWER_SAVED);
            case DUPLICATE -> setFlash(request, Messages.ANSWER_DUPLICATE);
            default -> setFlash(request, Messages.ANSWER_ERROR);
        }
        redirect(request, response, "/Poll?page=dashboard");
    }

    /** Respuestas recibidas por una encuesta; solo visibles para su propietario. */
    private void answers(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer id = Validation.parseInt(request.getParameter("id"));
        Poll poll = id == null ? null : App.get().getPolls().getPoll(AuthService.currentUserId(request), id);
        if (poll == null) {
            notFound(request, response);
            return;
        }
        Integer page = Validation.parseInt(request.getParameter("p"));
        request.setAttribute("pollId", id);
        request.setAttribute("result",
                App.get().getAnswers().getAnswersForPoll(id, page == null ? 1 : page, PAGE_SIZE));
        forward(request, response, "session/answers.jsp", "Respuestas: " + poll.getTitle());
    }
}
