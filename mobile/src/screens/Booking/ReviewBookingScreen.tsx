import React, { useEffect } from 'react';
import { Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useMutation } from '@tanstack/react-query';
import AppContainer from '../../components/layout/AppContainer';
import AppHeader from '../../components/layout/AppHeader';
import StepIndicator from '../../components/booking/StepIndicator';
import PaymentMethodCard from '../../components/booking/PaymentMethodCard';
import AppButton from '../../components/common/AppButton';
import { useBookingFlow } from '../../hooks/useBookingFlow';
import { useAuth } from '../../hooks/useAuth';
import { createBooking } from '../../api/bookingApi';

const steps = ['Service', 'Slot', 'Therapist', 'Review'];

const ReviewBookingScreen = () => {
  const navigation = useNavigation();
  const booking = useBookingFlow();
  const { user } = useAuth();

  useEffect(() => {
    if (!booking.selectedService) navigation.goBack();
  }, [booking.selectedService, navigation]);

  const { mutateAsync, isPending } = useMutation(createBooking);

  const confirm = async () => {
    if (!booking.spaId || !booking.selectedService || !booking.selectedSlot || !booking.paymentMethod) {
      return;
    }

    if (!user) {
      navigation.navigate('Auth' as never, undefined as never);
      return;
    }

    const payload = {
      spaId: booking.spaId,
      serviceId: booking.selectedService.id,
      slotId: booking.selectedSlot.id,
      therapistId: booking.selectedTherapist?.id,
      paymentMethod: booking.paymentMethod,
      source: booking.bookingSource
    };

    const result = await mutateAsync(payload);
    if (booking.paymentMethod === 'ONLINE') {
      navigation.navigate('Payment' as never, { bookingId: result.id } as never);
    } else {
      navigation.navigate('BookingConfirmation' as never, { bookingId: result.id } as never);
      booking.reset();
    }
  };

  return (
    <AppContainer>
      <AppHeader title="Review" showBack />
      <StepIndicator steps={steps} currentStep={3} />
      <View className="bg-white rounded-2xl p-4 mb-4 shadow-sm">
        <Text className="text-base font-semibold text-gray-900">{booking.spaName}</Text>
        <Text className="text-sm text-gray-600 mt-1">{booking.selectedService?.name}</Text>
        <Text className="text-sm text-gray-500 mt-1">{booking.selectedSlot?.startTime}</Text>
        {booking.selectedTherapist && (
          <Text className="text-sm text-gray-500 mt-1">Therapist: {booking.selectedTherapist.name}</Text>
        )}
      </View>
      <Text className="text-lg font-semibold text-gray-900 mb-2">Payment</Text>
      <PaymentMethodCard
        label="Pay online"
        description="Securely pay now to confirm"
        selected={booking.paymentMethod === 'ONLINE'}
        onPress={() => booking.setPaymentMethod('ONLINE')}
      />
      <PaymentMethodCard
        label="Pay at spa"
        description="Reserve now, pay at the venue"
        selected={booking.paymentMethod === 'PAY_AT_SPA'}
        onPress={() => booking.setPaymentMethod('PAY_AT_SPA')}
      />
      <AppButton label="Confirm booking" className="mt-4" onPress={confirm} disabled={isPending} />
    </AppContainer>
  );
};

export default ReviewBookingScreen;
