import React, { useState } from 'react';
import { Text, View, Pressable } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import AppContainer from '../../components/layout/AppContainer';
import AppHeader from '../../components/layout/AppHeader';
import AppTextInput from '../../components/common/AppTextInput';
import AppButton from '../../components/common/AppButton';
import { useAuth } from '../../hooks/useAuth';

const LoginScreen = () => {
  const navigation = useNavigation();
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const submit = async () => {
    await login(email, password);
    navigation.navigate('Main' as never);
  };

  return (
    <AppContainer>
      <AppHeader title="Welcome back" />
      <AppTextInput label="Email" value={email} onChangeText={setEmail} autoCapitalize="none" />
      <AppTextInput label="Password" value={password} onChangeText={setPassword} secureTextEntry />
      <AppButton label="Login" onPress={submit} className="mt-2" />
      <Pressable onPress={() => navigation.navigate('Register' as never)} className="mt-4">
        <Text className="text-center text-primary">Create an account</Text>
      </Pressable>
    </AppContainer>
  );
};

export default LoginScreen;
