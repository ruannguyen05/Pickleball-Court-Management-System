package vn.pickleball.courtservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.pickleball.courtservice.entity.Court;

public interface CourtRepository extends JpaRepository<Court, String> {
}
