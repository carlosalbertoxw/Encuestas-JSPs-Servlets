package encuestas.model;

/**
 * Perfil público de una cuenta (relación 1 a 1 con {@link User}).
 */
public class UserProfile {

    private User user;
    private String userName;
    private String name;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
