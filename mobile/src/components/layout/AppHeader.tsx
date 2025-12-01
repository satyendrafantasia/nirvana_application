import React from 'react';
import { View, Text, Pressable } from 'react-native';
import { useNavigation } from '@react-navigation/native';

interface Props {
  title: string;
  showBack?: boolean;
  rightAction?: React.ReactNode;
}

const AppHeader: React.FC<Props> = ({ title, showBack = false, rightAction }) => {
  const navigation = useNavigation();

  return (
    <View className="flex-row items-center justify-between py-4">
      <View className="flex-row items-center">
        {showBack && (
          <Pressable onPress={() => navigation.goBack()} className="mr-3">
            <Text className="text-primary text-lg">Back</Text>
          </Pressable>
        )}
        <Text className="text-xl font-semibold text-gray-900">{title}</Text>
      </View>
      {rightAction}
    </View>
  );
};

export default AppHeader;
