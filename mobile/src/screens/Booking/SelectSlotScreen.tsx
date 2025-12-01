import React, { useEffect, useState } from 'react';
import { Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import AppContainer from '../../components/layout/AppContainer';
import AppHeader from '../../components/layout/AppHeader';
import StepIndicator from '../../components/booking/StepIndicator';
import SlotGrid from '../../components/booking/SlotGrid';
import AppButton from '../../components/common/AppButton';
import { useBookingFlow } from '../../hooks/useBookingFlow';
import { getSlots } from '../../api/bookingApi';

const steps = ['Service', 'Slot', 'Therapist', 'Review'];

const SelectSlotScreen = () => {
  const navigation = useNavigation();
  const booking = useBookingFlow();
  const [date, setDate] = useState(booking.selectedDate ?? dayjs().format('YYYY-MM-DD'));

  useEffect(() => {
    booking.setDate(date);
  }, [booking, date]);

  const { data: slots } = useQuery(['slots', booking.spaId, booking.selectedService?.id, date], () => {
    if (!booking.spaId || !booking.selectedService) return Promise.resolve([]);
    return getSlots(booking.spaId, { serviceId: booking.selectedService.id, date });
  });

  return (
    <AppContainer>
      <AppHeader title="Select a slot" showBack />
      <StepIndicator steps={steps} currentStep={1} />
      <View className="bg-white rounded-2xl p-4 mb-4 shadow-sm">
        <Text className="text-sm text-gray-500 mb-2">Date</Text>
        <View className="flex-row justify-between items-center">
          <Text className="text-base font-semibold">{dayjs(date).format('dddd, MMM D')}</Text>
          <Text
            className="text-primary"
            onPress={() => setDate(dayjs(date).add(1, 'day').format('YYYY-MM-DD'))}
          >
            Next day
          </Text>
        </View>
      </View>
      <SlotGrid
        slots={slots ?? []}
        selectedSlotId={booking.selectedSlot?.id}
        onSelect={slot => booking.setSlot(slot)}
      />
      <AppButton
        label="Continue"
        className="mt-4"
        onPress={() => navigation.navigate('SelectTherapist' as never)}
        disabled={!booking.selectedSlot}
      />
    </AppContainer>
  );
};

export default SelectSlotScreen;
