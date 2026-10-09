package fr.mecadetect.referentiel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZoneRepository extends JpaRepository<Zone, Long> {

    List<Zone> findAllByOrderByCodeAsc();

    boolean existsByCodeIgnoreCase(String code);
}