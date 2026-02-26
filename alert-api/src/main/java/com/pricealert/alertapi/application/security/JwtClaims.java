package com.pricealert.alertapi.application.security;

public record JwtClaims(String sub, String jti, Long exp) {}
