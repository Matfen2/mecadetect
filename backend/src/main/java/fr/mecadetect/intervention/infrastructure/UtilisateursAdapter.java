package fr.mecadetect.intervention.infrastructure;

import fr.mecadetect.intervention.domain.Utilisateurs;
import fr.mecadetect.utilisateur.Utilisateur;
import fr.mecadetect.utilisateur.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/// Adaptateur : implémente le port [Utilisateurs] avec le repository Spring Data des comptes.
@Repository
@RequiredArgsConstructor
class UtilisateursAdapter implements Utilisateurs {

    private final UtilisateurRepository utilisateurRepository;

    @Override
    public Optional<Utilisateur> trouver(Long idUtilisateur) {
        return utilisateurRepository.findById(idUtilisateur);
    }
}
