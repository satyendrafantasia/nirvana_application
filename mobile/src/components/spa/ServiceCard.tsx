import React from 'react';
import { View, Text, Pressable } from 'react-native';
import { Service } from '../../types/spa';

interface Props {
  service: Service;
  onPress?: () => void;
}

const ServiceCard: React.FC<Props> = ({ service, onPress }) => {
  return (
    <Pressable
      onPress={onPress}
      className="bg-white rounded-2xl p-4 mb-3 shadow-sm"
    >
      <View className="flex-row justify-between items-center">
        <View>
          <Text className="text-base font-semibold text-gray-900">{service.name}</Text>
          {service.description && <Text className="text-sm text-gray-500 mt-1">{service.description}</Text>}
          <Text className="text-xs text-gray-400 mt-1">{service.durationMinutes} mins</Text>
        </View>
        <Text className="text-lg font-semibold text-primary">
          {service.currency} {service.price.toFixed(2)}
        </Text>
      </View>
    </Pressable>
  );
};

export default ServiceCard;
