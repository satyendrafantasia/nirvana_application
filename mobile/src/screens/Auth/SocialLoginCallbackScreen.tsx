import React, { useEffect } from 'react';
import { Text } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import AppContainer from '../../components/layout/AppContainer';
import AppHeader from '../../components/layout/AppHeader';
import Loader from '../../components/common/Loader';

const SocialLoginCallbackScreen = () => {
  const navigation = useNavigation();

  useEffect(() => {
    // Handle deep link or intent data here.
    const timer = setTimeout(() => navigation.navigate('Main' as never), 800);
    return () => clearTimeout(timer);
  }, [navigation]);

  return (
    <AppContainer scrollable={false}>
      <AppHeader title="Connecting" />
      <Loader message="Completing sign-in..." />
      <Text className="text-center text-gray-500 mt-2">
        Connecting your account. You can customize this to handle Google or other social auth flows.
      </Text>
    </AppContainer>
  );
};

export default SocialLoginCallbackScreen;
