import React, { useState } from 'react';
import { Text, View, FlatList } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useQuery } from '@tanstack/react-query';
import AppContainer from '../components/layout/AppContainer';
import AppHeader from '../components/layout/AppHeader';
import AppTextInput from '../components/common/AppTextInput';
import SpaCard from '../components/spa/SpaCard';
import { searchSpas } from '../api/spaApi';
import { SpaSummary } from '../types/spa';

const HomeScreen = () => {
  const navigation = useNavigation();
  const [query, setQuery] = useState('');
  const { data: spas, isLoading } = useQuery<SpaSummary[]>(['home-spas'], () =>
    searchSpas({ query: 'featured' })
  );

  return (
    <AppContainer>
      <AppHeader title="Discover" />
      <View className="bg-white rounded-2xl p-4 mb-4 shadow-sm">
        <Text className="text-lg font-semibold text-gray-900 mb-2">Find your next escape</Text>
        <AppTextInput
          placeholder="Search for spa or treatment"
          value={query}
          onChangeText={setQuery}
          onSubmitEditing={() => navigation.navigate('Search' as never, { query } as never)}
        />
      </View>
      <Text className="text-lg font-semibold text-gray-900 mb-3">Featured nearby</Text>
      <FlatList
        data={spas}
        refreshing={isLoading}
        keyExtractor={item => item.id}
        renderItem={({ item }) => (
          <SpaCard
            spa={item}
            onPress={() => navigation.navigate('SpaDetails' as never, { spaId: item.id } as never)}
          />
        )}
      />
    </AppContainer>
  );
};

export default HomeScreen;
