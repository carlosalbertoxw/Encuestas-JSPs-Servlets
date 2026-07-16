package encuestas.web;

import encuestas.infrastructure.App;
import encuestas.model.Poll;
import encuestas.service.AuthService;
import encuestas.service.Messages;
import encuestas.service.Validation;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tablero de encuestas del usuario autenticado: listado ordenado por posición y CRUD.
 */
public class PollServlet extends BaseServlet {

    private static final Logger LOGGER = Logger.getLogger(PollServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!AuthService.isAuthenticated(request)) {
            redirect(request, response, "/");
            return;
        }
        switch (param(request, "page")) {
            case "", "dashboard" -> dashboard(request, response);
            case "add" -> {
                request.setAttribute("poll", new Poll());
                forward(request, response, "session/pollForm.jsp", "Agregar encuesta");
            }
            case "edit" -> edit(request, response);
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
        switch (param(request, "form")) {
            case "add" -> save(request, response, false);
            case "update" -> save(request, response, true);
            case "delete" -> delete(request, response);
            default -> redirect(request, response, "/Poll?page=dashboard");
        }
    }

    private void dashboard(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        int userId = AuthService.currentUserId(request);
        request.setAttribute("polls", App.get().getPolls().getPolls(userId));
        forward(request, response, "session/dashboard.jsp", "Tablero");
    }

    private void edit(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer id = Validation.parseInt(request.getParameter("id"));
        Poll poll = id == null ? null : App.get().getPolls().getPoll(AuthService.currentUserId(request), id);
        if (poll == null) {
            notFound(request, response);
            return;
        }
        request.setAttribute("poll", poll);
        forward(request, response, "session/pollForm.jsp", "Editar encuesta");
    }

    private void save(HttpServletRequest request, HttpServletResponse response, boolean update)
            throws IOException {
        String title = param(request, "title");
        String description = param(request, "description");
        Integer position = Validation.parseInt(request.getParameter("position"));
        Integer id = Validation.parseInt(request.getParameter("id"));
        if (!Validation.isPollTitle(title) || !Validation.isPollDescription(description)
                || !Validation.isPollPosition(position) || (update && (id == null || id < 1))) {
            setFlash(request, Messages.VALIDATION_ERROR);
            redirect(request, response, "/Poll?page=dashboard");
            return;
        }
        Poll poll = new Poll();
        poll.setTitle(title);
        poll.setDescription(description);
        poll.setPosition(position);
        poll.setUserId(AuthService.currentUserId(request));
        if (update) {
            poll.setId(id);
            setFlash(request, App.get().getPolls().updatePoll(poll) ? Messages.POLL_UPDATED : Messages.UPDATE_ERROR);
        } else {
            setFlash(request, App.get().getPolls().addPoll(poll) ? Messages.POLL_SAVED : Messages.POLL_SAVE_ERROR);
        }
        redirect(request, response, "/Poll?page=dashboard");
    }

    private void delete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer id = Validation.parseInt(request.getParameter("id"));
        if (id == null || id < 1) {
            setFlash(request, Messages.VALIDATION_ERROR);
            redirect(request, response, "/Poll?page=dashboard");
            return;
        }
        int userId = AuthService.currentUserId(request);
        if (App.get().getPolls().deletePoll(userId, id)) {
            LOGGER.log(Level.INFO, "Encuesta {0} eliminada por el usuario {1}", new Object[]{id, userId});
            setFlash(request, Messages.POLL_DELETED);
        } else {
            setFlash(request, Messages.POLL_DELETE_ERROR);
        }
        redirect(request, response, "/Poll?page=dashboard");
    }
}
