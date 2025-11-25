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

        trimWhitespace(input);
        populateFormattedAddress(input);
        populateCountryCode(input);
        populateTimezone(input);

        log.debug("Address normalized: {}", input);
        return input;
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private void trimWhitespace(Address input) {
        input.setAddressLine(trimToNull(input.getAddressLine()));
        input.setAddressLine2(trimToNull(input.getAddressLine2()));
        input.setLocality(trimToNull(input.getLocality()));
        input.setCity(trimToNull(input.getCity()));
        input.setState(trimToNull(input.getState()));
        input.setPostalCode(trimToNull(input.getPostalCode()));
        input.setCountry(trimToNull(input.getCountry()));
        input.setCountryCode(trimToNull(input.getCountryCode()));
    }

    private void populateFormattedAddress(Address input) {
        if (notBlank(input.getFormattedAddress())) {
            return;
        }
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

    private void populateCountryCode(Address input) {
        if (notBlank(input.getCountryCode())) {
            input.setCountryCode(input.getCountryCode().toUpperCase());
            return;
        }

        if (input.getCountry() == null) {
            return;
        }

        switch (input.getCountry().trim().toLowerCase()) {
            case "india":
            case "bharat":
                input.setCountryCode("IN");
                break;
            case "united states":
            case "usa":
            case "united states of america":
                input.setCountryCode("US");
                break;
            case "united kingdom":
            case "uk":
                input.setCountryCode("GB");
                break;
            default:
                break;
        }
    }

    private void populateTimezone(Address input) {
        if (notBlank(input.getTimezone())) {
            return;
        }

        if ("IN".equalsIgnoreCase(input.getCountryCode())) {
            input.setTimezone("Asia/Kolkata");
        } else if ("US".equalsIgnoreCase(input.getCountryCode())) {
            input.setTimezone("America/New_York");
        } else if ("GB".equalsIgnoreCase(input.getCountryCode())) {
            input.setTimezone("Europe/London");
        } else {
            input.setTimezone("UTC");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

