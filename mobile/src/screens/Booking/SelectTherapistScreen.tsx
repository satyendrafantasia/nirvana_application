import React, { useState } from 'react';
import { Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useQuery } from '@tanstack/react-query';
import AppContainer from '../../components/layout/AppContainer';
import AppHeader from '../../components/layout/AppHeader';
import StepIndicator from '../../components/booking/StepIndicator';
import AppSelect from '../../components/common/AppSelect';
import TherapistCard from '../../components/booking/TherapistCard';
import AppButton from '../../components/common/AppButton';
import { useBookingFlow } from '../../hooks/useBookingFlow';
import { getTherapists } from '../../api/bookingApi';
import { Therapist } from '../../types/booking';

const steps = ['Service', 'Slot', 'Therapist', 'Review'];

const SelectTherapistScreen = () => {
  const navigation = useNavigation();
  const booking = useBookingFlow();
  const [preference, setPreference] = useState(booking.therapistType ?? 'ANY');
  const { data: therapists } = useQuery<Therapist[]>(['therapists', booking.spaId], () =>
    booking.spaId ? getTherapists(booking.spaId) : Promise.resolve([])
  );

  return (
    <AppContainer>
      <AppHeader title="Therapist" showBack />
      <StepIndicator steps={steps} currentStep={2} />
      <AppSelect
        label="Preference"
        options={[
          { label: 'Any available', value: 'ANY' },
          { label: 'Female therapist', value: 'FEMALE' },
          { label: 'Male therapist', value: 'MALE' },
          { label: 'Specific therapist', value: 'SPECIFIC' }
        ]}
        selected={preference}
        onSelect={value => {
          setPreference(value as typeof preference);
          booking.setTherapistPreference(value as typeof preference);
        }}
      />
      {preference === 'SPECIFIC' && (
        <View>
          <Text className="text-sm text-gray-500 mb-2">Choose a therapist</Text>
          {therapists?.map(therapist => (
            <TherapistCard
              key={therapist.id}
              therapist={therapist}
              selected={booking.selectedTherapist?.id === therapist.id}
              onPress={() => booking.setTherapistPreference('SPECIFIC', therapist)}
            />
          ))}
        </View>
      )}
      <AppButton
        label="Review booking"
        className="mt-4"
        onPress={() => navigation.navigate('ReviewBooking' as never)}
      />
    </AppContainer>
  );
};

export default SelectTherapistScreen;
