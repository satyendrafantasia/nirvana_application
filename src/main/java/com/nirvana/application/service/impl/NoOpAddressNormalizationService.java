package com.nirvana.application.service.impl;
import com.nirvana.application.model.Address;
import com.nirvana.application.service.AddressNormalizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NoOpAddressNormalizationService implements AddressNormalizationService {

    @Override
    public Address normalize(Address input) {
        if (input == null) return null;

        // In REAL impl: call Google Geocoding / Places API here.
        // This is just a placeholder to show how you'd shape the logic.

        if (input.getFormattedAddress() == null) {
            StringBuilder sb = new StringBuilder();
            if (notBlank(input.getAddressLine())) sb.append(input.getAddressLine());
            if (notBlank(input.getAddressLine2())) sb.append(", ").append(input.getAddressLine2());
            if (notBlank(input.getLocality())) sb.append(", ").append(input.getLocality());
            if (notBlank(input.getCity())) sb.append(", ").append(input.getCity());
            if (notBlank(input.getState())) sb.append(", ").append(input.getState());
            if (notBlank(input.getPostalCode())) sb.append(" - ").append(input.getPostalCode());
            if (notBlank(input.getCountry())) sb.append(", ").append(input.getCountry());
            input.setFormattedAddress(sb.toString());
        }

        // Example: if no countryCode but country present, quick dumb mapping
        if (input.getCountryCode() == null && input.getCountry() != null) {
            if ("India".equalsIgnoreCase(input.getCountry()) || "Bharat".equalsIgnoreCase(input.getCountry())) {
                input.setCountryCode("IN");
            }
            // add more if you feel like wasting time
        }

        // You can log that normalization did nothing if you haven't wired a real provider yet
        log.debug("Address normalized (no-op): {}", input);

        return input;
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}

