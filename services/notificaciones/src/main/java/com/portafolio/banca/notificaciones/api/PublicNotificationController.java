package com.portafolio.banca.notificaciones.api;

import com.portafolio.banca.notificaciones.application.NotificationService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notificaciones")
public class PublicNotificationController {
    private final NotificationService notifications;

    public PublicNotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public List<NotificationService.Delivery> own(@AuthenticationPrincipal Jwt jwt) {
        return notifications.own(jwt.getSubject());
    }
}
