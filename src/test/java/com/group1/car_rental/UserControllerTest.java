package com.group1.car_rental;

import com.group1.car_rental.config.SecurityConfig;
import com.group1.car_rental.controller.UserController;
import com.group1.car_rental.service.DbUserDetailsService;
import com.group1.car_rental.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private DbUserDetailsService dbUserDetailsService;

    @Test
    void getLogin_shouldReturnLoginForm() throws Exception {
        mockMvc.perform(get("/login"))
               .andExpect(status().isOk())
               .andExpect(view().name("user/login"));
    }

    @Test
    void getLogin_withError_shouldReturnLoginFormWithError() throws Exception {
        mockMvc.perform(get("/login").param("error", "true"))
               .andExpect(status().isOk())
               .andExpect(view().name("user/login"))
               .andExpect(model().attribute("error", "true"));
    }
}
