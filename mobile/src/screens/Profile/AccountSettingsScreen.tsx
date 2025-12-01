import React, { useState } from 'react';
import { Text, View } from 'react-native';
import { useMutation } from '@tanstack/react-query';
import AppContainer from '../../components/layout/AppContainer';
import AppHeader from '../../components/layout/AppHeader';
import AppTextInput from '../../components/common/AppTextInput';
import AppButton from '../../components/common/AppButton';
import { useAuth } from '../../hooks/useAuth';
import { updateProfile } from '../../api/userApi';

const AccountSettingsScreen = () => {
  const { user, setUser, logout } = useAuth();
  const [name, setName] = useState(user?.name ?? '');
  const [phone, setPhone] = useState(user?.phone ?? '');
  const mutation = useMutation(updateProfile, {
    onSuccess: updated => setUser(updated)
  });

  return (
    <AppContainer>
      <AppHeader title="Profile" />
      <View className="bg-white rounded-2xl p-4 mb-4 shadow-sm">
        <Text className="text-base font-semibold text-gray-900">Account</Text>
        <AppTextInput label="Name" value={name} onChangeText={setName} />
        <AppTextInput label="Phone" value={phone} onChangeText={setPhone} keyboardType="phone-pad" />
        <AppTextInput label="Email" value={user?.email} editable={false} />
        <AppButton
          label="Save"
          onPress={() => mutation.mutate({ name, phone })}
          className="mt-2"
        />
        <AppButton label="Logout" variant="secondary" onPress={logout} className="mt-3" />
      </View>
    </AppContainer>
  );
};

export default AccountSettingsScreen;
