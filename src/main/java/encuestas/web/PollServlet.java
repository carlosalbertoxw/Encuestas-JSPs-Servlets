package encuestas.web;

import encuestas.data.PagedResult;
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

/** Tablero de encuestas del usuario autenticado ({@code /Poll/...}): listado por posición y CRUD. */
public class PollServlet extends BaseServlet {

    private static final Logger LOGGER = Logger.getLogger(PollServlet.class.getName());
    private static final String DASHBOARD = "/Poll/Index";

    public PollServlet(App app) {
        super(app);
        get("Index", (request, response, id) -> dashboard(request, response));
        get("Add", (request, response, id) -> showForm(request, response, new Poll()));
        get("Edit", this::edit);
        post("Add", (request, response, id) -> save(request, response, false));
        post("Update", (request, response, id) -> save(request, response, true));
        post("Delete", (request, response, id) -> delete(request, response));
    }

    private void dashboard(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("result", app.getPolls().getPolls(AuthService.currentUserId(request),
                pageNumber(request), PagedResult.DEFAULT_PAGE_SIZE));
        forward(request, response, "session/dashboard.jsp", "Tablero");
    }

    private void edit(HttpServletRequest request, HttpServletResponse response, int id)
            throws ServletException, IOException {
        Poll poll = id < 1 ? null : app.getPolls().getPoll(AuthService.currentUserId(request), id);
        if (poll == null) {
            notFound(response);
            return;
        }
        showForm(request, response, poll);
    }

    private void showForm(HttpServletRequest request, HttpServletResponse response, Poll poll)
            throws ServletException, IOException {
        request.setAttribute("poll", poll);
        forward(request, response, "session/pollForm.jsp", poll.getId() == 0 ? "Agregar encuesta" : "Editar encuesta");
    }

    private void save(HttpServletRequest request, HttpServletResponse response, boolean update)
            throws ServletException, IOException {
        Integer id = Validation.parseInt(request.getParameter("id"));
        if (update && (id == null || id < 1)) {
            notFound(response);
            return;
        }
        Poll poll = new Poll();
        poll.setId(update ? id : 0);
        poll.setTitle(param(request, "title"));
        poll.setDescription(param(request, "description"));
        Integer position = Validation.parseInt(request.getParameter("position"));
        poll.setPosition(position == null ? 0 : position);
        poll.setUserId(AuthService.currentUserId(request));
        if (!Validation.isPollTitle(poll.getTitle()) || !Validation.isPollDescription(poll.getDescription())
                || !Validation.isPollPosition(position)) {
            // Se vuelve a mostrar el formulario con lo escrito.
            request.setAttribute("message", Messages.VALIDATION_ERROR);
            showForm(request, response, poll);
            return;
        }
        if (update) {
            setFlash(request, app.getPolls().updatePoll(poll) ? Messages.POLL_UPDATED : Messages.UPDATE_ERROR);
        } else {
            setFlash(request, app.getPolls().addPoll(poll) ? Messages.POLL_SAVED : Messages.POLL_SAVE_ERROR);
        }
        redirect(request, response, DASHBOARD);
    }

    private void delete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer id = Validation.parseInt(request.getParameter("id"));
        if (id == null || id < 1) {
            notFound(response);
            return;
        }
        int userId = AuthService.currentUserId(request);
        if (app.getPolls().deletePoll(userId, id)) {
            LOGGER.log(Level.INFO, "Encuesta {0} eliminada por el usuario {1}", new Object[]{id, userId});
            setFlash(request, Messages.POLL_DELETED);
        } else {
            setFlash(request, Messages.POLL_DELETE_ERROR);
        }
        redirect(request, response, DASHBOARD);
    }
}
