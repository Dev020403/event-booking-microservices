package com.event_booking_app.user_service.service;

import com.event_booking_app.user_service.dto.CreateUserRequest;
import com.event_booking_app.user_service.dto.UpdateUserRequest;
import com.event_booking_app.user_service.dto.UserResponse;
import com.event_booking_app.user_service.entity.User;
import com.event_booking_app.user_service.exception.EmailAlreadyExistsException;
import com.event_booking_app.user_service.exception.UserNotFoundException;
import com.event_booking_app.user_service.mapper.UserMapper;
import com.event_booking_app.user_service.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService{

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        log.debug("Attempting to create user with email: {}", request.email());
        String normalizedEmail = request.email().toLowerCase().trim();

        if(userRepository.existsByEmail(normalizedEmail))
        {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }

        String hashedPassword = passwordEncoder.encode(request.password());
        User user = userMapper.toEntity(request,hashedPassword);
        User savedUser = userRepository.save(user);

        log.info("Created new user [id={}]", savedUser.getId());
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        log.debug("Attempting to update user [id={}]", id);

        User user = findUserOrThrow(id);

        if (request.email() != null) {
            String normalizedEmail = request.email().toLowerCase().trim();
            if (!normalizedEmail.equals(user.getEmail())
                    && userRepository.existsByEmail(normalizedEmail)) {
                throw new EmailAlreadyExistsException(normalizedEmail);
            }
        }
        userMapper.applyUpdate(request, user);
        log.info("Updated user [id={}]", id);
        return userMapper.toResponse(user);
    }

    @Override
    public UserResponse getUserById(UUID id ) {
        log.debug("Fetching user [id={}]", id);
        return userMapper.toResponse(findUserOrThrow(id));
    }

    @Override
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        log.debug("Fetching all users [page={}, size={}]", pageable.getPageNumber(), pageable.getPageSize());
        return userRepository.findAll(pageable).map(userMapper::toResponse);
    }

    @Override
    @Transactional
    public void deleteUser(UUID id) {
        log.debug("Attempting to delete user [id={}]", id);
        User user = findUserOrThrow(id);
        userRepository.delete(user);
        log.info("Deleted user [id={}]", id);
    }

    private User findUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }
}
