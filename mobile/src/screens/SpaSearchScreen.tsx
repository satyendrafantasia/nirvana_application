import React, { useState } from 'react';
import { FlatList, Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useQuery } from '@tanstack/react-query';
import AppContainer from '../components/layout/AppContainer';
import AppHeader from '../components/layout/AppHeader';
import SpaCard from '../components/spa/SpaCard';
import AppTextInput from '../components/common/AppTextInput';
import AppButton from '../components/common/AppButton';
import { searchSpas, SpaSearchParams } from '../api/spaApi';
import { SpaSummary } from '../types/spa';

const SpaSearchScreen = () => {
  const navigation = useNavigation();
  const [params, setParams] = useState<SpaSearchParams>({ query: '', location: '' });

  const { data: spas, refetch, isFetching } = useQuery<SpaSummary[]>(['spa-search', params], () =>
    searchSpas(params)
  );

  return (
    <AppContainer>
      <AppHeader title="Search" />
      <View className="bg-white rounded-2xl p-4 mb-4 shadow-sm">
        <AppTextInput
          label="What are you looking for?"
          placeholder="Massage, facial, sauna..."
          value={params.query}
          onChangeText={value => setParams(prev => ({ ...prev, query: value }))}
        />
        <AppTextInput
          label="Location"
          placeholder="City or neighborhood"
          value={params.location}
          onChangeText={value => setParams(prev => ({ ...prev, location: value }))}
        />
        <AppButton label="Search" onPress={() => refetch()} />
      </View>
      <FlatList
        data={spas}
        refreshing={isFetching}
        keyExtractor={item => item.id}
        renderItem={({ item }) => (
          <SpaCard
            spa={item}
            onPress={() => navigation.navigate('SpaDetails' as never, { spaId: item.id } as never)}
          />
        )}
        ListEmptyComponent={<Text className="text-center text-gray-500">No spas found.</Text>}
      />
    </AppContainer>
  );
};

export default SpaSearchScreen;
