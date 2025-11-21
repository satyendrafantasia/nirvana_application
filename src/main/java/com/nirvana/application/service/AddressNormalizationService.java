package com.nirvana.application.service;



import com.nirvana.application.model.Address;

public interface AddressNormalizationService {

    /**
     * Takes a partially filled Address and returns a normalized version:
     * - fills formattedAddress
     * - fills latitude/longitude (if missing)
     * - fills countryCode, timezone, googlePlaceId, etc. where possible
     */
    Address normalize(Address input);
}
