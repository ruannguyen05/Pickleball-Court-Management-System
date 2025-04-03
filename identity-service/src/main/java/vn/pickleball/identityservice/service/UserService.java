package vn.pickleball.identityservice.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import vn.pickleball.identityservice.client.CourtClient;
import vn.pickleball.identityservice.constant.PredefinedRole;
import vn.pickleball.identityservice.dto.payment.NotificationResponse;
import vn.pickleball.identityservice.dto.request.ChangePasswordRequest;
import vn.pickleball.identityservice.dto.request.UserCreationRequest;
import vn.pickleball.identityservice.dto.request.UserUpdateRequest;
import vn.pickleball.identityservice.dto.response.PagedUserResponse;
import vn.pickleball.identityservice.dto.response.UserResponse;
import vn.pickleball.identityservice.entity.Role;
import vn.pickleball.identityservice.entity.User;
import vn.pickleball.identityservice.exception.ApiException;
import vn.pickleball.identityservice.exception.AppException;
import vn.pickleball.identityservice.exception.ErrorCode;
import vn.pickleball.identityservice.mapper.UserMapper;
import vn.pickleball.identityservice.repository.RoleRepository;
import vn.pickleball.identityservice.repository.UserRepository;
import vn.pickleball.identityservice.repository.UserSpecification;
import vn.pickleball.identityservice.utils.GenerateString;
import vn.pickleball.identityservice.websockethandler.NotificationWebSocketHandler;

import java.io.IOException;
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
    private final RestTemplate restTemplate = new RestTemplate();


    public UserResponse createUser(UserCreationRequest request) {
        User user = userMapper.toUser(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        HashSet<Role> roles = new HashSet<>();
        roleRepository.findById(PredefinedRole.USER_ROLE).ifPresent(roles::add);

        user.setRoles(roles);
        user.setActive(true);

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

    public void changePassword(ChangePasswordRequest request) {
        var context = SecurityContextHolder.getContext();
        String name = context.getAuthentication().getName();

        User user = userRepository.findByUsername(name)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        boolean authenticated = passwordEncoder.matches(request.getPassword(), user.getPassword());
        if (!authenticated) {
            throw new ApiException("Current pass incorrect","FAIL_PASS");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

//    @PostAuthorize("returnObject.username == authentication.name")
    public UserResponse updateUser(UserUpdateRequest request) {
        User user = userRepository.findById(request.getId()).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

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
        user.setActive(request.isActive());

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
    public PagedUserResponse getUsers(int page, int size, String username, String phoneNumber, String email, String roleName) {
        Pageable pageable = PageRequest.of(page - 1 , size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> userPage = userRepository.findAll(
                UserSpecification.filterUsersExcludeAdmin(username, phoneNumber, email, roleName),
                pageable
        );

        List<UserResponse> userResponses = userMapper.toUsersResponses(userPage.getContent());

        return PagedUserResponse.builder()
                .users(userResponses)
                .totalPages(userPage.getTotalPages())
                .totalElements(userPage.getTotalElements())
                .build();
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

    @PreAuthorize("hasRole('ADMIN')")
    public void updateUserStatus(String userId, boolean isActive) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found with id: " + userId, "USER_NOTFOUND"));
        user.setActive(isActive);
        userRepository.save(user);
    }

    public String updateAvatar(MultipartFile file){
        var context = SecurityContextHolder.getContext();
        String name = context.getAuthentication().getName();

        User user = userRepository.findByUsername(name)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        String oldPath = user.getAvatar() ;

        String avatar = null;
        try {
            avatar = uploadAvatarCourt(file, oldPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if(avatar == null || avatar.isEmpty()) return  "Upload avatar fail";

        user.setAvatar(avatar);

        userRepository.save(user);

        return "Upload avatar success";
    }

    public String uploadAvatarCourt(MultipartFile file, String oldPath) throws IOException {
        String url = "http://203.145.46.242:8080/api/court/public/upload-avatar";

        // Chuyển MultipartFile thành ByteArrayResource
        ByteArrayResource fileAsResource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();  // Phải có để server nhận được tên file
            }
        };

        // Tạo body multipart
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileAsResource);
        body.add("oldPath", oldPath);

        // Header HTTP
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // Đóng gói HTTP request
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // Gửi request
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

        return response.getBody();
    }
}
