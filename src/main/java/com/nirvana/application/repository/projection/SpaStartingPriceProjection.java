package com.nirvana.application.repository.projection;

/**
 * Projection for spa starting price lookup.
 */
public interface SpaStartingPriceProjection {
    Long getSpaId();

    Integer getStartingPriceCents();
}

