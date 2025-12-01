import React from 'react';
import { FlatList, Text, View } from 'react-native';
import { useQuery } from '@tanstack/react-query';
import AppContainer from '../../components/layout/AppContainer';
import AppHeader from '../../components/layout/AppHeader';
import { getUserBookings } from '../../api/bookingApi';
import { BookingSummary } from '../../types/booking';

const MyBookingsScreen = () => {
  const { data, isFetching } = useQuery<BookingSummary[]>(['my-bookings'], getUserBookings);

  return (
    <AppContainer>
      <AppHeader title="My bookings" />
      <FlatList
        data={data}
        refreshing={isFetching}
        keyExtractor={item => item.id}
        renderItem={({ item }) => (
          <View className="bg-white rounded-2xl p-4 mb-3 shadow-sm">
            <Text className="text-base font-semibold text-gray-900">{item.serviceName}</Text>
            <Text className="text-sm text-gray-600">{item.spaName}</Text>
            <Text className="text-xs text-gray-500 mt-1">{item.date}</Text>
            <Text className="text-xs text-primary mt-1">Status: {item.status}</Text>
          </View>
        )}
        ListEmptyComponent={<Text className="text-center text-gray-500">No bookings yet.</Text>}
      />
    </AppContainer>
  );
};

export default MyBookingsScreen;
