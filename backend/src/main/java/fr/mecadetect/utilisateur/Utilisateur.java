package fr.mecadetect.utilisateur;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Utilisateur de l'application. Un technicien est un utilisateur
 * dont le rôle vaut TECHNICIEN : il n'y a pas d'entité séparée.
 */
@Entity
@Table(name = "utilisateur")
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_utilisateur")
    private Long id;

    @Column(name = "nom", nullable = false, length = 50)
    private String nom;

    @Column(name = "prenom", nullable = false, length = 50)
    private String prenom;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "mot_de_passe_hash", nullable = false, length = 100)
    private String motDePasseHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    /** Constructeur requis par JPA, non utilisable dans le code métier. */
    protected Utilisateur() {
    }

    public Utilisateur(String nom, String prenom, String email, String motDePasseHash, Role role) {
        this.nom = Objects.requireNonNull(nom, "nom");
        this.prenom = Objects.requireNonNull(prenom, "prenom");
        this.email = Objects.requireNonNull(email, "email").toLowerCase();
        this.motDePasseHash = Objects.requireNonNull(motDePasseHash, "motDePasseHash");
        this.role = Objects.requireNonNull(role, "role");
    }

    /** Désactive le compte sans le supprimer, pour conserver l'historique. */
    public void desactiver() {
        this.actif = false;
    }

    public void reactiver() {
        this.actif = true;
    }

    public String getNomComplet() {
        return prenom + " " + nom;
    }

    public Long getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public String getEmail() {
        return email;
    }

    public String getMotDePasseHash() {
        return motDePasseHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActif() {
        return actif;
    }

    // Égalité basée sur l'identifiant : recommandée pour les entités JPA
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Utilisateur autre)) return false;
        return id != null && id.equals(autre.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}