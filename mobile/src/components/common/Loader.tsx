import React from 'react';
import { ActivityIndicator, View, Text } from 'react-native';

const Loader: React.FC<{ message?: string }> = ({ message }) => (
  <View className="flex-1 items-center justify-center">
    <ActivityIndicator color="#4FD1C5" />
    {message && <Text className="text-sm text-gray-500 mt-2">{message}</Text>}
  </View>
);

export default Loader;
