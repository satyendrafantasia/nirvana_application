import React, { useState } from 'react';
import { Text, Pressable } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import AppContainer from '../../components/layout/AppContainer';
import AppHeader from '../../components/layout/AppHeader';
import AppTextInput from '../../components/common/AppTextInput';
import AppButton from '../../components/common/AppButton';
import { useAuth } from '../../hooks/useAuth';

const RegisterScreen = () => {
  const navigation = useNavigation();
  const { register } = useAuth();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [phone, setPhone] = useState('');

  const submit = async () => {
    await register(name, email, password, phone);
    navigation.navigate('Main' as never);
  };

  return (
    <AppContainer>
      <AppHeader title="Create account" showBack />
      <AppTextInput label="Name" value={name} onChangeText={setName} />
      <AppTextInput label="Email" value={email} onChangeText={setEmail} autoCapitalize="none" />
      <AppTextInput label="Phone" value={phone} onChangeText={setPhone} keyboardType="phone-pad" />
      <AppTextInput label="Password" value={password} onChangeText={setPassword} secureTextEntry />
      <AppButton label="Sign up" onPress={submit} className="mt-2" />
      <Pressable onPress={() => navigation.navigate('Login' as never)} className="mt-4">
        <Text className="text-center text-primary">Already have an account? Login</Text>
      </Pressable>
    </AppContainer>
  );
};

export default RegisterScreen;
