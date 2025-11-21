package com.nirvana.application.mapper;
import com.nirvana.application.model.dto.AddressDTO;
import com.nirvana.application.model.Address;
import org.springframework.stereotype.Component;

@Component
public class AddressMapper {

    public AddressDTO toDto(Address address) {
        if (address == null) return null;

        return AddressDTO.builder()
                .addressLine(address.getAddressLine())
                .addressLine2(address.getAddressLine2())
                .city(address.getCity())
                .state(address.getState())
                .postalCode(address.getPostalCode())
                .locality(address.getLocality())
                .landmark(address.getLandmark())
                .country(address.getCountry())
                .countryCode(address.getCountryCode())
                .googlePlaceId(address.getGooglePlaceId())
                .formattedAddress(address.getFormattedAddress())
                .latitude(address.getLatitude())
                .longitude(address.getLongitude())
                .timezone(address.getTimezone())
                .addressType(address.getAddressType())
                .geoSource(address.getGeoSource())
                .metaJson(address.getMetaJson())
                .build();
    }

    public Address toEntity(AddressDTO dto) {
        if (dto == null) return null;

        return Address.builder()
                .addressLine(dto.getAddressLine())
                .addressLine2(dto.getAddressLine2())
                .city(dto.getCity())
                .state(dto.getState())
                .postalCode(dto.getPostalCode())
                .locality(dto.getLocality())
                .landmark(dto.getLandmark())
                .country(dto.getCountry())
                .countryCode(dto.getCountryCode())
                .googlePlaceId(dto.getGooglePlaceId())
                .formattedAddress(dto.getFormattedAddress())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .timezone(dto.getTimezone())
                .addressType(dto.getAddressType())
                .geoSource(dto.getGeoSource())
                .metaJson(dto.getMetaJson())
                .build();
    }

    /**
     * In-place update for PATCH-style operations.
     */
    public void updateEntity(Address existing, AddressDTO dto) {
        if (existing == null || dto == null) return;

        if (dto.getAddressLine() != null) existing.setAddressLine(dto.getAddressLine());
        if (dto.getAddressLine2() != null) existing.setAddressLine2(dto.getAddressLine2());
        if (dto.getCity() != null) existing.setCity(dto.getCity());
        if (dto.getState() != null) existing.setState(dto.getState());
        if (dto.getPostalCode() != null) existing.setPostalCode(dto.getPostalCode());
        if (dto.getLocality() != null) existing.setLocality(dto.getLocality());
        if (dto.getLandmark() != null) existing.setLandmark(dto.getLandmark());
        if (dto.getCountry() != null) existing.setCountry(dto.getCountry());
        if (dto.getCountryCode() != null) existing.setCountryCode(dto.getCountryCode());
        if (dto.getGooglePlaceId() != null) existing.setGooglePlaceId(dto.getGooglePlaceId());
        if (dto.getFormattedAddress() != null) existing.setFormattedAddress(dto.getFormattedAddress());
        if (dto.getLatitude() != null) existing.setLatitude(dto.getLatitude());
        if (dto.getLongitude() != null) existing.setLongitude(dto.getLongitude());
        if (dto.getTimezone() != null) existing.setTimezone(dto.getTimezone());
        if (dto.getAddressType() != null) existing.setAddressType(dto.getAddressType());
        if (dto.getGeoSource() != null) existing.setGeoSource(dto.getGeoSource());
        if (dto.getMetaJson() != null) existing.setMetaJson(dto.getMetaJson());
    }
}
