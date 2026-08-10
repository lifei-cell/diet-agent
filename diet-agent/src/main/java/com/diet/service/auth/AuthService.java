package com.diet.service.auth;

import com.diet.exception.DietException;
import com.diet.mapper.UserMapper;
import com.diet.model.AppUser;
import com.diet.model.AuthResponse;
import com.diet.model.AuthenticatedUser;
import com.diet.model.LoginRequest;
import com.diet.model.RegisterRequest;
import com.diet.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern USERNAME = Pattern.compile("[a-zA-Z0-9_]{3,32}");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request == null) {
            throw new DietException("注册信息不能为空");
        }
        String username = normalizeUsername(request.username());
        validatePassword(request.password());
        if (userMapper.findByUsername(username) != null) {
            throw new DietException("用户名已被使用");
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(normalizeDisplayName(request.displayName(), username));
        user.setEnabled(true);
        userMapper.insert(user);
        return issueToken(user);
    }

    public AuthResponse login(LoginRequest request) {
        if (request == null) {
            throw new DietException("登录信息不能为空");
        }
        String username = normalizeUsername(request.username());
        AppUser user = userMapper.findByUsername(username);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled()) || request.password() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new DietException("用户名或密码错误");
        }
        return issueToken(user);
    }

    public AuthenticatedUser currentUser(Long userId) {
        AppUser user = userMapper.findById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new DietException("用户不存在或已被禁用");
        }
        return AuthenticatedUser.from(user);
    }

    private AuthResponse issueToken(AppUser user) {
        return new AuthResponse(jwtService.createToken(user), "Bearer", jwtService.expirationSeconds(), AuthenticatedUser.from(user));
    }

    private String normalizeUsername(String rawUsername) {
        String username = rawUsername == null ? "" : rawUsername.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(username).matches()) {
            throw new DietException("用户名须为 3 到 32 位字母、数字或下划线");
        }
        return username;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new DietException("密码长度须为 8 到 72 位");
        }
    }

    private String normalizeDisplayName(String rawDisplayName, String username) {
        String displayName = rawDisplayName == null ? "" : rawDisplayName.trim();
        if (displayName.isEmpty()) {
            return username;
        }
        if (displayName.length() > 64) {
            throw new DietException("昵称不能超过 64 个字符");
        }
        return displayName;
    }
}
