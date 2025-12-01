import React from 'react';
import { View, Text } from 'react-native';

interface Props {
  steps: string[];
  currentStep: number;
}

const StepIndicator: React.FC<Props> = ({ steps, currentStep }) => (
  <View className="flex-row items-center mb-4">
    {steps.map((step, index) => (
      <View key={step} className="flex-1 items-center">
        <View
          className={`w-8 h-8 rounded-full items-center justify-center ${
            index <= currentStep ? 'bg-primary' : 'bg-gray-200'
          }`}
        >
          <Text className={`${index <= currentStep ? 'text-white' : 'text-gray-500'}`}>{index + 1}</Text>
        </View>
        <Text className="text-xs text-gray-500 mt-1">{step}</Text>
      </View>
    ))}
  </View>
);

export default StepIndicator;
