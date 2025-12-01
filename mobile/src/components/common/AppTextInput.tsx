import React from 'react';
import { Text, TextInput, TextInputProps, View } from 'react-native';

interface Props extends TextInputProps {
  label?: string;
  errorMessage?: string;
  className?: string;
}

const AppTextInput: React.FC<Props> = ({ label, errorMessage, className, ...rest }) => (
  <View className="mb-4">
    {label && <Text className="text-sm text-gray-600 mb-1">{label}</Text>}
    <TextInput
      className={`border rounded-xl px-3 py-3 bg-white text-gray-900 ${className ?? ''}`}
      placeholderTextColor="#A0AEC0"
      {...rest}
    />
    {errorMessage && <Text className="text-xs text-red-500 mt-1">{errorMessage}</Text>}
  </View>
);

export default AppTextInput;
