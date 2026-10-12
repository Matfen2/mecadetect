package fr.mecadetect.intervention;

import java.time.LocalDateTime;

/** Planification demandée par l'admin. La version est celle que l'admin a sous les yeux (UC-DB6). */
public record PlanificationCommande(long version, Long idTechnicien, LocalDateTime datePlanifiee) {
}