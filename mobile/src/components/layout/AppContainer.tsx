import React from 'react';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ScrollView, View, ViewProps } from 'react-native';

interface Props extends ViewProps {
  scrollable?: boolean;
  className?: string;
}

const AppContainer: React.FC<Props> = ({ children, scrollable = true, className, ...rest }) => {
  if (scrollable) {
    return (
      <SafeAreaView className="flex-1 bg-background">
        <ScrollView className="flex-1 px-4" contentContainerStyle={{ paddingVertical: 16 }} {...rest}>
          {children}
        </ScrollView>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView className="flex-1 bg-background">
      <View className={`flex-1 px-4 py-4 ${className ?? ''}`} {...rest}>
        {children}
      </View>
    </SafeAreaView>
  );
};

export default AppContainer;
