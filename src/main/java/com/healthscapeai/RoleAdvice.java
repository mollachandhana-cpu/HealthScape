package com.healthscapeai;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class RoleAdvice {
    @ModelAttribute("isAdmin")
    boolean isAdmin(Authentication au) { return au != null && au.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")); }
}
