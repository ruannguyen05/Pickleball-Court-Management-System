package vn.pickleball.identityservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import vn.pickleball.identityservice.dto.request.UpdateBookingSlot;

@FeignClient(name = "court-client", url = "${court_service.url_service}")
public interface CourtClient {

    @PostMapping("${court_service.update_bookingSlot}")
    ResponseEntity<Void> updateBookingSlots(@RequestBody UpdateBookingSlot updateBookingSlot);
}

