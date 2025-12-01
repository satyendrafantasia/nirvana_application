import React, { useState } from 'react';
import { Text } from 'react-native';
import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import AppContainer from '../../components/layout/AppContainer';
import AppHeader from '../../components/layout/AppHeader';
import AppTextInput from '../../components/common/AppTextInput';
import AppButton from '../../components/common/AppButton';
import { verifyOtp } from '../../api/authApi';
import { AuthStackParamList } from '../../navigation/AuthNavigator';

const OtpVerificationScreen = () => {
  const navigation = useNavigation();
  const route = useRoute<RouteProp<AuthStackParamList, 'OtpVerification'>>();
  const { email } = route.params;
  const [code, setCode] = useState('');

  const submit = async () => {
    await verifyOtp(email, code);
    navigation.navigate('Login' as never);
  };

  return (
    <AppContainer>
      <AppHeader title="Enter OTP" showBack />
      <Text className="text-gray-600 mb-2">We sent a code to {email}</Text>
      <AppTextInput label="Code" value={code} onChangeText={setCode} keyboardType="numeric" />
      <AppButton label="Verify" onPress={submit} />
    </AppContainer>
  );
};

export default OtpVerificationScreen;
