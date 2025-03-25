package vn.pickleball.identityservice.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.pickleball.identityservice.entity.Order;
import vn.pickleball.identityservice.service.OrderService;

import java.util.List;

@Component
@Slf4j
public class RefundScheduler {
    @Autowired
    private OrderService orderService;

//    @Scheduled(cron = "0 0/30 * * * ?")
//    private void refund(){
//        log.info("Execute refund job");
//        List<Order> orders = orderService.getUnsettledOrders();
//
//        orders.forEach(o -> orderService.refundByJob(o));
//    }
}
