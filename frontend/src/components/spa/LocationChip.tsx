interface LocationChipProps {
  distanceKm?: number;
  city?: string;
}

const LocationChip = ({ distanceKm, city }: LocationChipProps) => {
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-3 py-1 text-xs text-slate-700">
      📍 {city ? `${city}` : 'Nearby'} {distanceKm ? `• ${distanceKm.toFixed(1)} km` : ''}
    </span>
  );
};

export default LocationChip;
