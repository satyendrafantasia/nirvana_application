import React from 'react';
import { Pressable, Text, View } from 'react-native';

interface Props {
  label: string;
  description?: string;
  selected?: boolean;
  onPress?: () => void;
}

const PaymentMethodCard: React.FC<Props> = ({ label, description, selected, onPress }) => (
  <Pressable
    onPress={onPress}
    className={`rounded-2xl border p-4 mb-3 ${selected ? 'border-primary bg-primary/10' : 'border-gray-200'}`}
  >
    <View className="flex-row justify-between items-center">
      <View className="flex-1">
        <Text className="text-base font-semibold text-gray-900">{label}</Text>
        {description && <Text className="text-sm text-gray-500 mt-1">{description}</Text>}
      </View>
      <View
        className={`h-5 w-5 rounded-full border ${selected ? 'bg-primary border-primary' : 'border-gray-300'}`}
      />
    </View>
  </Pressable>
);

export default PaymentMethodCard;
