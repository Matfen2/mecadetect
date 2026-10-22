package fr.mecadetect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/// Point d'entrée de l'API MécaDétect.
///
/// `@ConfigurationPropertiesScan` enregistre automatiquement les classes de paramétrage
/// annotées `@ConfigurationProperties` de `fr.mecadetect` et de ses sous-paquets.
@SpringBootApplication
@ConfigurationPropertiesScan
public class BackendApplication {

	/// Démarre l'application.
	///
	/// @param args arguments de la ligne de commande, transmis à Spring Boot
	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}