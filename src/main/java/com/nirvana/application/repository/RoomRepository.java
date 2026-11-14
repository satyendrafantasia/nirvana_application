package com.nirvana.application.repository;
import com.nirvana.application.model.SpaRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RoomRepository extends JpaRepository<SpaRoom,Long> {
    List<SpaRoom> findBySpa_IdOrderByName(Long spa);
    Optional<SpaRoom> findByIdAndSpa_Id(Long room,Long spa);
}
