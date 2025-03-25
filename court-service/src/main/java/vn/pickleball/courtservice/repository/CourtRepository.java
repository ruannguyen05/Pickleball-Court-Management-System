package vn.pickleball.courtservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.pickleball.courtservice.entity.Court;

import java.util.List;

public interface CourtRepository extends JpaRepository<Court, String> {
    List<Court> findByManagerId(String managerId);
}
