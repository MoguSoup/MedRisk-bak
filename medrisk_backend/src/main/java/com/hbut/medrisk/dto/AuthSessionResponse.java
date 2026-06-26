package com.hbut.medrisk.dto;

public record AuthSessionResponse(String token, CurrentUserResponse user) {
}
