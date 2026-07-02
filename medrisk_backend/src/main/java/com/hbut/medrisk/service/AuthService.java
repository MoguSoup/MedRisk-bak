package com.hbut.medrisk.service;

import com.hbut.medrisk.dto.AuthSessionResponse;
import com.hbut.medrisk.dto.CurrentUserPasswordRequest;
import com.hbut.medrisk.dto.CurrentUserResponse;
import com.hbut.medrisk.dto.CurrentUserUpdateRequest;
import com.hbut.medrisk.dto.LoginRequest;
import com.hbut.medrisk.dto.PasswordResetRequest;
import com.hbut.medrisk.dto.RegisterRequest;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.repository.UserRepository;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AuthService {
    private static final Set<String> ROLES = Set.of("PATIENT", "DOCTOR", "ADMIN");

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final FileStorageService files;
    private final EmailVerificationService emailVerification;

    public AuthService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditService auditService,
            FileStorageService files,
            EmailVerificationService emailVerification) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.files = files;
        this.emailVerification = emailVerification;
    }

    @Transactional
    public AuthSessionResponse register(RegisterRequest request) {
        String email = emailVerification.normalizeEmail(request.email());
        if (users.existsByUsername(request.username())) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (users.existsByEmail(email)) {
            throw new IllegalArgumentException("邮箱已存在");
        }
        emailVerification.verifyAndConsume(email, EmailVerificationService.PURPOSE_REGISTER, request.emailCode());
        UserEntity user = new UserEntity();
        user.setUsername(request.username().trim());
        user.setEmail(email);
        user.setName(request.name().trim());
        user.setRole(normalizeRegisterRole(request.role()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus("ACTIVE");
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        users.save(user);
        auditService.log(user.getId(), "REGISTER", "USER", user.getId().toString(), "{}");
        return sessionFor(user);
    }

    @Transactional
    public AuthSessionResponse login(LoginRequest request) {
        return login(request, null);
    }

    @Transactional
    public AuthSessionResponse login(LoginRequest request, String clientIp) {
        UserEntity user = users.findByUsername(request.username())
                .orElseThrow(() -> new AuthException("用户名或密码错误"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthException("用户名或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new AuthException("账号已被禁用，请联系管理员");
        }
        user.setLastLoginAt(LocalDateTime.now());
        auditService.log(user.getId(), "LOGIN", "USER", user.getId().toString(), "{}", clientIp);
        return sessionFor(user);
    }

    public void sendRegisterCode(String email) {
        String normalizedEmail = emailVerification.normalizeEmail(email);
        if (users.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("邮箱已存在");
        }
        emailVerification.sendCode(normalizedEmail, EmailVerificationService.PURPOSE_REGISTER);
    }

    public void sendPasswordResetCode(String email) {
        String normalizedEmail = emailVerification.normalizeEmail(email);
        if (users.findByEmail(normalizedEmail).isPresent()) {
            emailVerification.sendCode(normalizedEmail, EmailVerificationService.PURPOSE_PASSWORD_RESET);
        }
    }

    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        String email = emailVerification.normalizeEmail(request.email());
        UserEntity user = users.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("邮箱验证码不正确"));
        emailVerification.verifyAndConsume(email, EmailVerificationService.PURPOSE_PASSWORD_RESET, request.emailCode());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        auditService.log(user.getId(), "RESET_PASSWORD_BY_EMAIL", "USER", user.getId().toString(), "{}");
    }

    public UserEntity requireUser(String authorizationHeader) {
        String token = bearerToken(authorizationHeader);
        Object username = jwtService.parseToken(token).get("sub");
        if (username == null) {
            throw new AuthException("登录令牌缺少用户信息");
        }
        UserEntity user = users.findByUsername(username.toString()).orElseThrow(() -> new AuthException("用户不存在"));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new AuthException("账号已被禁用，请联系管理员");
        }
        return user;
    }

    public void requireAnyRole(UserEntity user, String... roles) {
        for (String role : roles) {
            if (user.getRole().equals(role)) {
                return;
            }
        }
        throw new SecurityException("当前账号没有访问权限");
    }

    public CurrentUserResponse toCurrentUser(UserEntity user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getLastLoginAt());
    }

    @Transactional
    public CurrentUserResponse updateCurrentUser(UserEntity user, CurrentUserUpdateRequest request) {
        UserEntity managed = managedUser(user);
        String email = request.email().trim();
        if (users.existsByEmailAndIdNot(email, managed.getId())) {
            throw new IllegalArgumentException("邮箱已被使用");
        }
        managed.setEmail(email);
        managed.setName(request.name().trim());
        managed.setPhone(blank(request.phone()) ? null : request.phone().trim());
        managed.setUpdatedAt(LocalDateTime.now());
        auditService.log(managed.getId(), "UPDATE_PROFILE", "USER", managed.getId().toString(), "{}");
        return toCurrentUser(managed);
    }

    @Transactional
    public CurrentUserResponse changePassword(UserEntity user, CurrentUserPasswordRequest request) {
        UserEntity managed = managedUser(user);
        if (!passwordEncoder.matches(request.oldPassword(), managed.getPasswordHash())) {
            throw new AuthException("旧密码不正确");
        }
        managed.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        managed.setUpdatedAt(LocalDateTime.now());
        auditService.log(managed.getId(), "CHANGE_PASSWORD", "USER", managed.getId().toString(), "{}");
        return toCurrentUser(managed);
    }

    @Transactional
    public CurrentUserResponse updateAvatar(UserEntity user, MultipartFile avatar) throws IOException {
        UserEntity managed = managedUser(user);
        if (avatar == null || avatar.isEmpty()) {
            throw new IllegalArgumentException("头像文件不能为空");
        }
        String contentType = avatar.getContentType() == null ? "" : avatar.getContentType().toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("image/")) {
            throw new IllegalArgumentException("头像必须是图片文件");
        }
        FileStorageService.StoredFile stored = files.store("avatars", avatar);
        managed.setAvatarUrl(stored.url());
        managed.setUpdatedAt(LocalDateTime.now());
        auditService.log(managed.getId(), "UPDATE_AVATAR", "USER", managed.getId().toString(), "{}");
        return toCurrentUser(managed);
    }

    private AuthSessionResponse sessionFor(UserEntity user) {
        return new AuthSessionResponse(jwtService.createToken(user), toCurrentUser(user));
    }

    private String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new AuthException("请先登录");
        }
        return authorizationHeader.substring("Bearer ".length()).trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private UserEntity managedUser(UserEntity user) {
        return users.findById(user.getId()).orElseThrow(() -> new AuthException("用户不存在"));
    }

    public String normalizeRole(String role) {
        String normalized = role == null || role.isBlank() ? "PATIENT" : role.toUpperCase(Locale.ROOT);
        if (!ROLES.contains(normalized)) {
            return "PATIENT";
        }
        return normalized;
    }

    private String normalizeRegisterRole(String role) {
        String normalized = normalizeRole(role);
        return "ADMIN".equals(normalized) ? "PATIENT" : normalized;
    }
}
