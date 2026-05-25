package com.lnzz.argus.system.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.system.model.CurrentUserResponse;
import com.lnzz.argus.system.model.LoginRequest;
import com.lnzz.argus.system.model.LoginResponse;
import com.lnzz.argus.system.model.RouteRecord;
import com.lnzz.argus.system.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Portal 认证与动态路由 API。
 *
 * @author lnzz
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthService authService;

    @PostMapping("/sessions")
    public Result<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return Result.success(authService.login(request, servletRequest));
    }

    @DeleteMapping("/sessions/current")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }

    @GetMapping("/me")
    public Result<CurrentUserResponse> me(HttpServletRequest request) {
        return Result.success(authService.currentUser(request.getRemoteAddr()));
    }

    @GetMapping("/routers")
    public Result<List<RouteRecord>> routers(HttpServletRequest request) {
        return Result.success(authService.routes(request.getRemoteAddr()));
    }
}
