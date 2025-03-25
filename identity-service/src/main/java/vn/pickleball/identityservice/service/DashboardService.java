package vn.pickleball.identityservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pickleball.identityservice.dto.response.OrderData;
import vn.pickleball.identityservice.dto.response.OrderPage;
import vn.pickleball.identityservice.dto.response.TransactionDto;
import vn.pickleball.identityservice.dto.response.TransactionResponse;
import vn.pickleball.identityservice.entity.Order;
import vn.pickleball.identityservice.entity.Transaction;
import vn.pickleball.identityservice.mapper.OrderMapper;
import vn.pickleball.identityservice.mapper.TransactionMapper;
import vn.pickleball.identityservice.repository.OrderRepository;
import vn.pickleball.identityservice.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DashboardService {
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    @PreAuthorize("hasRole('ADMIN')")
    public TransactionResponse getTransactionsWithSummary(String paymentStatus, String courtId, String orderId,
                                                          LocalDateTime startDate, LocalDateTime endDate,
                                                          int page, int size) {

        PageRequest pageable = PageRequest.of(page - 1, size);
        var transactionsPage = transactionRepository.findTransactions(paymentStatus, courtId, orderId, startDate, endDate, pageable);

        BigDecimal totalAmount = transactionRepository.getTotalAmountExcludingRefund(courtId, startDate, endDate);
        BigDecimal refundAmount = transactionRepository.getTotalRefundAmount(courtId, startDate, endDate);
        BigDecimal netAmount = totalAmount.subtract(refundAmount);

        List<TransactionDto> transactionDtos = transactionMapper.toDtoList(transactionsPage.getContent());

        return TransactionResponse.builder()
                .transactions(transactionDtos)
                .totalPages(transactionsPage.getTotalPages())
                .totalElements(transactionsPage.getTotalElements())
                .totalAmount(totalAmount)
                .refundAmount(refundAmount)
                .netAmount(netAmount)
                .build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    public OrderPage searchOrders(String courtId, String orderStatus, String paymentStatus,
                                    LocalDate startDate, LocalDate endDate, int page, int size) {
        PageRequest pageable = PageRequest.of(page - 1, size);
        var ordersPage = orderRepository.findOrders(courtId, orderStatus, paymentStatus, startDate, endDate, pageable);

        List<OrderData> orderData = orderMapper.toDatas(ordersPage.getContent());

        return OrderPage.builder()
                .orders(orderData)
                .totalElements(ordersPage.getTotalElements())
                .totalPages(ordersPage.getTotalPages())
                .build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<TransactionDto> getTransactionByOrderId(String orderId){
        List<Transaction> transactions = transactionRepository.findByOrderId(orderId);

        return transactionMapper.toDtoList(transactions);
    }
}
