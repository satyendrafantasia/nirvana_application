import React from 'react';
import { Text, View } from 'react-native';

interface Props {
  message: string;
  type?: 'success' | 'error' | 'info';
}

const background = {
  success: 'bg-green-100',
  error: 'bg-red-100',
  info: 'bg-blue-100'
};

const textColor = {
  success: 'text-green-700',
  error: 'text-red-700',
  info: 'text-blue-700'
};

const Toast: React.FC<Props> = ({ message, type = 'info' }) => (
  <View className={`p-3 rounded-xl ${background[type]}`}>
    <Text className={`text-sm ${textColor[type]}`}>{message}</Text>
  </View>
);

export default Toast;
