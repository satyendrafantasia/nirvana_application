interface RatingBadgeProps {
  rating?: number;
  count?: number;
}

const RatingBadge = ({ rating, count = 0 }: RatingBadgeProps) => {
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-700">
      {rating == null ? 'Not rated yet' : `★ ${rating.toFixed(1)}`}{' '}
      {count > 0 && <span className="text-amber-600">({count})</span>}
    </span>
  );
};

export default RatingBadge;
