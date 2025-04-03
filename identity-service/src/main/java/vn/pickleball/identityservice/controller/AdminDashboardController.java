package vn.pickleball.identityservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.pickleball.identityservice.dto.response.OrderPage;
import vn.pickleball.identityservice.dto.response.TransactionDto;
import vn.pickleball.identityservice.dto.response.TransactionResponse;
import vn.pickleball.identityservice.entity.Order;
import vn.pickleball.identityservice.service.DashboardService;
import vn.pickleball.identityservice.service.OrderService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {
    private final DashboardService dashboardService;
    private final OrderService orderService;

    @GetMapping("/transactions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public TransactionResponse getTransactions(
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String courtId,
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return dashboardService.getTransactionsWithSummary(paymentStatus, courtId, orderId, startDate, endDate, page, size);
    }

    @GetMapping("/orders")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public OrderPage getOrders(
            @RequestParam(required = false) String courtId,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return dashboardService.searchOrders(courtId, orderStatus, paymentStatus, startDate, endDate, page, size);
    }

    @GetMapping("/getTransactionByOid")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public List<TransactionDto> getTransactionsByOid(@RequestParam String orderId){
        return dashboardService.getTransactionByOrderId(orderId);
    }

    @PostMapping("/refund")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<String> refundByAdmin(@RequestParam String orderId,
                                                @RequestParam BigDecimal refundAmount) {
        orderService.refundByAdmin(orderId, refundAmount);
        return ResponseEntity.ok("Refund processed successfully");
    }
}
