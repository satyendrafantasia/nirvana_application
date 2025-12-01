import React from 'react';
import { Text, View } from 'react-native';
import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import AppContainer from '../../components/layout/AppContainer';
import AppHeader from '../../components/layout/AppHeader';
import AppButton from '../../components/common/AppButton';
import { RootStackParamList } from '../../navigation/RootNavigator';

const BookingConfirmationScreen = () => {
  const route = useRoute<RouteProp<RootStackParamList, 'BookingConfirmation'>>();
  const navigation = useNavigation();
  const { bookingId } = route.params;

  return (
    <AppContainer scrollable={false}>
      <AppHeader title="Confirmed" />
      <View className="bg-white rounded-2xl p-6 mb-4 shadow-sm items-center">
        <Text className="text-3xl mb-3">🎉</Text>
        <Text className="text-xl font-semibold text-gray-900">Booking confirmed</Text>
        <Text className="text-sm text-gray-600 mt-2 text-center">
          Your booking reference is {bookingId}. You will receive notifications and reminders before
          your visit.
        </Text>
      </View>
      <AppButton label="Back to home" onPress={() => navigation.navigate('Main' as never)} />
    </AppContainer>
  );
};

export default BookingConfirmationScreen;
