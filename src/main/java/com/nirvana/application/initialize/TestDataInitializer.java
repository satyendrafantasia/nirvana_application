package com.nirvana.application.initialize;

import com.nirvana.application.model.*;
import com.nirvana.application.model.enums.RoleType;
import com.nirvana.application.model.enums.RoomType;
import com.nirvana.application.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
@Slf4j
public class TestDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AdminRepository adminRepository;
    private final CustomerRepository customerRepository;
    private final SpaManagerRepository SpaManagerRepository;
    private final PasswordEncoder passwordEncoder;
    private final AddressRepository addressRepository;
    private final SpaRepository SpaRepository;
    private final AvailabilityRepository availabilityRepository;

    @Override
    @Transactional
    public void run(String... args) {

        try {
            log.warn("Checking if test data persistence is required...");

            if (roleRepository.count() == 0 && userRepository.count() == 0) {
                log.info("Initiating test data persistence");

                Role adminRole = new Role(RoleType.ADMIN);
                Role customerRole = new Role(RoleType.CUSTOMER);
                Role SpaManagerRole = new Role(RoleType.Spa_MANAGER);

                roleRepository.save(adminRole);
                roleRepository.save(customerRole);
                roleRepository.save(SpaManagerRole);
                log.info("Role data persisted");

                User user1 = User.builder().username("admin@Spa.com").password(passwordEncoder.encode("1")).name("Admin").lastName("Admin").role(adminRole).build();
                User user2 = User.builder().username("customer1@Spa.com").password(passwordEncoder.encode("1")).name("Kaya Alp").lastName("Koker").role(customerRole).build();
                User user3 = User.builder().username("manager1@Spa.com").password(passwordEncoder.encode("1")).name("John").lastName("Doe").role(SpaManagerRole).build();
                User user4 = User.builder().username("manager2@Spa.com").password(passwordEncoder.encode("1")).name("Max").lastName("Mustermann").role(SpaManagerRole).build();

                userRepository.save(user1);
                userRepository.save(user2);
                userRepository.save(user3);
                userRepository.save(user4);

                Admin admin1 = Admin.builder().user(user1).build();
                Customer c1 = Customer.builder().user(user2).build();
                SpaManager hm1 = SpaManager.builder().user(user3).build();
                SpaManager hm2 = SpaManager.builder().user(user4).build();

                adminRepository.save(admin1);
                customerRepository.save(c1);
                SpaManagerRepository.save(hm1);
                SpaManagerRepository.save(hm2);
                log.info("User data persisted");

                Address addressIst1 = Address.builder().addressLine("Acısu Sokağı No:19, 34357").city("Istanbul")
                        .country("Turkey").build();
                Address addressIst2 = Address.builder().addressLine("Çırağan Cd. No:28, 34349 Beşiktaş").city("Istanbul")
                        .country("Turkey").build();
                Address addressIst3 = Address.builder().addressLine("Çırağan Cd. No:32, 34349 Beşiktaş").city("Istanbul")
                        .country("Turkey").build();

                Address addressBerlin1 = Address.builder().addressLine("Unter den Linden 77").city("Berlin")
                        .country("Germany").build();
                Address addressBerlin2 = Address.builder().addressLine("Potsdamer Platz 3, Mitte, 10785").city("Berlin")
                        .country("Germany").build();
                Address addressBerlin3 = Address.builder().addressLine("Budapester Str. 2, Mitte, 10787").city("Berlin")
                        .country("Germany").build();

                addressRepository.save(addressIst1);
                addressRepository.save(addressIst2);
                addressRepository.save(addressIst3);
                addressRepository.save(addressBerlin1);
                addressRepository.save(addressBerlin2);
                addressRepository.save(addressBerlin3);

                Spa SpaIst1 = Spa.builder().name("Swissotel The Bosphorus Istanbul")
                        .address(addressIst1).SpaManager(hm1).build();
                Spa SpaIst2 = Spa.builder().name("Four Seasons Spa Istanbul")
                        .address(addressIst2).SpaManager(hm1).build();
                Spa SpaIst3 = Spa.builder().name("Ciragan Palace Kempinski Istanbul")
                        .address(addressIst3).SpaManager(hm1).build();

                Spa SpaBerlin1 = Spa.builder().name("Spa Adlon Kempinski Berlin")
                        .address(addressBerlin1).SpaManager(hm2).build();
                Spa SpaBerlin2 = Spa.builder().name("The Ritz-Carlton Berlin")
                        .address(addressBerlin2).SpaManager(hm2).build();
                Spa SpaBerlin3 = Spa.builder().name("InterContinental Berlin")
                        .address(addressBerlin3).SpaManager(hm2).build();

                Room singleRoomIst1 = Room.builder().roomType(RoomType.SINGLE)
                        .pricePerNight(370).roomCount(35).Spa(SpaIst1).build();
                Room doubleRoomIst1 = Room.builder().roomType(RoomType.DOUBLE)
                        .pricePerNight(459).roomCount(45).Spa(SpaIst1).build();

                Room singleRoomIst2 = Room.builder().roomType(RoomType.SINGLE)
                        .pricePerNight(700).roomCount(25).Spa(SpaIst2).build();
                Room doubleRoomIst2 = Room.builder().roomType(RoomType.DOUBLE)
                        .pricePerNight(890).roomCount(30).Spa(SpaIst2).build();

                Room singleRoomIst3 = Room.builder().roomType(RoomType.SINGLE)
                        .pricePerNight(691).roomCount(30).Spa(SpaIst3).build();
                Room doubleRoomIst3 = Room.builder().roomType(RoomType.DOUBLE)
                        .pricePerNight(800).roomCount(75).Spa(SpaIst3).build();

                Room singleRoomBerlin1 = Room.builder().roomType(RoomType.SINGLE)
                        .pricePerNight(120.0).roomCount(25).Spa(SpaBerlin1).build();
                Room doubleRoomBerlin1 = Room.builder().roomType(RoomType.DOUBLE)
                        .pricePerNight(250.0).roomCount(15).Spa(SpaBerlin1).build();

                Room singleRoomBerlin2 = Room.builder().roomType(RoomType.SINGLE)
                        .pricePerNight(300).roomCount(50).Spa(SpaBerlin2).build();
                Room doubleRoomBerlin2 = Room.builder().roomType(RoomType.DOUBLE)
                        .pricePerNight(400).roomCount(50).Spa(SpaBerlin2).build();

                Room singleRoomBerlin3 = Room.builder().roomType(RoomType.SINGLE)
                        .pricePerNight(179).roomCount(45).Spa(SpaBerlin3).build();
                Room doubleRoomBerlin3 = Room.builder().roomType(RoomType.DOUBLE)
                        .pricePerNight(256).roomCount(25).Spa(SpaBerlin3).build();

                SpaIst1.getRooms().addAll(Arrays.asList(singleRoomIst1,doubleRoomIst1));
                SpaIst2.getRooms().addAll(Arrays.asList(singleRoomIst2,doubleRoomIst2));
                SpaIst3.getRooms().addAll(Arrays.asList(singleRoomIst3,doubleRoomIst3));
                SpaBerlin1.getRooms().addAll(Arrays.asList(singleRoomBerlin1,doubleRoomBerlin1));
                SpaBerlin2.getRooms().addAll(Arrays.asList(singleRoomBerlin2,doubleRoomBerlin2));
                SpaBerlin3.getRooms().addAll(Arrays.asList(singleRoomBerlin3,doubleRoomBerlin3));

                SpaRepository.save(SpaIst1);
                SpaRepository.save(SpaIst2);
                SpaRepository.save(SpaIst3);
                SpaRepository.save(SpaBerlin1);
                SpaRepository.save(SpaBerlin2);
                SpaRepository.save(SpaBerlin3);
                log.info("Spa data persisted");

                Availability av1Berlin1 = Availability.builder().Spa(SpaBerlin1)
                        .date(LocalDate.of(2023,9,1)).room(singleRoomBerlin1).availableRooms(5).build();
                Availability av2Berlin1 = Availability.builder().Spa(SpaBerlin1)
                        .date(LocalDate.of(2023,9,2)).room(doubleRoomBerlin1).availableRooms(7).build();

                availabilityRepository.save(av1Berlin1);
                availabilityRepository.save(av2Berlin1);
                log.info("Availability data persisted");

            } else {
                log.info("Test data persistence is not required");
            }
            log.warn("App ready");
        } catch (DataAccessException e) {
            log.error("Exception occurred during data persistence: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected exception occurred: " + e.getMessage());
        }
    }
}
