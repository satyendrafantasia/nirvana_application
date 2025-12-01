import React, { useEffect } from 'react';
import { Text, View, Linking } from 'react-native';
import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import { useMutation } from '@tanstack/react-query';
import AppContainer from '../../components/layout/AppContainer';
import AppHeader from '../../components/layout/AppHeader';
import AppButton from '../../components/common/AppButton';
import { initiatePayment } from '../../api/paymentApi';
import { RootStackParamList } from '../../navigation/RootNavigator';
import { useBookingFlow } from '../../hooks/useBookingFlow';

const PaymentScreen = () => {
  const navigation = useNavigation();
  const route = useRoute<RouteProp<RootStackParamList, 'Payment'>>();
  const { bookingId } = route.params;
  const booking = useBookingFlow();

  const { mutateAsync, data, isPending } = useMutation(
    ({ bookingId, method }: { bookingId: string; method: 'ONLINE' | 'PAY_AT_SPA' }) =>
      initiatePayment(bookingId, method)
  );

  useEffect(() => {
    if (bookingId) {
      mutateAsync({ bookingId, method: 'ONLINE' }, { onSuccess: () => booking.reset() });
    }
  }, [bookingId, booking, mutateAsync]);

  return (
    <AppContainer>
      <AppHeader title="Payment" showBack />
      <View className="bg-white rounded-2xl p-4 mb-4 shadow-sm">
        <Text className="text-base font-semibold text-gray-900">Complete payment</Text>
        <Text className="text-sm text-gray-500 mt-2">
          We will redirect you to the payment gateway if required. You can also finalize at the
          spa if you picked pay-on-visit.
        </Text>
      </View>
      {data?.redirectUrl && (
        <AppButton label="Open payment" onPress={() => Linking.openURL(data.redirectUrl!)} />
      )}
      <AppButton
        label="View confirmation"
        className="mt-3"
        onPress={() => navigation.navigate('BookingConfirmation' as never, { bookingId } as never)}
        disabled={isPending}
      />
    </AppContainer>
  );
};

export default PaymentScreen;
