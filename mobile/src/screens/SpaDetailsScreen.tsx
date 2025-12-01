import React, { useEffect } from 'react';
import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import { Text, View, FlatList } from 'react-native';
import { useQuery } from '@tanstack/react-query';
import AppContainer from '../components/layout/AppContainer';
import AppHeader from '../components/layout/AppHeader';
import RatingBadge from '../components/spa/RatingBadge';
import ServiceCard from '../components/spa/ServiceCard';
import { getSpaDetails, getSpaServices } from '../api/spaApi';
import { RootStackParamList } from '../navigation/RootNavigator';
import { useBookingFlow } from '../hooks/useBookingFlow';

const SpaDetailsScreen = () => {
  const navigation = useNavigation();
  const route = useRoute<RouteProp<RootStackParamList, 'SpaDetails'>>();
  const { spaId } = route.params;
  const booking = useBookingFlow();

  const { data: details } = useQuery(['spa-details', spaId], () => getSpaDetails(spaId));
  const { data: services } = useQuery(['spa-services', spaId], () => getSpaServices(spaId));

  useEffect(() => {
    if (details) {
      booking.setSpa(details.id, details.name);
    }
  }, [booking, details]);

  return (
    <AppContainer>
      <AppHeader title={details?.name ?? 'Spa'} showBack />
      {details && (
        <View className="bg-white rounded-2xl p-4 mb-4 shadow-sm">
          <View className="flex-row justify-between items-center mb-2">
            <Text className="text-xl font-semibold text-gray-900">{details.name}</Text>
            <RatingBadge rating={details.rating} reviewCount={details.reviewCount} />
          </View>
          <Text className="text-sm text-gray-600">{details.address}</Text>
          {details.openingHours && (
            <Text className="text-xs text-gray-500 mt-1">Open: {details.openingHours}</Text>
          )}
        </View>
      )}
      <Text className="text-lg font-semibold text-gray-900 mb-2">Services</Text>
      <FlatList
        data={services}
        keyExtractor={item => item.id}
        renderItem={({ item }) => (
          <ServiceCard
            service={item}
            onPress={() => {
              booking.setService(item);
              navigation.navigate('SelectSlot' as never);
            }}
          />
        )}
        ListEmptyComponent={<Text className="text-gray-500 text-center">No services listed.</Text>}
      />
    </AppContainer>
  );
};

export default SpaDetailsScreen;
