package com.nirvana.application.service;

import com.nirvana.application.model.User;
import com.nirvana.application.model.dto.UserDTO;
import com.nirvana.application.model.dto.UserRegistrationDTO;

import java.util.List;

public interface UserService {

    User saveUser(UserRegistrationDTO registrationDTO);

    // For registration
    User findUserByUsername(String username);

    UserDTO findUserDTOByUsername(String username);

    UserDTO findUserById(Long id);

    List<UserDTO> findAllUsers();

    void updateUser(UserDTO userDTO);

    void updateLoggedInUser(UserDTO userDTO);

    void deleteUserById(Long id);

    User resetPassword(ResetPasswordDTO resetPasswordDTO);

}
