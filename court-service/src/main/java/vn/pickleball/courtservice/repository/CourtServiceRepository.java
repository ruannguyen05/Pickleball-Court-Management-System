package vn.pickleball.courtservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.pickleball.courtservice.entity.CourtServiceEntity;

import java.util.List;

public interface CourtServiceRepository extends JpaRepository<CourtServiceEntity, String> {
    List<CourtServiceEntity> findByCourtId(String courtId);
}
