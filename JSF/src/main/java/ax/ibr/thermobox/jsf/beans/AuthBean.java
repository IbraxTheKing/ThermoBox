package ax.ibr.thermobox.jsf.beans;

import ax.ibr.thermobox.business.implementations.BusinessFactory;
import ax.ibr.thermobox.common.entities.User;
import ax.ibr.thermobox.common.services.UserService;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;

@Named
@SessionScoped
public class AuthBean implements Serializable {

    @Inject
    private UserService userService;

    private String username;
    private String password;
    private User currentUser;

    public String login() {
        if (userService == null) {
            userService = new BusinessFactory().getUserService();
        }
        User u = userService.getByUsername(username);
        if (u != null && u.isPasswordValid(password)) {
            currentUser = u;
            FacesContext.getCurrentInstance().getExternalContext()
                    .getSessionMap().put("currentUser", u);
            password = null;
            return "index.xhtml?faces-redirect=true";
        }
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
                FacesMessage.SEVERITY_ERROR, "Connexion refusée", "Identifiant ou mot de passe invalide"));
        return null;
    }

    public String logout() {
        FacesContext.getCurrentInstance().getExternalContext().invalidateSession();
        return "login.xhtml?faces-redirect=true";
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public boolean hasRole(String role) {
        return currentUser != null && currentUser.getType() != null && currentUser.getType().hasRole(role);
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public User getCurrentUser() { return currentUser; }
}