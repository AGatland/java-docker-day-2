package com.booleanuk.api.controller;

import com.booleanuk.api.model.ERole;
import com.booleanuk.api.model.Role;
import com.booleanuk.api.model.User;
import com.booleanuk.api.payload.request.LoginRequest;
import com.booleanuk.api.payload.request.SignupRequest;
import com.booleanuk.api.payload.response.ErrorResponse;
import com.booleanuk.api.payload.response.JwtResponse;
import com.booleanuk.api.payload.response.MessageResponse;
import com.booleanuk.api.repository.RoleRepository;
import com.booleanuk.api.repository.UserRepository;
import com.booleanuk.api.security.jwt.JwtUtils;
import com.booleanuk.api.security.services.UserDetailsImpl;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("auth")
public class AuthController {
    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        // salting happens here
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        List<String> roles = userDetails.getAuthorities().stream()
                .map((item) -> item.getAuthority()).collect(Collectors.toList());

        return ResponseEntity.ok(new JwtResponse(jwt, userDetails.getId(),
                userDetails.getUsername(), userDetails.getEmail(), roles));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signupRequest) {
        if (this.userRepository.existsByUsername(signupRequest.getUsername())) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: Username is already taken."));
        }
        if (this.userRepository.existsByEmail(signupRequest.getEmail())) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: Email is already in use."));
        }
        // Salting here
        User user = new User(signupRequest.getUsername(), signupRequest.getEmail(), encoder.encode(signupRequest.getPassword()));
        Set<String> strRoles = signupRequest.getRole();
        Set<Role> roles = new HashSet<>();

        if (strRoles == null) {
            Role userRole = roleRepository.findByName(ERole.ROLE_USER)
                    .orElseThrow(() -> new RuntimeException("Error: Role not found"));
            roles.add(userRole);
        } else {
            strRoles.forEach((role) -> {
                switch (role) {
                    case "admin":
                        Role adminRole = roleRepository.findByName(ERole.ROLE_ADMIN)
                                .orElseThrow(() -> new RuntimeException("Error: Role not found"));
                        roles.add(adminRole);
                        break;
                    case "mod":
                        Role modRole = roleRepository.findByName(ERole.ROLE_MODERATOR)
                                .orElseThrow(() -> new RuntimeException("Error: Role not found"));
                        roles.add(modRole);
                        break;
                    default:
                        Role userRole = roleRepository.findByName(ERole.ROLE_USER)
                                .orElseThrow(() -> new RuntimeException("Error: Role not found"));
                        roles.add(userRole);
                        break;
                }
            });
        }
        user.setRoles(roles);
        userRepository.save(user);
        return ResponseEntity.ok(new MessageResponse("User registered successfully"));
    }

    @PostMapping("/addroles")
    public ResponseEntity<?> fillRoles() {
        if (!roleRepository.existsByName(ERole.ROLE_USER)) {
            this.roleRepository.save(new Role(ERole.ROLE_USER));
        }
        if (!roleRepository.existsByName(ERole.ROLE_MODERATOR)) {
            this.roleRepository.save(new Role(ERole.ROLE_MODERATOR));
        }
        if (!roleRepository.existsByName(ERole.ROLE_ADMIN)) {
            this.roleRepository.save(new Role(ERole.ROLE_ADMIN));
        }
        return ResponseEntity.ok(new MessageResponse("Roles added to DB"));
    }

    @PostMapping("/addadmin/{userId}")
    public ResponseEntity<?> fillRoles(@PathVariable int userId) {
        User user = this.userRepository.findById(userId).orElse(null);
        Role role = this.roleRepository.findByName(ERole.ROLE_ADMIN).orElse(null);
        if (user == null || role == null) {
            ErrorResponse error = new ErrorResponse();
            error.set("not found");
            return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
        }
        Set<Role> roles = user.getRoles();
        roles.add(role);
        user.setRoles(roles);
        this.userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("Admin role added to user"));
    }
}
