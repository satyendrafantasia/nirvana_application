import React from 'react';
import { Pressable, PressableProps, Text } from 'react-native';

interface Props extends PressableProps {
  label: string;
  variant?: 'primary' | 'secondary';
  className?: string;
}

const AppButton: React.FC<Props> = ({ label, variant = 'primary', className, ...rest }) => {
  const baseClasses =
    variant === 'primary'
      ? 'bg-primary'
      : 'bg-white border border-primary';

  const textClasses = variant === 'primary' ? 'text-white' : 'text-primary';

  return (
    <Pressable
      className={`w-full items-center py-3 rounded-xl ${baseClasses} ${className ?? ''}`}
      {...rest}
    >
      <Text className={`text-base font-semibold ${textClasses}`}>{label}</Text>
    </Pressable>
  );
};

export default AppButton;
