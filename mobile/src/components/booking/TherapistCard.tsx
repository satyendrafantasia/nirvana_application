import React from 'react';
import { View, Text, Pressable, Image } from 'react-native';
import { Therapist } from '../../types/booking';

interface Props {
  therapist: Therapist;
  selected?: boolean;
  onPress?: () => void;
}

const TherapistCard: React.FC<Props> = ({ therapist, selected, onPress }) => (
  <Pressable
    onPress={onPress}
    className={`flex-row items-center rounded-2xl border p-3 mb-3 ${
      selected ? 'border-primary bg-primary/10' : 'border-gray-200'
    }`}
  >
    {therapist.avatarUrl ? (
      <Image source={{ uri: therapist.avatarUrl }} className="h-12 w-12 rounded-full mr-3" />
    ) : (
      <View className="h-12 w-12 rounded-full bg-primary/20 items-center justify-center mr-3">
        <Text className="text-primary font-semibold">{therapist.name[0]}</Text>
      </View>
    )}
    <View className="flex-1">
      <Text className="text-base font-semibold text-gray-900">{therapist.name}</Text>
      {therapist.gender && <Text className="text-xs text-gray-500">{therapist.gender}</Text>}
    </View>
  </Pressable>
);

export default TherapistCard;
