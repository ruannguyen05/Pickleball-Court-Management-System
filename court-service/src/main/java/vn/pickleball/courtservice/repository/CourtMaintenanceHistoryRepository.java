package vn.pickleball.courtservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.pickleball.courtservice.entity.CourtMaintenanceHistory;

import java.util.List;

public interface CourtMaintenanceHistoryRepository extends JpaRepository<CourtMaintenanceHistory, String> {
    List<CourtMaintenanceHistory> findByCourtSlotIdOrderByStartTimeDesc(String courtSlotId);
}
