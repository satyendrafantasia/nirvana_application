import React from 'react';
import { Text, View } from 'react-native';

interface Props {
  label: string;
}

const LocationChip: React.FC<Props> = ({ label }) => (
  <View className="bg-primary/10 px-3 py-1 rounded-full">
    <Text className="text-primary text-xs font-semibold">{label}</Text>
  </View>
);

export default LocationChip;
