package vn.pickleball.identityservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.pickleball.identityservice.dto.request.NotificationRequest;
import vn.pickleball.identityservice.dto.response.NotificationResponse;
import vn.pickleball.identityservice.entity.Notification;
import vn.pickleball.identityservice.entity.User;
import vn.pickleball.identityservice.mapper.NotificationMapper;
import vn.pickleball.identityservice.repository.NotificationRepository;
import vn.pickleball.identityservice.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final UserRepository userRepository;

    // Lưu thông báo mới
    public NotificationRequest saveNotification(NotificationRequest request, String phoneNumber) {
        User user = userRepository.findByIdOrPhoneNumber(phoneNumber)
                .orElseThrow(() -> null);

        Notification notification = notificationMapper.toEntity(request);
        notification.setUser(user);
        notification.setPhoneNumber(phoneNumber);
        notification.setCreateAt(LocalDateTime.now());

        return notificationMapper.toDto(notificationRepository.save(notification));
    }

    // Cập nhật trạng thái thông báo
    public NotificationRequest updateStatus(String id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        notification.setStatus("read");
        return notificationMapper.toDto(notificationRepository.save(notification));
    }

    public NotificationResponse getAllNotifications(String value) {
        List<NotificationRequest> notifications = notificationMapper.toDtoList( notificationRepository.findByUserIdOrPhoneNumber(value));
        Long totalCount = notificationRepository.countByIdOrPhoneNumber(value);
        Long unreadCount = notificationRepository.countUnreadByIdOrPhoneNumber(value);
        return NotificationResponse.builder()
                .notifications(notifications)
                .totalCount(totalCount)
                .unreadCount(unreadCount)
                .build();
    }

    public Long courtUnRead(String value){
        return notificationRepository.countUnreadByIdOrPhoneNumber(value);
    }
}
