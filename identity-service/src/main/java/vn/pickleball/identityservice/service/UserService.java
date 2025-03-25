package vn.pickleball.identityservice.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.pickleball.identityservice.constant.PredefinedRole;
import vn.pickleball.identityservice.dto.payment.NotificationResponse;
import vn.pickleball.identityservice.dto.request.UserCreationRequest;
import vn.pickleball.identityservice.dto.request.UserUpdateRequest;
import vn.pickleball.identityservice.dto.response.UserResponse;
import vn.pickleball.identityservice.entity.Role;
import vn.pickleball.identityservice.entity.User;
import vn.pickleball.identityservice.exception.ApiException;
import vn.pickleball.identityservice.exception.AppException;
import vn.pickleball.identityservice.exception.ErrorCode;
import vn.pickleball.identityservice.mapper.UserMapper;
import vn.pickleball.identityservice.repository.RoleRepository;
import vn.pickleball.identityservice.repository.UserRepository;
import vn.pickleball.identityservice.utils.GenerateString;
import vn.pickleball.identityservice.websockethandler.NotificationWebSocketHandler;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserService {
    UserRepository userRepository;
    RoleRepository roleRepository;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;
    EmailService emailService;
    NotificationWebSocketHandler socketHandler;
    RedisTemplate<String, UserCreationRequest> redisUserTemplate;


    public UserResponse createUser(UserCreationRequest request) {
        User user = userMapper.toUser(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        HashSet<Role> roles = new HashSet<>();
        roleRepository.findById(PredefinedRole.USER_ROLE).ifPresent(roles::add);

        user.setRoles(roles);

        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }

        return userMapper.toUserResponse(user);
    }

    public String registerStudent(UserCreationRequest request) {
        if (!isValidEduEmail(request.getEmail())) {
            throw new ApiException("Email must be student email (.edu.vn)", "INVALID_EMAIL");
        }
        saveUserToRedis(request);
        emailService.sendRegistrationConfirmationEmail(request.getEmail(),request.getPhoneNumber(),request.getFirstName()+request.getLastName());
        return request.getPhoneNumber();
    }

    public void forgetPass(String key){
        User user = userRepository
                .findByUsernameOrEmailOrPhoneNumber(key)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        String email = user.getEmail();

        if(email==null) throw new ApiException("Account not have email", "NO_EMAIL");
        String newPass = GenerateString.generateRandomString(6);
        user.setPassword(passwordEncoder.encode(newPass));
        userRepository.save(user);
        emailService.sendNewPasswordEmail(email,newPass);
    }


    public UserResponse confirmRegisterStudent(String phoneNumber){
        UserCreationRequest request = getUserFromRedis(phoneNumber);
        if(request == null){
            throw new ApiException("Confirmation email is expired", "INVALID_CONFIRM");
        }

        User user = userMapper.toUser(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        HashSet<Role> roles = new HashSet<>();
        roleRepository.findById(PredefinedRole.USER_ROLE).ifPresent(roles::add);

        user.setRoles(roles);
        user.setStudent(true);

        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }
//        socketHandler.sendNotification(NotificationResponse.builder()
//                        .key(phoneNumber)
//                        .resCode("200")
//                        .resDesc("Confirm student success")
//                .build());
        return userMapper.toUserResponse(user);

    }


    public boolean isValidEduEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.edu\\.vn$");
    }

    public void saveUserToRedis(UserCreationRequest user) {
        redisUserTemplate.opsForValue().set("USER:" + user.getPhoneNumber(), user, Duration.ofHours(1));
    }

    public UserCreationRequest getUserFromRedis(String phoneNumber) {
        return redisUserTemplate.opsForValue().get("USER:" + phoneNumber);
    }

    public UserResponse getMyInfo() {
        var context = SecurityContextHolder.getContext();
        String name = context.getAuthentication().getName();

        User user = userRepository.findByUsername(name).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        return userMapper.toUserResponse(user);
    }

//    @PostAuthorize("returnObject.username == authentication.name")
    public UserResponse updateUser(UserUpdateRequest request) {
        User user = userRepository.findById(request.getId()).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if(request.getPassword() != null || !request.getPassword().isEmpty()){
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        userMapper.updateUser(user, request);

//        var roles = roleRepository.findAllById(request.getRoles());
//        user.setRoles(new HashSet<>(roles));

        return userMapper.toUserResponse(userRepository.save(user));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateUserByAdmin(UserUpdateRequest request) {
        User user = userRepository.findById(request.getId()).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        userMapper.updateUser(user, request);

        var roles = roleRepository.findAllById(request.getRoles());
        user.setRoles(new HashSet<>(roles));

        return userMapper.toUserResponse(userRepository.save(user));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse createUserByAdmin(UserCreationRequest request) {
        User user = userMapper.toUser(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        var roles = roleRepository.findAllById(request.getRoles());
        user.setRoles(new HashSet<>(roles));

        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }

        return userMapper.toUserResponse(user);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(String userId) {
        userRepository.deleteById(userId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> getUsers() {
        log.info("In method get Users");
        return userRepository.findUsersWithNonAdminRole("ADMIN").stream().map(userMapper::toUserResponse).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse getUser(String id) {
        return userMapper.toUserResponse(
                userRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> getUsersByRole(String role) {
        return userRepository.findUsersWithRole(role.toUpperCase()).stream().map(userMapper::toUserResponse).toList();
    }
}
