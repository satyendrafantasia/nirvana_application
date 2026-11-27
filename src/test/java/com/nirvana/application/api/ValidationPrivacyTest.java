package com.nirvana.application.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ValidationPrivacyTest {
    @Test void fieldErrorsIdentifyTheFieldWithoutReturningSubmittedValues() throws Exception {
        var binding = new BeanPropertyBindingResult(new Object(), "form");
        binding.addError(new FieldError("form","password","private-test-marker",false,null,null,"Invalid length"));
        var failure = mock(MethodArgumentNotValidException.class);
        when(failure.getBindingResult()).thenReturn(binding);
        var response = new GlobalExceptionHandler().handleMethodArgumentNotValid(failure,new MockHttpServletRequest());
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(response.getBody());
        assertThat(json).contains("password", "Invalid length").doesNotContain("rejectedValue","private-test-marker");
    }
}
