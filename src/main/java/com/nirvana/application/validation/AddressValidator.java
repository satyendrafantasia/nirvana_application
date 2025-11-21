package com.nirvana.application.validation;


import com.nirvana.application.model.Address;
import org.springframework.stereotype.Component;

@Component
public class AddressValidator {

    public void validate(Address address) {
        if (address == null) {
            throw new IllegalArgumentException("Address is required");
        }

        // Basic sanity checks beyond annotations
        if (isBlank(address.getAddressLine())) {
            throw new IllegalArgumentException("Address line1 is required");
        }
        if (isBlank(address.getCity())) {
            throw new IllegalArgumentException("City is required");
        }
        if (isBlank(address.getCountry())) {
            throw new IllegalArgumentException("Country is required");
        }

        // Optional: simple India-specific postal code check example
        if ("IN".equalsIgnoreCase(address.getCountryCode())
                && address.getPostalCode() != null
                && !address.getPostalCode().matches("^[1-9][0-9]{5}$")) {
            throw new IllegalArgumentException("Invalid Indian PIN code: " + address.getPostalCode());
        }

        // Lat/lon: both or none
        if (address.getLatitude() != null ^ address.getLongitude() != null) {
            throw new IllegalArgumentException("Both latitude and longitude must be set together");
        }

        // Lat/lon sanity (if present)
        if (address.getLatitude() != null) {
            double lat = address.getLatitude();
            if (lat < -90.0 || lat > 90.0) {
                throw new IllegalArgumentException("Latitude out of range [-90,90]");
            }
        }
        if (address.getLongitude() != null) {
            double lon = address.getLongitude();
            if (lon < -180.0 || lon > 180.0) {
                throw new IllegalArgumentException("Longitude out of range [-180,180]");
            }
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
