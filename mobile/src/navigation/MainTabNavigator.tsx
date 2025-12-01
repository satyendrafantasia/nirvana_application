import React from 'react';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import HomeScreen from '../screens/HomeScreen';
import SpaSearchScreen from '../screens/SpaSearchScreen';
import MyBookingsScreen from '../screens/Profile/MyBookingsScreen';
import AccountSettingsScreen from '../screens/Profile/AccountSettingsScreen';
import { Text } from 'react-native';

export type MainTabParamList = {
  Home: undefined;
  Search: undefined;
  Bookings: undefined;
  Profile: undefined;
};

const Tab = createBottomTabNavigator<MainTabParamList>();

const MainTabNavigator = () => (
  <Tab.Navigator
    screenOptions={{
      headerShown: false,
      tabBarActiveTintColor: '#4FD1C5'
    }}
  >
    <Tab.Screen
      name="Home"
      component={HomeScreen}
      options={{ tabBarLabel: ({ color }) => <Text style={{ color }}>Home</Text> }}
    />
    <Tab.Screen
      name="Search"
      component={SpaSearchScreen}
      options={{ tabBarLabel: ({ color }) => <Text style={{ color }}>Search</Text> }}
    />
    <Tab.Screen
      name="Bookings"
      component={MyBookingsScreen}
      options={{ tabBarLabel: ({ color }) => <Text style={{ color }}>Bookings</Text> }}
    />
    <Tab.Screen
      name="Profile"
      component={AccountSettingsScreen}
      options={{ tabBarLabel: ({ color }) => <Text style={{ color }}>Profile</Text> }}
    />
  </Tab.Navigator>
);

export default MainTabNavigator;
