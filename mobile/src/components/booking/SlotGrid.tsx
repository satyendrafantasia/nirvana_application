import React from 'react';
import { FlatList, Pressable, Text, View } from 'react-native';
import { Slot } from '../../types/booking';

interface Props {
  slots: Slot[];
  selectedSlotId?: string;
  onSelect: (slot: Slot) => void;
}

const SlotGrid: React.FC<Props> = ({ slots, selectedSlotId, onSelect }) => (
  <FlatList
    data={slots}
    numColumns={3}
    keyExtractor={item => item.id}
    columnWrapperStyle={{ gap: 8 }}
    contentContainerStyle={{ gap: 8 }}
    renderItem={({ item }) => {
      const isSelected = selectedSlotId === item.id;
      return (
        <Pressable
          disabled={!item.available}
          onPress={() => onSelect(item)}
          className={`flex-1 rounded-xl border px-3 py-3 items-center ${
            isSelected ? 'border-primary bg-primary/10' : 'border-gray-200'
          } ${!item.available ? 'opacity-50' : ''}`}
        >
          <Text className="text-sm text-gray-900">{item.startTime}</Text>
          <Text className="text-xs text-gray-400">{item.endTime}</Text>
        </Pressable>
      );
    }}
  />
);

export default SlotGrid;
