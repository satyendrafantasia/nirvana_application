import React from 'react';
import { Text, View } from 'react-native';

interface Props {
  rating?: number;
  reviewCount?: number;
}

const RatingBadge: React.FC<Props> = ({ rating, reviewCount }) => {
  if (!rating) return null;

  return (
    <View className="flex-row items-center bg-yellow-50 px-2 py-1 rounded-full">
      <Text className="text-yellow-600 text-xs font-semibold">★ {rating.toFixed(1)}</Text>
      {reviewCount !== undefined && (
        <Text className="text-gray-500 text-xs ml-1">({reviewCount})</Text>
      )}
    </View>
  );
};

export default RatingBadge;
