package com.nirvana.application.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SpaRegistrationDTO {

    @NotBlank(message = "Spa name cannot be empty")
    @Pattern(regexp = "^(?!\\s*$)[A-Za-z0-9 ]+$", message = "Spa name must only contain letters and numbers")
    private String name;

    @Valid
    private AddressDTO addressDTO;

    @Valid
    private List<RoomDTO> roomDTOs = new ArrayList<>();

}
