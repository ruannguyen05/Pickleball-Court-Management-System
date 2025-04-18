package vn.pickleball.identityservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.pickleball.identityservice.entity.CourtStaff;
import vn.pickleball.identityservice.entity.CourtStaffId;

import java.util.List;

@Repository
public interface CourtStaffRepository extends JpaRepository<CourtStaff, CourtStaffId> {

    List<CourtStaff> findByUserId(String userId);

    List<CourtStaff> findByCourtId(String courtId);

    boolean existsByUserIdAndCourtId(String userId, String courtId);
}