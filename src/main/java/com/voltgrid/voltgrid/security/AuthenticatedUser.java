package com.voltgrid.voltgrid.security;


import com.voltgrid.voltgrid.enums.Role;

public record AuthenticatedUser(Long id, String email, Role role) {
}