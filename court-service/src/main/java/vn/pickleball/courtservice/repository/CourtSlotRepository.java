package vn.pickleball.courtservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.pickleball.courtservice.entity.Court;
import vn.pickleball.courtservice.entity.CourtSlot;

import java.util.List;
import java.util.Optional;

public interface CourtSlotRepository extends JpaRepository<CourtSlot, String> {
    List<CourtSlot> findByCourtIdOrderByCreatedAtAsc(String courtId);
    List<CourtSlot> findByCourtId(String courtId);

    Optional<CourtSlot> findByCourtIdAndName(String courtId,String name);
}
