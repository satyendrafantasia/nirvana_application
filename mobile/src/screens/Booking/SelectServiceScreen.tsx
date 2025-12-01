import React from 'react';
import { Text, View } from 'react-native';
import { useQuery } from '@tanstack/react-query';
import { useNavigation } from '@react-navigation/native';
import AppContainer from '../../components/layout/AppContainer';
import AppHeader from '../../components/layout/AppHeader';
import ServiceCard from '../../components/spa/ServiceCard';
import { useBookingFlow } from '../../hooks/useBookingFlow';
import { getSpaServices } from '../../api/spaApi';

const SelectServiceScreen = () => {
  const navigation = useNavigation();
  const booking = useBookingFlow();
  const { data: services } = useQuery(['booking-services', booking.spaId], () =>
    booking.spaId ? getSpaServices(booking.spaId) : Promise.resolve([])
  );

  if (!booking.spaId) {
    return (
      <AppContainer>
        <AppHeader title="Select Service" showBack />
        <Text className="text-gray-500">Please pick a spa first.</Text>
      </AppContainer>
    );
  }

  return (
    <AppContainer>
      <AppHeader title="Select Service" showBack />
      {services?.map(service => (
        <ServiceCard
          key={service.id}
          service={service}
          onPress={() => {
            booking.setService(service);
            navigation.navigate('SelectSlot' as never);
          }}
        />
      ))}
    </AppContainer>
  );
};

export default SelectServiceScreen;
