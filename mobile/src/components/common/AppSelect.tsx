import React from 'react';
import { Text, View, Pressable } from 'react-native';

interface Option {
  label: string;
  value: string;
}

interface Props {
  label?: string;
  options: Option[];
  selected?: string;
  onSelect: (value: string) => void;
}

const AppSelect: React.FC<Props> = ({ label, options, selected, onSelect }) => (
  <View className="mb-4">
    {label && <Text className="text-sm text-gray-600 mb-1">{label}</Text>}
    <View className="bg-white rounded-xl border border-gray-200">
      {options.map(option => {
        const isSelected = option.value === selected;
        return (
          <Pressable
            key={option.value}
            className={`px-4 py-3 ${isSelected ? 'bg-primary/10' : ''}`}
            onPress={() => onSelect(option.value)}
          >
            <Text className={`text-base ${isSelected ? 'text-primary font-semibold' : 'text-gray-800'}`}>
              {option.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  </View>
);

export default AppSelect;
