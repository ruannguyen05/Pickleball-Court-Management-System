package vn.pickleball.identityservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.pickleball.identityservice.dto.response.OrderPage;
import vn.pickleball.identityservice.dto.response.TransactionDto;
import vn.pickleball.identityservice.dto.response.TransactionResponse;
import vn.pickleball.identityservice.entity.Order;
import vn.pickleball.identityservice.service.DashboardService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {
    private final DashboardService dashboardService;

    @GetMapping("/transactions")
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
    public List<TransactionDto> getTransactionsByOid(@RequestParam String orderId){
        return dashboardService.getTransactionByOrderId(orderId);
    }
}
