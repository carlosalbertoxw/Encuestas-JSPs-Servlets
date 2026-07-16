package encuestas.model;

/**
 * Respuesta a una encuesta: calificación de 1 a 5 estrellas y comentario opcional.
 * Única por usuario y encuesta.
 */
public class Answer {

    private int id;
    private int stars;
    private String comment;
    private int pollId;
    private int userId;
    /** Nombre de usuario del autor; se llena al listar respuestas. */
    private String userName;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getStars() {
        return stars;
    }

    public void setStars(int stars) {
        this.stars = stars;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public int getPollId() {
        return pollId;
    }

    public void setPollId(int pollId) {
        this.pollId = pollId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}
