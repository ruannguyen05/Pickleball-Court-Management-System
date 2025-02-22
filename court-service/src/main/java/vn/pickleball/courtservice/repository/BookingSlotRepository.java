package vn.pickleball.courtservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.pickleball.courtservice.entity.BookingSlot;
import vn.pickleball.courtservice.entity.Court;

public interface BookingSlotRepository extends JpaRepository<BookingSlot, String> {
}
