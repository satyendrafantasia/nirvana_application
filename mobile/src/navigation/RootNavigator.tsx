import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { View, ActivityIndicator } from 'react-native';
import AuthNavigator from './AuthNavigator';
import MainTabNavigator from './MainTabNavigator';
import { useAuth } from '../hooks/useAuth';
import SelectServiceScreen from '../screens/Booking/SelectServiceScreen';
import SelectSlotScreen from '../screens/Booking/SelectSlotScreen';
import SelectTherapistScreen from '../screens/Booking/SelectTherapistScreen';
import ReviewBookingScreen from '../screens/Booking/ReviewBookingScreen';
import PaymentScreen from '../screens/Booking/PaymentScreen';
import BookingConfirmationScreen from '../screens/Booking/BookingConfirmationScreen';
import SpaDetailsScreen from '../screens/SpaDetailsScreen';

export type RootStackParamList = {
  Auth: undefined;
  Main: undefined;
  SpaDetails: { spaId: string; spaName?: string };
  SelectService: undefined;
  SelectSlot: undefined;
  SelectTherapist: undefined;
  ReviewBooking: undefined;
  Payment: undefined;
  BookingConfirmation: { bookingId: string };
};

const Stack = createNativeStackNavigator<RootStackParamList>();

const RootNavigator = () => {
  const { user, isLoading } = useAuth();

  if (isLoading) {
    return (
      <View className="flex-1 items-center justify-center bg-background">
        <ActivityIndicator color="#4FD1C5" />
      </View>
    );
  }

  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      {!user ? (
        <Stack.Screen name="Auth" component={AuthNavigator} />
      ) : (
        <Stack.Screen name="Main" component={MainTabNavigator} />
      )}
      <Stack.Screen name="SpaDetails" component={SpaDetailsScreen} />
      <Stack.Screen name="SelectService" component={SelectServiceScreen} />
      <Stack.Screen name="SelectSlot" component={SelectSlotScreen} />
      <Stack.Screen name="SelectTherapist" component={SelectTherapistScreen} />
      <Stack.Screen name="ReviewBooking" component={ReviewBookingScreen} />
      <Stack.Screen name="Payment" component={PaymentScreen} />
      <Stack.Screen name="BookingConfirmation" component={BookingConfirmationScreen} />
    </Stack.Navigator>
  );
};

export default RootNavigator;
