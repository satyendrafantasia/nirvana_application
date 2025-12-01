import React from 'react';
import { View, Text, Pressable, Image } from 'react-native';
import { SpaSummary } from '../../types/spa';

interface Props {
  spa: SpaSummary;
  onPress?: () => void;
}

const SpaCard: React.FC<Props> = ({ spa, onPress }) => {
  return (
    <Pressable
      onPress={onPress}
      className="bg-white rounded-2xl p-4 mb-3 shadow-sm flex-row"
    >
      {spa.coverImageUrl ? (
        <Image source={{ uri: spa.coverImageUrl }} className="h-20 w-20 rounded-xl mr-3" />
      ) : (
        <View className="h-20 w-20 rounded-xl mr-3 bg-primary/10 items-center justify-center">
          <Text className="text-primary font-semibold">SPA</Text>
        </View>
      )}
      <View className="flex-1">
        <Text className="text-lg font-semibold text-gray-900">{spa.name}</Text>
        <Text className="text-sm text-gray-500 mt-1" numberOfLines={1}>
          {spa.address}
        </Text>
        <View className="flex-row items-center mt-2">
          {spa.rating && (
            <Text className="text-xs text-yellow-500 mr-2">★ {spa.rating.toFixed(1)}</Text>
          )}
          {spa.reviewCount !== undefined && (
            <Text className="text-xs text-gray-400">{spa.reviewCount} reviews</Text>
          )}
          {spa.distanceKm !== undefined && (
            <Text className="text-xs text-gray-400 ml-2">{spa.distanceKm.toFixed(1)} km</Text>
          )}
        </View>
      </View>
    </Pressable>
  );
};

export default SpaCard;
