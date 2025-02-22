package vn.pickleball.courtservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.pickleball.courtservice.entity.Court;
import vn.pickleball.courtservice.entity.CourtPrice;

import java.util.List;

public interface CourtPriceRepository extends JpaRepository<CourtPrice, String> {

    List<CourtPrice> findByCourtId(String courtId);
}
