import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import LoginScreen from '../screens/Auth/LoginScreen';
import RegisterScreen from '../screens/Auth/RegisterScreen';
import OtpVerificationScreen from '../screens/Auth/OtpVerificationScreen';
import SocialLoginCallbackScreen from '../screens/Auth/SocialLoginCallbackScreen';

export type AuthStackParamList = {
  Login: undefined;
  Register: undefined;
  OtpVerification: { email: string };
  SocialLoginCallback: undefined;
};

const Stack = createNativeStackNavigator<AuthStackParamList>();

const AuthNavigator = () => (
  <Stack.Navigator>
    <Stack.Screen name="Login" component={LoginScreen} options={{ headerShown: false }} />
    <Stack.Screen name="Register" component={RegisterScreen} options={{ headerShown: false }} />
    <Stack.Screen
      name="OtpVerification"
      component={OtpVerificationScreen}
      options={{ title: 'Verify OTP' }}
    />
    <Stack.Screen
      name="SocialLoginCallback"
      component={SocialLoginCallbackScreen}
      options={{ title: 'Connecting account' }}
    />
  </Stack.Navigator>
);

export default AuthNavigator;
